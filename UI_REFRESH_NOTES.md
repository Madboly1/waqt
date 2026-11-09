# WAQT UI Refresh — 3D Petrol & Orange

## UI changes
- Replaced the full-screen four-way radial selector with a lower semicircular arc that rises from the bottom trigger. Each arc segment has a tab icon and label; the selected segment is highlighted in orange.
- Added subtle depth to task and habit cards using petrol gradients, elevation, and a slight 3D rotation.
- Changed tasks, habits, and notes to side-swipe carousels: the active card sits in front, while previous/next cards peek from the sides with low opacity and a light blur on Android 12+.
- Added a short slide/tilt exit transition when switching carousel cards.
- Updated the floating overlay border from the old blue tone to teal/petrol.

## Fixes and settings
- Local audio is selected through the Storage Access Framework, and its URI/name are persisted so the app can recover the selection after restart.
- Added custom audio playback/stop preview, volume persistence, and volume control for the custom player.
- Kept the custom audio player lifecycle cleanup in `onDestroy`.
- Added a restore-backup action alongside export, with a confirmation dialog and validation through the existing repository import routine.
- Backup import now restores the settings included in the exported JSON, in addition to tasks, notes, categories, and quotes.
- Saving the default break duration updates an idle break timer's duration as well as the stored preference.
- Bumped app version to 4.1.0 (versionCode 5).

## Validation
- XML resources parsed successfully.
- Kotlin source brace/parenthesis counts are balanced.
- A full Android Gradle build was not run in this environment because Gradle/Android SDK are not installed here. Build using the included GitHub Actions workflow or a local Android setup, then test carousel gestures, the arc selector, alarms, custom audio, and backup restore on a device before release.

## Notes
- The blur effect is enabled on Android 12+; older Android versions keep the same side-card depth using scale, opacity, and 3D rotation.
- This package contains the editable Android project source, not a verified APK.
