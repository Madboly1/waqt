# WAQT 5.6.6 — Live Voice Connection Fix

## Changes
- Added a 20-second timeout while waiting for Gemini's `setupComplete` event.
- Added explicit status stages: opening WebSocket, sending setup, waiting for Gemini, and connected.
- Added readable diagnostics for WebSocket HTTP failures and Gemini JSON error responses.
- Prevented microphone streaming until `setupComplete` is received.
- Added microphone permission/device/recording-state checks and audio send error reporting.
- Handles transcription events both at the top-level and inside `serverContent`.
- Preserves the actual connection error in the Live Voice dialog instead of overwriting it with the generic ended status.
- Kept Gemini model `gemini-3.8-live` and the existing Live Voice UI.

## Validation
- ZIP archive integrity checked after packaging.
- Android Gradle build and on-device Gemini Live connection test were not run in this environment. A real API key, network access, and Android device are required to verify the handshake end-to-end.
