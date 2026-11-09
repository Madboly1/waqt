# WAQT 5.6.3 — AI Chat Cleanup & Multimodal

## AI model controls
- Removed the large dynamic model catalogue and the model-selection dialog.
- The chat now uses one fixed default model (`gemini-3.5-flash-lite`) and one internal fallback (`gemini-3.8-flash`).
- Removed the model picker from the Gemini API-key dialog and Settings; users no longer have to choose a model.

## Image chat
- Added an image attachment button to the AI chat.
- Supports JPEG, PNG and WebP through Android's document picker.
- Sends image bytes as Gemini `inlineData` with the correct MIME type.
- Rejects image attachments larger than 12 MB before sending.

## Voice chat
- Added an in-chat microphone button.
- Requests microphone permission only when the user taps the microphone.
- Records an M4A/AAC voice message and sends it to Gemini as `audio/mp4`.
- Automatically reads the assistant's response aloud using Android TextToSpeech after a voice message.
- Added a speaker button to read the latest assistant response again.
- Stops and releases the recorder when the chat is dismissed; shuts down TTS with the Activity.

## Notes
- This is voice-message chat with spoken assistant replies, not a continuous full-duplex live call.
- The existing Gemini API key is still required for cloud AI features.
- Android Build and live Gemini requests were not run in this environment.
