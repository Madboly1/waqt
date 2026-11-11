package com.example.glassproductivity

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Gemini is primary; text requests fall back to Groq and then OpenRouter. */
object GeminiClient {
    data class MediaInput(val bytes: ByteArray, val mimeType: String, val label: String, val localPath: String? = null)
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    const val FALLBACK_MODEL = "gemini-3.8-flash"
    const val GROQ_MODEL = "openai/gpt-oss-120b"
    const val OPENROUTER_MODEL = "openrouter/free"

    private data class Attempt(val provider: String, val result: Result<String>)

    fun generate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        media: MediaInput? = null,
        groqApiKey: String = "",
        openRouterApiKey: String = "",
        callback: (Result<String>) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        thread(name = "waqt-ai-router") {
            val attempts = mutableListOf<Attempt>()
            val geminiKey = apiKey.trim()
            val groqKey = groqApiKey.trim()
            val routerKey = openRouterApiKey.trim()

            if (media != null) {
                if (geminiKey.isBlank()) {
                    attempts += Attempt("Gemini", Result.failure(IllegalStateException("Image and voice messages currently require a Gemini API key.")))
                } else {
                    var result: Result<String> = Result.failure(IllegalStateException("No Gemini model attempt was made"))
                    for (candidate in listOf(model.trim().ifBlank { DEFAULT_MODEL }, FALLBACK_MODEL).distinct()) {
                        result = retryTransient { callGemini(geminiKey, candidate, systemPrompt, history, media) }
                        if (result.isSuccess || !isRetryable(result.exceptionOrNull())) break
                    }
                    attempts += Attempt("Gemini", result)
                }
            } else {
                if (geminiKey.isNotBlank()) {
                    var result: Result<String> = Result.failure(IllegalStateException("No Gemini model attempt was made"))
                    for (candidate in listOf(model.trim().ifBlank { DEFAULT_MODEL }, FALLBACK_MODEL).distinct()) {
                        result = retryTransient { callGemini(geminiKey, candidate, systemPrompt, history, null) }
                        if (result.isSuccess || !isRetryable(result.exceptionOrNull())) break
                    }
                    attempts += Attempt("Gemini", result)
                    if (result.isSuccess) {
                        main.post { callback(result) }
                        return@thread
                    }
                }
                if (groqKey.isNotBlank()) {
                    val result = retryTransient { callOpenAiCompatible(
                        "https://api.groq.com/openai/v1/chat/completions", groqKey,
                        GROQ_MODEL, systemPrompt, history, "Groq"
                    ) }
                    attempts += Attempt("Groq", result)
                    if (result.isSuccess) {
                        main.post { callback(result) }
                        return@thread
                    }
                }
                if (routerKey.isNotBlank()) {
                    val result = retryTransient { callOpenAiCompatible(
                        "https://openrouter.ai/api/v1/chat/completions", routerKey,
                        OPENROUTER_MODEL, systemPrompt, history, "OpenRouter"
                    ) }
                    attempts += Attempt("OpenRouter", result)
                    if (result.isSuccess) {
                        main.post { callback(result) }
                        return@thread
                    }
                }
            }

            val details = attempts.joinToString(" | ") {
                "${it.provider}: ${it.result.exceptionOrNull()?.message ?: "unknown error"}"
            }.ifBlank { "No provider API key is configured." }
            main.post { callback(Result.failure(Exception(details))) }
        }
    }


    /** Retry transient DNS/socket failures once; provider errors (4xx) are never retried. */
    private fun retryTransient(request: () -> Result<String>): Result<String> {
        var result = request()
        repeat(1) {
            val error = result.exceptionOrNull()
            val transient = error is java.net.UnknownHostException ||
                error is java.net.ConnectException ||
                error is java.net.SocketTimeoutException ||
                (error is java.net.SocketException && error.message.orEmpty().contains("abort", ignoreCase = true))
            if (!transient) return result
            try { Thread.sleep(650L) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return result }
            result = request()
        }
        return result
    }

    private fun isRetryable(error: Throwable?): Boolean =
        error != null && (
            error.message.orEmpty().startsWith("HTTP ") ||
            error is java.net.SocketTimeoutException ||
            error is java.net.ConnectException ||
            error is java.io.IOException
        )

    private fun callGemini(
        apiKey: String, model: String, systemPrompt: String,
        history: List<Pair<String, String>>, media: MediaInput?
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            require(apiKey.isNotBlank()) { "Gemini API key is empty" }
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
                    contents.put(JSONObject().put("role", if (role == "model") "model" else "user").put("parts", parts))
                }
            }
            if (contents.length() == 0) return Result.failure(IllegalArgumentException("No message to send"))
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .put("generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.55).put("topP", 0.9).put("maxOutputTokens", 1600))

            connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20000; readTimeout = 45000
                doOutput = true; useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "WAQT-Android/5.6.27")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = try {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() } ?: response.take(240)
                } catch (_: Exception) { response.take(240) }
                Result.failure(Exception("HTTP $status: $detail"))
            } else {
                val root = JSONObject(response)
                val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                val answer = buildString {
                    if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }.trim()
                if (answer.isNotBlank()) Result.success(answer) else {
                    val reason = candidate?.optString("finishReason").orEmpty()
                    val blocked = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    Result.failure(Exception(blocked.ifBlank { reason.ifBlank { "Gemini returned an empty response" } }))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun callOpenAiCompatible(
        endpoint: String, apiKey: String, model: String, systemPrompt: String,
        history: List<Pair<String, String>>, provider: String
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            require(apiKey.isNotBlank()) { "$provider API key is empty" }
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(8).forEach { (role, content) ->
                if (content.isNotBlank()) messages.put(JSONObject()
                    .put("role", if (role == "model") "assistant" else "user")
                    .put("content", content))
            }
            val body = JSONObject().put("model", model).put("messages", messages)
                .put("temperature", 0.55).put("max_tokens", 1600)
                .put("response_format", JSONObject().put("type", "json_object"))
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10000; readTimeout = 28000
                doOutput = true; useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                if (provider == "OpenRouter") {
                    setRequestProperty("HTTP-Referer", "https://github.com/Madboly1/waqt")
                    setRequestProperty("X-Title", "WAQT")
                }
            }
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = try {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() } ?: response.take(240)
                } catch (_: Exception) { response.take(240) }
                Result.failure(Exception("HTTP $status: $detail"))
            } else {
                val message = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                val content = when (val raw = message?.opt("content")) {
                    is String -> raw
                    is JSONArray -> buildString {
                        for (i in 0 until raw.length()) append(raw.optJSONObject(i)?.optString("text").orEmpty())
                    }
                    else -> ""
                }.trim()
                if (content.isBlank()) Result.failure(Exception("$provider returned an empty response"))
                else Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
