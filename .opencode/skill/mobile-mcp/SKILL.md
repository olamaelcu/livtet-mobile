---
name: mobile-mcp
description: Mobile automation and debugging for Android and iOS apps via accessibility snapshots. Use when asked to test, debug, inspect, screenshot, tap, swipe, type, navigate, or crash-report on the Livtet mobile apps. Triggers on "screen", "tap", "click", "screenshot", "debug on device", "what's on screen", "launch the app", "crash", "install on device", "run on emulator", "simulator".
---

# Mobile MCP

## When to use

Use mobile-mcp tools when the user asks you to interact with or inspect a
running Livtet Android or iOS app. The server provides platform-agnostic
accessibility-tree snapshots and coordinate-based interactions, so you don't
need separate Android/iOS expertise.

Always run `mobile_list_available_devices` first to confirm a target is
connected before issuing any other `mobile_*` tool.

## Device requirements

- **Android**: emulator booted via `mise run android-emulator --start phone`
  (or the `apk` alias), or a real device attached via USB with `adb devices`
  showing it. Do NOT start or kill an emulator yourself — the guardrails in
  `android/AGENTS.md` forbid it. Ask the user or suggest the mise alias.
- **iOS** (macOS only): Xcode + a booted simulator (`xcrun simctl list
  devices booted`). Same guardrail — never boot or shut down a simulator
  without asking. See `ios/AGENTS.md`.
- On Linux, iOS tools return an empty device list because `xcrun` is absent.
  That's expected — don't retry or loop.

## Tool categories

- **Device management**: `mobile_list_available_devices`, `mobile_get_screen_size`,
  `mobile_get_orientation`, `mobile_set_orientation`
- **App management**: `mobile_list_apps`, `mobile_launch_app` (package:
  `net.olamaelcu.livtet`), `mobile_terminate_app`, `mobile_install_app`,
  `mobile_uninstall_app`
- **Screen interaction**: `mobile_take_screenshot`, `mobile_save_screenshot`,
  `mobile_list_elements_on_screen`, `mobile_click_on_screen_at_coordinates`,
  `mobile_double_tap_on_screen`, `mobile_long_press_on_screen_at_coordinates`,
  `mobile_swipe_on_screen`
- **Input & navigation**: `mobile_type_keys`, `mobile_press_button`, `mobile_open_url`
- **Crash reports**: `mobile_list_crashes`, `mobile_get_crash`

Always prefer `mobile_list_elements_on_screen` over coordinate-based taps when
possible — the accessibility snapshot is deterministic and cheaper.

## Build artifacts on the device

The Livtet app must already be installed and running on the target device.
To install:
- Android: `mise run android-build && mise run android-install` (ask the user first)
- iOS: `mise run ios-build && mise run ios-run-simulator` (macOS only, ask first)

Do NOT run these build commands yourself — always ask, per the guardrails in
the platform AGENTS.md files.
