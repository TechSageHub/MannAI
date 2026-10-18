package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.GeminiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProviderTest {

    private fun provider(apiKey: String = "test-key", model: String = "gemini-1.5-flash") =
        GeminiProvider(apiKey, model)

    @Test
    fun isConfiguredFalseWhenKeyOrModelMissing() {
        assertFalse(provider("", "gemini-1.5-flash").isConfigured)
        assertFalse(provider("test-key", "").isConfigured)
        assertFalse(provider("", "").isConfigured)
    }

    @Test
    fun isConfiguredTrueWhenKeyAndModelPresent() {
        assertTrue(provider().isConfigured)
    }

    @Test
    fun errorStatusMapsToSafeMessagesWithoutLeakingKey() {
        assertEquals("Invalid Gemini API key or request.", provider().errorForStatus(400)?.message)
        assertEquals("Invalid Gemini API key or request.", provider().errorForStatus(403)?.message)
        assertEquals("Gemini rate limit reached. Try again later.", provider().errorForStatus(429)?.message)
        assertEquals("Gemini service temporarily unavailable.", provider().errorForStatus(500)?.message)
        assertEquals("Gemini service temporarily unavailable.", provider().errorForStatus(502)?.message)
        assertEquals("Gemini service temporarily unavailable.", provider().errorForStatus(503)?.message)
        assertNull("2xx should not produce a status error", provider().errorForStatus(200))
        assertNull("unknown status returns null (generic fallback)", provider().errorForStatus(520))
    }

    @Test
    fun defaultModelIsGemini1_5Flash() {
        assertEquals("gemini-1.5-flash", GeminiProvider.DEFAULT_MODEL)
    }

    private fun assertThrows(message: String, block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(message, threw)
    }
}
