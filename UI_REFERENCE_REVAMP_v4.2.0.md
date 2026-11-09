# WAQT UI Reference Revamp — v4.2.0

This revision updates the Android source project to better follow the supplied WAQT UI reference (dark petrol, warm orange accents, layered cards, and a raised bottom arc selector).

## Changes made

- Rebuilt the radial navigation selector as four filled annular sectors instead of thick stroked arc lines. Each sector has a dark petrol gradient, subtle teal edge, and a warm orange selected state.
- Added a fixed ambient background layer with very subtle petrol and orange light blooms, while keeping content readable.
- Refined the bottom trigger button to look like a dark glass control with an orange outline instead of a flat orange disk.
- Refined the task, habit, and note carousel: narrowed cards with side peeks, stronger foreground elevation, slightly more visible rear cards, a restrained blur on Android 12+, and a more legible carousel counter pill.
- Increased foreground card border definition to better separate the active card from the rear stack.
- Kept the previous settings/audio/backup work in place and updated the project version to 4.2.0 (versionCode 6).

## Validation performed

- XML resources parsed successfully.
- Kotlin source was checked for syntax diagnostics using the Kotlin compiler parser; a full compile could not complete because this environment does not contain the Android SDK / Android Gradle dependencies.
- This archive is the editable Android project source, not an APK. Build it with Android SDK + Gradle 8.7 + JDK 17, then test the arc gesture, carousel gestures, alarms, audio playback, and backup restore on-device.

## Build

From the project root:

```bash
gradle assembleDebug --no-daemon
```

The debug APK should be generated at `app/build/outputs/apk/debug/app-debug.apk`.
