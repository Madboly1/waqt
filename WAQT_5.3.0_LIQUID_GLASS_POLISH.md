# WAQT 5.3.0 — Liquid Glass Polish

This update is based on the supplied WAQT 5.2.0 source tree (as requested).

## UI changes
- Reworked the dark palette to a midnight-blue glass direction with icy cyan highlights.
- Increased translucency and layered reflections in shared glass card/button drawables.
- Added multi-stop glass gradients, fine luminous borders, and soft depth layers.
- Updated the app atmosphere to use blue, violet, and cyan light blooms.
- Restyled the header and floating AI assistant bubble to match the glass palette.
- Updated the floating navigation rail colors to fit the new cool-glass visual language.

## Compatibility
- Existing task, habit, focus, notes, assistant, storage, and audio logic was intentionally left intact.
- Android versionCode/versionName updated to 21 / 5.3.0.

## Build verification
The source archive was inspected and edited, but a full Android Gradle build was not run in this environment. Run `gradle assembleDebug` in GitHub Actions or Android Studio to verify the build.
