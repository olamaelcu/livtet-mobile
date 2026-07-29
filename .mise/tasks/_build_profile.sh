#!/usr/bin/env bash
# Shared build-profile helper for cargo-driving tasks.
# Sourced by other .mise/tasks/*.sh files - not meant to be run directly.
#
# Caller must declare in its shebang header:
#   #USAGE flag "--profile <profile>" help="Build profile" default="debug" {
#   #USAGE   choices "debug" "release"
#   #USAGE }
#
# Then `source "$(dirname "${BASH_SOURCE[0]}")/_build_profile.sh"` and use
# $PROFILE, $CARGO_PROFILE_DIR, and $CARGO_PROFILE_FLAG.
#
# Precedence: mise usage flag > LIVTET_BUILD_PROFILE env var > "debug" default.

set -e

PROFILE="${usage_profile:-${LIVTET_BUILD_PROFILE:-debug}}"

case "$PROFILE" in
debug)
  CARGO_PROFILE_DIR="debug"
  CARGO_PROFILE_FLAG=""
  ;;
release)
  CARGO_PROFILE_DIR="release"
  CARGO_PROFILE_FLAG="--release"
  ;;
*)
  echo "Error: unknown profile '$PROFILE' (expected: debug, release)" >&2
  exit 2
  ;;
esac

export PROFILE CARGO_PROFILE_DIR CARGO_PROFILE_FLAG
echo "Profile: $PROFILE"