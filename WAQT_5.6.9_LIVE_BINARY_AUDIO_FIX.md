# WAQT 5.6.9 — Live Binary Audio Fix

## What changed
- Fixed Gemini Live binary WebSocket message handling.
- Binary frames are treated as raw mono PCM16 little-endian audio at 24 kHz and sent to AudioTrack.
- Added guards for audio arriving before setup completion and incomplete PCM samples.
- Kept JSON text-frame handling for transcripts, setup events, and inline audio payloads.
- Bumped Android version to 5.6.9 (versionCode 35).

## Validation
- Source-level change and ZIP integrity checked.
- Android Gradle build and live-device/API test were not run in this environment.
