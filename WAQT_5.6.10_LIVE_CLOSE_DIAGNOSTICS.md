# WAQT 5.6.10 — Live close diagnostics

- Preserve the last server message received before a WebSocket close.
- Report the connection stage and close reason/code when Gemini closes the session.
- Prevent a generic onClosing status from overwriting a useful server error.
- Bump Android version to 5.6.10 (versionCode 36).

Validation: source-level inspection and ZIP integrity only. Android Gradle build and a real Gemini session were not run.
