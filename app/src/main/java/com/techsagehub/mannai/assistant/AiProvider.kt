package com.techsagehub.mannai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional online language-model integration.
 *
 * Privacy-first: completely inert unless the user enters their own
 * OpenAI-compatible endpoint + key in Settings (stored only on this phone,
 * never in source control). Offline features never depend on this.
 */
interface AiProvider {
    val isConfigured: Boolean
    suspend fun chat(system: String, user: String): Result<String>
}

class OpenAiCompatibleProvider(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String
) : AiProvider {
    override val isConfigured: Boolean
        get() = endpoint.isNotBlank() && apiKey.isNotBlank()

    override suspend fun chat(system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(endpoint.trimEnd('/') + "/chat/completions")
                val body = JSONObject()
                    .put("model", model.ifBlank { "gpt-4o-mini" })
                    .put("temperature", 0.7)
                    .put("max_tokens", 500)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("AI service error ($code): ${text.take(200)}")
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
}

class DisabledAiProvider : AiProvider {
    override val isConfigured = false
    override suspend fun chat(system: String, user: String): Result<String> =
        Result.failure(IllegalStateException("Online AI is not configured. Offline features still work."))
}
