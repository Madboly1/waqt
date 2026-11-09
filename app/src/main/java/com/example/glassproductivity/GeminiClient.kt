package com.example.glassproductivity

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Gemini REST client. Network work runs off the UI thread; callbacks return on the main thread. */
object GeminiClient {
    data class MediaInput(val bytes: ByteArray, val mimeType: String, val label: String)
    // Google's current low-latency, cost-efficient text model for everyday requests.
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    // One capable fallback if the selected/default model is unavailable or temporarily busy.
    const val FALLBACK_MODEL = "gemini-3.8-flash"

    fun generate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        media: MediaInput? = null,
        callback: (Result<String>) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        thread(name = "waqt-gemini") {
            val primary = model.trim().ifBlank { DEFAULT_MODEL }
            val candidates = listOf(primary, FALLBACK_MODEL).distinct()
            var result: Result<String> = Result.failure(IllegalStateException("No model attempt was made"))

            for ((index, candidateModel) in candidates.withIndex()) {
                result = callOnce(apiKey, candidateModel, systemPrompt, history, media)
                if (result.isSuccess) break

                val message = result.exceptionOrNull()?.message.orEmpty()
                // Do not waste time retrying authentication, malformed requests, or quota errors.
                val shouldFallback = message.startsWith("HTTP 404") ||
                    message.startsWith("HTTP 429") ||
                    message.startsWith("HTTP 500") ||
                    message.startsWith("HTTP 502") ||
                    message.startsWith("HTTP 503") ||
                    message.startsWith("HTTP 504")
                if (!shouldFallback || index == candidates.lastIndex) break
                // No long retry chain: move to the fallback immediately.
            }
            main.post { callback(result) }
        }
    }

    private fun callOnce(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        media: MediaInput?
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            require(apiKey.isNotBlank()) { "API key is empty" }
            val contents = JSONArray()
            val recent = history.takeLast(8)
            recent.forEachIndexed { index, (role, message) ->
                if (message.isNotBlank()) {
                    val parts = JSONArray().put(JSONObject().put("text", message))
                    if (media != null && index == recent.lastIndex && role != "model") {
                        parts.put(JSONObject().put("inlineData", JSONObject()
                            .put("mimeType", media.mimeType)
                            .put("data", Base64.encodeToString(media.bytes, Base64.NO_WRAP))))
                    }
                    contents.put(JSONObject()
                        .put("role", if (role == "model") "model" else "user")
                        .put("parts", parts))
                }
            }
            if (contents.length() == 0) return Result.failure(IllegalArgumentException("No message to send"))

            val generationConfig = JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.55)
                .put("topP", 0.9)
                .put("maxOutputTokens", 1600)

            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .put("generationConfig", generationConfig)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 22000
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }

            val payload = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }

            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val message = try {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() } ?: responseText.take(240)
                } catch (_: Exception) { responseText.take(240) }
                Result.failure(Exception("HTTP $status: $message"))
            } else {
                val root = JSONObject(responseText)
                val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                val answer = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }.trim()

                if (answer.isBlank()) {
                    val reason = candidate?.optString("finishReason").orEmpty()
                    val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    val detail = when {
                        blockReason.isNotBlank() -> "Blocked: $blockReason"
                        reason.isNotBlank() -> "Empty response ($reason)"
                        else -> "Gemini returned an empty response"
                    }
                    Result.failure(Exception(detail))
                } else {
                    Result.success(answer)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

}
