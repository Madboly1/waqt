# WAQT 5.6.2 — AI Assistant Repair

Based on the last agreed stable source: WAQT 5.6.1. No unrelated UI or feature redesigns.

## AI connectivity and reliability
- Replaced the old rotating `gemini-flash-latest` default with Google's documented `gemini-3.5-flash-lite` model.
- Added `gemini-3.8-flash` as a single fallback for unavailable or temporarily busy primary model responses.
- Removed the previous multi-second retry/sleep chain. The fallback is attempted immediately for model-not-found, rate-limit, and transient server errors.
- Improved HTTP response parsing, empty-response diagnostics, and guaranteed connection cleanup.
- Model picker continues to list models actually exposed by the user's API key and supporting `generateContent`.

## Faster request payloads
- The current day snapshot is sent only with the current request instead of being stored and resent in every history turn.
- Conversation history is limited to the last three exchanges.
- Day snapshot size reduced to the 15 most recent task entries and 10 habits.
- Kept the assistant's JSON reply/actions contract and existing task/note/focus action handling.

## Version
- `versionCode`: 28
- `versionName`: 5.6.2

## Verification status
- Source archive integrity and static source checks performed.
- Android Gradle build was not run in this environment; GitHub Actions/device testing is still required.
