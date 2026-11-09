# WAQT 5.6.8 — Live Connection Scheme Fix

## Root cause
`HttpUrl.Builder` from OkHttp does not accept the `wss` scheme. The Live session URL builder therefore throws `unexpected scheme: wss` before a WebSocket connection can even be attempted.

## Fix
Build the URL with `https`. OkHttp's `newWebSocket()` upgrades the HTTPS request to a secure WebSocket (WSS) internally. The Gemini endpoint path, API key query parameter, model, and session setup remain unchanged.

## Validation
- Confirmed the URL builder now uses `https`.
- Confirmed the project version is 5.6.8 / versionCode 34.
- ZIP integrity checked after packaging.
- Android Gradle build and real-device Gemini Live connection were not run in this environment.
