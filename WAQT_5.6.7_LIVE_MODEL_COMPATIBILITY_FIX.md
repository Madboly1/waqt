# WAQT 5.6.7 — Gemini Live model compatibility fix

## Root cause identified
The app connects to `generativelanguage.googleapis.com` using a Gemini Developer API key created for Google AI Studio. The previous build requested `gemini-3.8-live`, which is documented for the Google Cloud Agent Platform, not the Gemini Developer API key endpoint used by this app. This provider/model mismatch can prevent the Live session setup from completing.

## Change
- Switched the Live model to `gemini-3.1-flash-live-preview`, listed by Google for the Gemini Developer API Live API.
- Kept the existing WebSocket endpoint, API-key authentication, session setup flow, timeout, diagnostics, microphone handling, and Live UI.
- Version updated to 5.6.7 (versionCode 33).

## Validation
- Confirmed the selected model/provider pairing against current Google documentation.
- ZIP integrity checked after packaging.
- Android Gradle build and real-device connection test were not run in this environment.
