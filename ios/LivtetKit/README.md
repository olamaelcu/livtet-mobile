# LivtetKit

Swift Package Manager project wrapping the `livtet-ffi` Rust library via
UniFFI-generated bindings.

## Structure

| Target           | Path                    | Description                                                           |
| ---------------- | ----------------------- | --------------------------------------------------------------------- |
| `LivtetKit`      | `Sources/LivtetKit/`    | Public Swift API with type aliases and extensions                     |
| `LivtetKitFFI`   | `Sources/LivtetKitFFI/` | UniFFI-generated Swift bindings (produced by `mise run ios-bindings`) |
| `LivtetKitTests` | `Tests/LivtetKitTests/` | Unit tests                                                            |

## Build

1. Build the Rust static library for the target platform:

   ```
   cargo build -p livtet-ffi --release --target aarch64-apple-ios
   ```

2. Generate Swift bindings:

   ```
   mise run ios-bindings
   ```

3. Open and build in Xcode:
   ```
   open Package.swift
   ```

## Dependencies

- [FastULID](https://github.com/elijahdou/FastULID) — ULID type mapped from `DbId` via `uniffi.toml`

## Configuration

The `uniffi.toml` at `crates/livtet-ffi/uniffi.toml` configures:

- `DbId` → `ULID` (FastULID.Swift)
