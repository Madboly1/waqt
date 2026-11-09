# WAQT 5.5.0 – Journal & Focus Audio

Built on the user's WAQT 5.4.0 Liquid Glass Audio/UI source.

## Changes
- Added a personal audio row to the Pomodoro focus dashboard. Audio can still be selected from the left rail; the selected file is shown beside ambient sound presets with play/pause and add controls.
- Renamed the Notes tab to My Journal / مدونتي in the main navigation and related onboarding labels.
- Extended NoteItem with backward-compatible `audioPath` and `imageUri` fields; old saved notes continue to load.
- Added voice-note recording saved inside the app's private files directory, plus playback from the journal card.
- Added image attachment from the device document picker and camera preview capture; images appear on the journal card.
- Added RECORD_AUDIO permission declaration and runtime permission request.

## Verification
- ZIP archive integrity checked.
- Android Gradle build was not run in this environment; please run `gradle assembleDebug` / the repository's GitHub Actions workflow before installing.
