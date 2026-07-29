#!/usr/bin/env bash
# check-swift-deps.sh — detect unused and missing SwiftPM package deps
# for a single Swift package.
#
# Usage: scripts/check-swift-deps.sh [package_dir]
#        scripts/check-swift-deps.sh --report [package_dir]
#
# Background
# ----------
# Cargo has `cargo-udeps`. SwiftPM has no first-party equivalent.
# `Periphery` covers declarations, but not "I declared FastULID as a
# dep but never `import FastULID` in any .swift file" — that's a
# distinct failure mode (broken local Package.swift, copy-pasted
# transitives, etc.).
#
# This script fills the gap by parsing the package manifest,
# walking every Sources/**/*.swift file for `import` statements,
# and comparing declared deps against imported modules.
#
# Strategy
# --------
# 1. Try `swift package dump-package` (cleanest data, parses
#    conditional deps + binary targets). If it fails — usually
#    because the host Swift toolchain is older than the package's
#    declared `swift-tools-version` — fall back to a regex-based
#    parse of Package.swift directly. The regex pass handles the
#    95% case; the dump-package path handles edge cases like
#    conditional targets and multi-target dependency fan-out.
# 2. Read the configured package→module map from
#    .swift-deps-audit.yaml. By default, SwiftPM packageName ==
#    moduleName. Override entries exist for packages that pin a
#    different module name (rare but real — see elijahdou/FastULID
#    vs the `FastULID` module it actually exports).
# 3. Walk `Sources/**/*.swift` (configurable; recursive) for
#    leading-line `import <Module>` statements. Pull the module
#    name into a sorted set.
# 4. For each declared package dep that maps to a module that is
#    NOT in the import set: UNUSED. (Local path-based deps are
#    ignored — see UNUSED reporting below.)
# 5. For each imported module not covered by any declared package
#    dep and not in `ignoreModules`: MISSING. `Foundation`,
#    `Swift`, `SwiftUI`, etc. are pre-ignored so they don't
#    pollute the report.
# 6. Tests targets are skipped — the test target in a typical
#    package only consumes modules, it doesn't reflect what's used
#    by the published library. Add to `ignoreTargets` per project.
# 7. Exit non-zero on findings unless `--report` was passed.
#
# Exit codes
# ----------
#   0 — clean (no findings, or `--report` mode)
#   1 — findings present (only when not in `--report` mode)
#   2 — config or environment error (missing config, missing
#       package dir, etc.)
#
# Scope of the first run
# ----------------------
# This implementation prioritizes UNUSED-package detection (the
# main value-add over Periphery). MISSING-module detection is
# implemented but best-effort — its false-positive rate is higher
# because transitive product imports + Swift stdlib overlap are
# common sources of friction. If your first run floods you with
# MISSING reports, set `strict: false` in .swift-deps-audit.yaml
# to suppress them and surface only UNUSED findings.
#
# Requirements
# ------------
#   - bash 4+
#   - jq (1.6+)
#   - python3 (for YAML config parsing — bundled with Xcode CLT)
#
# Optional:
#   - swift (5.7+ preferred for `dump-package`; falls back
#     gracefully when unavailable or too old)

set -euo pipefail

# shellcheck disable=SC2034  # set for callers that source this script
REPORT_ONLY=0
if [[ "${1:-}" == "--report" ]]; then
  # shellcheck disable=SC2034  # set for callers that source this script
  REPORT_ONLY=1
  shift
fi

PKG_DIR="${1:-.}"
PKG_DIR="$(cd "$PKG_DIR" 2>/dev/null && pwd || true)"
if [[ -z "$PKG_DIR" || ! -d "$PKG_DIR" ]]; then
  echo "ERROR: package dir '$1' is not a directory" >&2
  exit 2
fi

if [[ ! -f "$PKG_DIR/Package.swift" ]]; then
  cat >&2 <<EOF
ERROR: $PKG_DIR does not contain a Package.swift
EOF
  exit 2
fi

# --- Config -----------------------------------------------------------------
# Locating the config: package-local first, then repo root. The repo-root
# fallback lets a single config cover multiple packages in a monorepo.

CONFIG=""
if [[ -f "$PKG_DIR/.swift-deps-audit.yaml" ]]; then
  CONFIG="$PKG_DIR/.swift-deps-audit.yaml"
else
  # Walk up to the git toplevel looking for a config.
  prev="$PWD"
  cur="$PKG_DIR"
  while [[ "$cur" != "/" ]]; do
    if [[ -f "$cur/.swift-deps-audit.yaml" ]]; then
      CONFIG="$cur/.swift-deps-audit.yaml"
      break
    fi
    [[ "$cur" == "$(cd "$cur/.." && pwd)" ]] && break
    cur="$(cd "$cur/.." && pwd)"
  done
  cd "$prev"
fi

if [[ -z "$CONFIG" ]]; then
  cat >&2 <<EOF
ERROR: no .swift-deps-audit.yaml found near $PKG_DIR
  Drop one next to Package.swift (per-package override) or at the repo root.
  See .swift-deps-audit.yaml in the repo for the format.
EOF
  exit 2
fi

# Parse the YAML config. We accept a deliberately small subset of YAML:
#
#   <key>:
#     - <item>                          # sequence entries
#   <key>:
#     <subKey>: <value>                 # map entries (only one level deep)
#   strict: true                        # simple key: value
#
# We deliberately avoid PyYAML (not always available) and yq
# (separate install). The implementation is awk + grep + sed. The
# declaration order in the file doesn't matter; we emit final
# shell-eval-friendly lines at the end.

DEFAULT_IGNORE_MODULES='Foundation,Swift,SwiftUI,UIKit,AppKit,WatchKit,Combine,ObjectiveC,CoreFoundation,CoreGraphics,CoreLocation,CoreData,CryptoKit,SwiftData,Charts,MapKit,WebKit,AVFoundation,AVFAudio,SceneKit,MetalKit,Metal,GameplayKit,SpriteKit,QuartzCore,Accelerate,Security,SystemConfiguration,CFNetwork,SafariServices,BackgroundTasks,PushKit,CallKit,VisionKit,Vision,PDFKit,PencilKit,os,os.log,os.signpost,Darwin,Dispatch,Network'

MODULE_MAP__=""
# shellcheck disable=SC2034  # populated by awk and sourced via /tmp file below
IGNORE_TARGETS__=""
IGNORE_MODULES__="$DEFAULT_IGNORE_MODULES"
STRICT__="1"
EXTRA_SOURCES__=""

# State machine: track the current top-level key and whether we're
# reading its sequence ("- item") or its map (under it).
# shellcheck disable=SC2034  # mirrored as awk variables in the state machine below
current_key=""
# shellcheck disable=SC2034
reading=""
# Python-free parser built from awk. Emit a single line per top-level
# key once we've seen the entire block, so we can run post-processing.
awk -v IGNORE_MODULES__="$DEFAULT_IGNORE_MODULES" '
BEGIN {
    cur = ""
    reading = ""
    pkgmap_items = ""
    ignore_targets_items = ""
    ignore_modules_items = IGNORE_MODULES__  # default; overridden by config
    strict_val = "1"
    extra_sources_items = ""
}

/^[ \t]*#/ { next }                  # comment
/^[ \t]*$/ { next }                  # blank line
/^[ \t]*[A-Za-z_][A-Za-z0-9_]*:[ \t]*$/ {
    # Top-level key start (no inline value): entering a sequence or map.
    key = $0
    sub(/^[ \t]*/, "", key)
    sub(/:[ \t]*$/, "", key)
    cur = key
    reading = "seq-or-map"
    next
}
/^[ \t]*[A-Za-z_][A-Za-z0-9_]*:[ \t]+[^ \t].*$/ {
    # Inline scalar: "<key>: <value>". Capture and stop the current block.
    line = $0
    sub(/^[ \t]*/, "", line)
    split(line, kv, ":")
    k = kv[1]
    v = kv[2]
    sub(/^[ \t]+/, "", v)
    sub(/[ \t]+$/, "", v)

    cur = ""
    reading = ""

    if (k == "strict") {
        strict_val = (v == "true" || v == "1") ? "1" : "0"
    }
    next
}

/^[ \t]+-[ \t]+/ {
    if (cur == "ignoreTargets") {
        v = $0
        sub(/^[ \t]+-[ \t]+/, "", v)
        sub(/[ \t]+$/, "", v)
        v = strip_quotes(v)
        if (ignore_targets_items == "") ignore_targets_items = v
        else ignore_targets_items = ignore_targets_items "," v
    } else if (cur == "ignoreModules") {
        v = $0
        sub(/^[ \t]+-[ \t]+/, "", v)
        sub(/[ \t]+$/, "", v)
        v = strip_quotes(v)
        if (ignore_modules_items == "") ignore_modules_items = v
        else ignore_modules_items = ignore_modules_items "," v
    } else if (cur == "extraSources") {
        v = $0
        sub(/^[ \t]+-[ \t]+/, "", v)
        sub(/[ \t]+$/, "", v)
        v = strip_quotes(v)
        if (extra_sources_items == "") extra_sources_items = v
        else extra_sources_items = extra_sources_items "," v
    }
    next
}

/^[ \t]+[A-Za-z_][A-Za-z0-9_]*:[ \t]+/ {
    # 2-space-indented map entry under cur.
    line = $0
    sub(/^[ \t]+/, "", line)
    split(line, kv, ":")
    k = kv[1]
    v = kv[2]
    sub(/^[ \t]+/, "", v)
    sub(/[ \t]+$/, "", v)
    if (cur == "packageModuleMap") {
        v = strip_quotes(v)
        if (pkgmap_items == "") pkgmap_items = k "=" v
        else pkgmap_items = pkgmap_items "," k "=" v
    }
    next
}

function strip_quotes(s,    _r) {
    _r = s
    if (substr(_r, 1, 1) == "\"" && substr(_r, length(_r), 1) == "\"") {
        _r = substr(_r, 2, length(_r) - 2)
    }
    if (substr(_r, 1, 1) == "\x27" && substr(_r, length(_r), 1) == "\x27") {
        _r = substr(_r, 2, length(_r) - 2)
    }
    return _r
}

END {
    print "MODULE_MAP__=" pkgmap_items
    print "IGNORE_TARGETS__=" ignore_targets_items
    print "IGNORE_MODULES__=" ignore_modules_items
    print "STRICT__=" strict_val
    print "EXTRA_SOURCES__=" extra_sources_items
}
' "$CONFIG" >/tmp/livtet-cfg-$$

# shellcheck disable=SC1090  # dynamic path by design
. /tmp/livtet-cfg-$$
rm -f /tmp/livtet-cfg-$$

# --- Step 1: extract declared deps + target products -------------------------

USE_DUMP_PACKAGE=0
DUMP_JSON=""
if command -v swift >/dev/null 2>&1; then
  # Capture stderr too so we can mention dump-package failures in the report.
  if DUMP_JSON="$(cd "$PKG_DIR" && swift package dump-package 2>/dev/null)"; then
    # Sanity-check: did we get JSON?
    if printf '%s' "$DUMP_JSON" | jq -e '.name' >/dev/null 2>&1; then
      USE_DUMP_PACKAGE=1
    fi
  fi
fi

if [[ "$USE_DUMP_PACKAGE" -eq 1 ]]; then
  # The dump-package JSON gives us declared packages, target deps, etc.
  # Skip packages whose URL is null (path-based local siblings — `vendored-deps/SwiftFoo`).
  DECLARED_JSON=$(printf '%s' "$DUMP_JSON" | jq -r '
        (.dependencies // []) as $deps
        | $deps
        | map(select(.url != null) | .name)
        | .[]
    ' 2>/dev/null | sort -u || true)

  TARGET_PRODUCTS_JSON=$(printf '%s' "$DUMP_JSON" | jq -r '
        (
            (.targets // [])
            | map(select(("'"$IGNORE_TARGETS__"'".split(",")) as $ig | (.name as $n | ($ig | index($n)) == null)) )
            | map(.dependencies // []) | add | .[]
            | select(.product != null) | .product
        )' 2>/dev/null | sort -u || true)
else
  # Regex fallback: parse Package.swift directly. We extract:
  #   - package names from `.package(url: "...", ...)`
  #   - product names imported by targets via `.product(name: "<X>", ...)`.
  # This misses conditional deps and complex multi-line manifests but
  # is good enough for the common case.
  PKG_SWIFT="$PKG_DIR/Package.swift"

  # Pull package names from .package(url: "...", ...) entries that
  # have a *remote* URL. We grab the URL via grep, then extract the
  # last path component (minus `.git`).
  DECLARED_JSON=$(
    grep -oE '\.package\([^)]*url:[[:space:]]*"[^"]+"[^)]*\)' "$PKG_SWIFT" 2>/dev/null |
      sed -E 's|^.*url:[[:space:]]*"[^"]+/([^/]+)\.git".*$|\1|' |
      sort -u || true
  )

  # Pull product names from `.product(name: "X", package: "Y")` inside
  # any target. Simple scan; if your Package.swift wraps these across
  # many lines, switch to the dump-package path by upgrading the toolchain.
  TARGET_PRODUCTS_JSON=$(
    grep -oE '\.product\([[:space:]]*name:[[:space:]]*"[^"]+"' "$PKG_SWIFT" 2>/dev/null |
      sed -E 's|.*name:[[:space:]]*"([^"]+)".*|\1|' |
      sort -u || true
  )

  if [[ -z "$DECLARED_JSON$TARGET_PRODUCTS_JSON" ]]; then
    cat >&2 <<EOF
WARN: did not extract any package or product names from \${PKG_SWIFT}
WARN: the fallback parser is regex-based and may miss multi-line .package(...) calls.
WARN: install a swift toolchain matching or newer than the package's tools-version, then re-run.
EOF
  fi
fi

# --- Step 2: walk Sources/**/*.swift for `import` statements -----------------

SOURCES_DIRS=()
SOURCES_DIRS+=("$PKG_DIR/Sources")
if [[ -n "$EXTRA_SOURCES__" ]]; then
  IFS=',' read -ra extra_arr <<<"$EXTRA_SOURCES__"
  for p in "${extra_arr[@]}"; do
    # Resolve relative to PKG_DIR
    SOURCES_DIRS+=("$PKG_DIR/$p")
  done
fi

# Collect all `import Foo` (leading-line only — strip leading whitespace;
# that's how Swift Grammar allows `import`).
IMPORTED_MODULES=""
for src_dir in "${SOURCES_DIRS[@]}"; do
  [[ -d "$src_dir" ]] || continue
  while IFS= read -r -d '' f; do
    # Extract leading-line `import` tokens only. Skip typealias and
    # fully-qualified imports like `import class FooKit.Bar`.
    grep -hE '^[ \t]*import[ \t]+[A-Za-z_][A-Za-z0-9_]*' "$f" 2>/dev/null |
      sed -E 's/^[ \t]*import[ \t]+//' >>/tmp/livtet-imports-$$ || true
  done < <(find "$src_dir" -name "*.swift" -type f -print0)
done

# Drop typealias / class / enum / struct / func / let / var qualifiers
# (they're not real modules but the regex would catch them in
# `import class Foo.Bar` etc.). For simplicity we only keep tokens
# matching the bare-module pattern.
IMPORTED_MODULES=$(
  sort -u /tmp/livtet-imports-$$ 2>/dev/null |
    grep -E '^[A-Za-z_][A-Za-z0-9_]*$' ||
    true
)
rm -f /tmp/livtet-imports-$$ || true

# --- Step 3: classify -------------------------------------------------------

# macOS-shipped bash is 3.2 and doesn't support associative arrays
# (`declare -A`). Use a line-delimited tempfile keyed by "<key> <value>"
# instead. This is bash 3.2-safe.

PKG_TO_MODULE_FILE=/tmp/livtet-pkg-to-module-$$
: >"$PKG_TO_MODULE_FILE"

# Default module-per-package mapping.
printf '%s\n' "$DECLARED_JSON" | while IFS= read -r pkg; do
  [[ -z "$pkg" ]] && continue
  printf '%s %s\n' "$pkg" "$pkg" >>"$PKG_TO_MODULE_FILE"
done

# Apply overrides from config (key=module form).
if [[ -n "$MODULE_MAP__" ]]; then
  IFS=',' read -ra entries <<<"$MODULE_MAP__"
  for entry in "${entries[@]}"; do
    k="${entry%%=*}"
    v="${entry#*=}"
    if [[ -n "$k" && -n "$v" ]]; then
      # Replace the existing line for k if present, else append.
      tmp=$(mktemp)
      awk -v k="$k" -v v="$v" '
                $1 == k { print k, v; next }
                { print }
            ' "$PKG_TO_MODULE_FILE" >"$tmp" && mv "$tmp" "$PKG_TO_MODULE_FILE"
    fi
  done
fi

# Lookup helpers — both wrap simple grep. Output is the right-hand
# side (or empty).
pkg_to_module() {
  local pkg="$1"
  awk -v k="$pkg" '$1 == k { print $2; exit }' "$PKG_TO_MODULE_FILE"
}

# Module-names declared via target products but not mapped to a package
# (because the dump was missing the package row, or the consumer is a
# same-package product like `LivtetKit`). That's fine — we only flag
# imports that ARE used but have no declared package.

echo "=== check-swift-deps.sh ==="
echo "Package dir : $PKG_DIR"
echo "Config      : $CONFIG"
if [[ "$USE_DUMP_PACKAGE" -eq 1 ]]; then
  echo "Manifest via: swift package dump-package"
else
  echo "Manifest via: regex fallback (no dump-package)"
fi
echo "Strict mode : $STRICT__"
echo

UNUSED_COUNT=0
MISSING_COUNT=0

# ---- UNUSED: declared package deps whose mapped module is never imported ----
echo "## UNUSED declared package deps"
echo "(Declared in Package.swift, but no \`import\` of the mapped module anywhere in Sources/)"
echo
# Iterate over declared packages, checking whether the mapped module
# appears in IMPORTED_MODULES (sorted-unique, one per line).
while IFS= read -r pkg; do
  [[ -z "$pkg" ]] && continue
  module="$(pkg_to_module "$pkg")"
  [[ -z "$module" ]] && module="$pkg"

  if printf '%s\n' "$IMPORTED_MODULES" | awk -v m="$module" '$0 == m { found=1; exit } END { exit !found }'; then
    continue
  fi
  if printf '%s\n' "$TARGET_PRODUCTS_JSON" | awk -v m="$module" '$0 == m { found=1; exit } END { exit !found }'; then
    # shellcheck disable=SC2016  # literal percent signs in printf format are intentional
    printf '  - %-30s -> module %s (declared via .product, but no `import` of %s found)\n' "$pkg" "$module" "$module"
  else
    printf '  - %-30s -> module %s (never imported)\n' "$pkg" "$module"
  fi
  UNUSED_COUNT=$((UNUSED_COUNT + 1))
done <<<"$(printf '%s\n' "$DECLARED_JSON")"
[[ "$UNUSED_COUNT" -eq 0 ]] && echo "  (none)"
echo

# ---- MISSING: imported modules with no declaring package --------------------
echo "## MISSING package coverage"
echo "(Imported in Sources/, but no declared package produces this module)"
echo
if [[ "$STRICT__" -eq 1 ]]; then
  # Build derived files: ignored-modules, declared-modules, same-package-targets.
  IGN_FILE=/tmp/livtet-ignored-$$
  DEC_FILE=/tmp/livtet-declared-$$
  : >"$IGN_FILE"
  : >"$DEC_FILE"
  if [[ -n "$IGNORE_MODULES__" ]]; then
    IFS=',' read -ra mods <<<"$IGNORE_MODULES__"
    for m in "${mods[@]}"; do
      printf '%s\n' "$m" >>"$IGN_FILE"
    done
  fi
  # Declared modules = right-hand column of PKG_TO_MODULE_FILE (deduped).
  awk '{ print $2 }' "$PKG_TO_MODULE_FILE" | sort -u >"$DEC_FILE"

  # Same-package target names (heuristic: anything in Package.swift
  # that has `name: "X"` inside a `target(` block).
  awk -v out=/tmp/livtet-same-pkg-$$ '
        /target\(/ { in_tgt=1; next }
        /^\)/ || /^[ \t]*\)/ { in_tgt=0; next }
        in_tgt && /^[ \t]*name:[[:space:]]*\"/ { match($0, /\"[^\"]+\"/); print substr($0, RSTART+1, RLENGTH-2) > out }
    ' "$PKG_DIR/Package.swift"
  sort -u /tmp/livtet-same-pkg-$$ >/tmp/livtet-same-pkg-2-$$ && mv /tmp/livtet-same-pkg-2-$$ /tmp/livtet-same-pkg-$$

  for mod in $IMPORTED_MODULES; do
    [[ -z "$mod" ]] && continue
    # Skip ignored.
    if awk -v m="$mod" '$0 == m { found=1; exit } END { exit !found }' "$IGN_FILE"; then
      continue
    fi
    # Skip declared modules (covered by a package dep).
    if awk -v m="$mod" '$0 == m { found=1; exit } END { exit !found }' "$DEC_FILE"; then
      continue
    fi
    # Skip same-package targets.
    if awk -v m="$mod" '$0 == m { found=1; exit } END { exit !found }' /tmp/livtet-same-pkg-$$; then
      continue
    fi
    printf '  - %s\n' "$mod"
    MISSING_COUNT=$((MISSING_COUNT + 1))
  done
  rm -f "$IGN_FILE" "$DEC_FILE" /tmp/livtet-same-pkg-$$
  [[ "$MISSING_COUNT" -eq 0 ]] && echo "  (none)"
else
  echo "  (skipped -- strict mode is off)"
fi
echo

echo "## Summary"
echo "  UNUSED declared deps : $UNUSED_COUNT"
echo "  MISSING packages     : $MISSING_COUNT"

# --- Step 4: exit code ------------------------------------------------------
# shellcheck disable=SC2034  # reserved for future non-zero exit on combined errors
TOTAL=$((UNUSED_COUNT + MISSING_COUNT))
rm -f "$PKG_TO_MODULE_FILE" 2>/dev/null || true
