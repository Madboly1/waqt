# WAQT 5.6.11 – Live Voice Removal

- Removed the experimental Gemini Live Voice button and dialog from the AI chat UI.
- Removed the GeminiLiveVoiceSession WebSocket/audio implementation and its OkHttp dependency.
- Kept regular AI chat, image attachments, push-to-talk voice-message recording, and chat history intact.
- Kept microphone permission handling for voice-message recording and voice notes; only the Live Voice-specific permission flow was removed.

This source change has not been built or device-tested.
