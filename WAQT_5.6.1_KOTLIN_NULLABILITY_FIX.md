# WAQT 5.6.1 — Kotlin nullability fix

- Fixed the reported Kotlin compile error in `MainActivity.kt` at the journal audio recording save handler.
- `outputFile?.absolutePath` is nullable; the save handler now explicitly requires a valid output path and throws a controlled exception if the recording file is unexpectedly unavailable.
- Bumped Android version to `5.6.1` (`versionCode` 27).

Build verification: source-level fix and ZIP integrity checked. A Gradle Android build was not run in this environment.
