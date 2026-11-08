# Taskwarrior integration

This project uses [Taskwarrior](https://taskwarrior.org/) for in-repo task
tracking, the same setup as `../desktop`. The task database lives in the
repository (`.taskdata/`), so the list travels with the code to every device
and every agent.

## Why this exists

- A single, repo-local task list shared by Android, iOS, and the core submodule.
- Humans use the `task` CLI; agents use the `taskwarrior-mcp` server.
- No external service, no sync server, no auth — just files in the repo.

## Setup

First-time (once per clone):

```sh
mise install                  # installs taskwarrior 3.4.2 + pipx:taskwarrior-mcp
direnv allow .                # exports mise env vars into the shell
mise run task-init            # creates .taskdata/ if missing
```

Daily, `task` and the MCP server work without further setup. `TASKRC` and
`TASKDATA` are set by `mise.toml`'s `[env]` block and point at `.taskrc` and
`.taskdata/` at the repo root.

## File layout

| Path                          | Purpose                                                       | Tracked? |
| ----------------------------- | ------------------------------------------------------------- | -------- |
| `mise.toml`                   | Declares `taskwarrior` + `pipx:taskwarrior-mcp`; sets env   | yes |
| `.taskrc`                     | Taskwarrior config + `livtet_component` UDA                 | yes |
| `.taskdata/`                  | Taskwarrior 3.x database (Taskchampion SQLite)              | yes |
| `.mise/tasks/task-init`       | Idempotent bootstrap that creates `.taskdata/`              | yes |
| `.envrc.example`              | Template for the per-developer `.envrc`                     | yes |
| `.envrc`                      | Local dev env (`eval "$(mise env -s bash)"`)                | no  |
| `.opencode/opencode.jsonc`    | Wires the `taskwarrior-mcp` MCP server                      | yes |

## CLI usage

```sh
task list
task add "Port the shelf wall to iOS" livtet_component:mobile project:ios due:2026-12-01
task 1 done
task 3 delete
task 4 annotate "needs a Mac to verify"
```

Filters compose:

```sh
task list +ios
task +mobile
task project:sync
```

The `task` shim must resolve to Taskwarrior, not to the Go `task` runner. If
`task --version` prints a Go build or complains about a `Taskfile.yml`, the
shell is picking the wrong tool; run `mise which task` from this directory.

## MCP usage

Opencode sessions in this repo see the `taskwarrior_*` MCP tool family:

Core: `taskwarrior_add`, `taskwarrior_list`, `taskwarrior_complete`,
`taskwarrior_modify`, `taskwarrior_delete`, `taskwarrior_get`,
`taskwarrior_bulk_get`, `taskwarrior_annotate`, `taskwarrior_start`,
`taskwarrior_stop`.

Reporting: `taskwarrior_projects`, `taskwarrior_project_summary`,
`taskwarrior_tags`, `taskwarrior_summary`, `taskwarrior_undo`.

Agent intelligence: `taskwarrior_suggest`, `taskwarrior_ready`,
`taskwarrior_blocked`, `taskwarrior_dependencies`, `taskwarrior_triage`,
`taskwarrior_context`.

All tools read and write the same `.taskdata/` as the CLI. Both MCP server
commands are wrapped in `mise x --` so the env block (and therefore
`TASKRC`/`TASKDATA`) is applied to each spawned process.

## Across devices

`.taskdata/taskchampion.sqlite3` is committed. Pull before starting, commit the
database with your work, push afterwards — the same way task state moves on
desktop.

- Two devices editing the same task before syncing produce a SQLite merge
  conflict. Resolve it by taking one side, then re-applying the other side's
  change with `task add`/`task modify` so the history stays readable.
- Do not add `.taskdata/` to `.gitignore`: the tracked database *is* the shared
  state.
- The database is small (kilobytes); avoid importing attachments into it.

## The `livtet_component` UDA

Defined in `.taskrc` to match the monorepo's sub-projects:

```ini
uda.livtet_component.type   = string
uda.livtet_component.label  = Component
uda.livtet_component.values = web,desktop,mobile,platform,data,opds,core
```

Mobile tasks usually set `livtet_component:mobile`. Work that lives in the Rust
core sets `livtet_component:core` but is still tracked in the repo that owns the
workstream: the core repo (`olamaelcu/livtet`) has no task list of its own.

## Troubleshooting

- `task --version` reports the Go task runner — see the shim note above.
- `task list` shows another repo's tasks — `TASKDATA` is not set for this
  directory. Run `direnv allow .` or `eval "$(mise env -s bash)"`.
- The MCP server reads the wrong list — opencode is not running with the mise
  env; confirm the command is `mise x -- taskwarrior-mcp`.
- A fresh clone has no `.taskdata/` — it is tracked, so a normal checkout brings
  it; if it is missing, `mise run task-init` recreates an empty one.

## Relationship to the roadmap

`docs/ROADMAP.md` is the human-readable plan (implemented / in progress /
backlog). Taskwarrior is the working queue: one task per roadmap item that
somebody is actually going to pick up, plus the immediate steps that do not
belong in a roadmap. Keep the two in sync by hand, like `../desktop` does.
