# WAQT 5.6.4 – Live Voice & Chat History

## Chat history
- Added an on-device SQLite conversation store.
- Conversations and text transcripts are retained between app launches.
- Added a previous-conversations list in the assistant header.
- Added a new-chat action and a delete-current-chat action.
- Restores recent messages into the assistant context when a conversation is reopened.

## Interactive voice
- Added a dedicated live voice conversation button.
- Uses Gemini Live over a persistent WebSocket session (`gemini-3.8-live`).
- Streams microphone audio as 16 kHz mono PCM in short chunks.
- Plays model audio output as streamed 24 kHz PCM.
- Enables input and output transcription for a readable transcript.
- Supports natural turn-taking and interruption through the Live API.
- Requests microphone permission only when live voice is started.

## Dependencies / version
- Added OkHttp 4.12.0 for WebSocket transport.
- Version: 5.6.4 (versionCode 30).

## Important limitations / verification
- Chat history is stored locally on the device; deleting app data removes it.
- Text transcripts are stored; raw live audio is not retained.
- Gemini Live requires a valid API key and network access. Availability and quotas depend on the key/project.
- The API key is used directly by the Android app, consistent with the existing Gemini REST integration. A production release should proxy Live API credentials through a secure backend or use short-lived ephemeral tokens.
- Archive integrity can be checked locally, but Android Gradle build and real-device microphone/audio testing must still be run in CI/device before release.
