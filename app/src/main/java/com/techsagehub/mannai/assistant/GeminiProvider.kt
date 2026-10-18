package com.techsagehub.mannai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Gemini API provider for reply drafting.
 *
 * Privacy notes:
 * - Completely inert unless the user enters their own Gemini API key in Settings.
 * - The key is sent only as the `key` query parameter on the request to
 *   generativelanguage.googleapis.com, never logged, never stored in source control.
 * - Only the selected context for a specific reply-generation request is transmitted.
 * - No automatic provider switching: failures are reported as errors, never silently
 *   retried through a different service.
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String
) : AiProvider {

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"
    private val connectTimeoutMs = 15_000
    private val readTimeoutMs = 30_000
    private val maxOutputTokens = 500
    private val callTimeoutBufferMs = 5_000

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank()

    override suspend fun chat(system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            val callTimeoutMs = (readTimeoutMs + callTimeoutBufferMs).toLong()
            val result: Result<String>? = withTimeoutOrNull(callTimeoutMs) {
                runCatching {
                    val url = URL("$baseUrl/models/${model.trim()}:generateContent?key=$apiKey")
                    val body = buildRequestBody(system, user)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        setRequestProperty("Content-Type", "application/json")
                        // Never log or persist this header; it carries the API key.
                        setRequestProperty("X-Goog-Api-Key", apiKey)
                        doOutput = true
                    }
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val text = stream.bufferedReader().use { it.readText() }
                    if (code !in 200..299) {
                        throw errorForStatus(code) ?: IllegalStateException("Gemini service error ($code).")
                    }
                    val parsed = parseResponse(text)
                    if (parsed.isBlank()) throw IllegalStateException("Gemini returned an empty reply.")
                    parsed.trim()
                }
            }
            return@withContext result ?: Result.failure(
                IllegalStateException("AI request timed out. Please try again.")
            )
        }

    /** Maps a non-success HTTP status to a user-safe error, never leaking credentials. */
    internal fun errorForStatus(code: Int): IllegalStateException? {
        return when (code) {
            400, 403 -> IllegalStateException("Invalid Gemini API key or request.")
            429 -> IllegalStateException("Gemini rate limit reached. Try again later.")
            500, 502, 503 -> IllegalStateException("Gemini service temporarily unavailable.")
            else -> null
        }
    }

    internal fun buildRequestBody(system: String, user: String): String {
        val body = JSONObject()
        val userPart = JSONObject().put("text", user)
        val userParts = JSONArray().put(userPart)
        val userContent = JSONObject().put("role", "user").put("parts", userParts)
        val contents = JSONArray().put(userContent)
        body.put("contents", contents)

        if (system.isNotBlank()) {
            val sysPart = JSONObject().put("text", system)
            val sysParts = JSONArray().put(sysPart)
            body.put("systemInstruction", JSONObject().put("parts", sysParts))
        }

        val genConfig = JSONObject()
        genConfig.put("temperature", 0.7)
        genConfig.put("maxOutputTokens", maxOutputTokens)
        body.put("generationConfig", genConfig)

        return body.toString()
    }

    internal fun parseResponse(text: String): String {
        val root = JSONObject(text)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val pf = root.optJSONObject("promptFeedback")
            val reason = pf?.optJSONArray("safetyRatings")?.let { sr ->
                sr.length().takeIf { it > 0 }?.let { "blocked" } ?: "no candidate"
            } ?: "no candidate"
            throw IllegalStateException("Gemini returned no generated content ($reason).")
        }
        val first = candidates.getJSONObject(0)
        val content = first.optJSONObject("content")
        if (content == null) throw IllegalStateException("Malformed Gemini response.")
        val parts = content.optJSONArray("parts")
        if (parts == null || parts.length() == 0) throw IllegalStateException("Malformed Gemini response.")
        return parts.getJSONObject(0).optString("text", "")
    }

    companion object {
        /** Default Gemini model used when the user has not overridden it. */
        const val DEFAULT_MODEL = "gemini-1.5-flash"
    }
}
