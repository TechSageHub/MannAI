package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.WhatsAppMessageEvent
import com.techsagehub.mannai.assistant.WhatsAppNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppNotificationParserTest {

    private val now = System.currentTimeMillis()

    @Test
    fun nonWhatsAppPackageIsIgnored() {
        val result = WhatsAppNotificationParser.parse(
            packageName = "com.whatsapp.w4b",
            title = "Alice",
            text = "Hello",
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNull("Non-official WhatsApp package should be ignored", result)
    }

    @Test
    fun blankTitleFailsClosed() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "",
            text = "Hello",
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNull("Blank sender should fail closed", result)
    }

    @Test
    fun whitespaceOnlyTitleFailsClosed() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "   ",
            text = "Hello",
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNull("Whitespace-only sender should fail closed", result)
    }

    @Test
    fun blankMessageFailsClosed() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "Alice",
            text = "",
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNull("Blank message should fail closed", result)
    }

    @Test
    fun nullTitleAndTextFailsClosed() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = null,
            text = null,
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNull("Null title and text should fail closed", result)
    }

    @Test
    fun validNotificationProducesEvent() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "Alice",
            text = "Hello there",
            bigText = "Hello there, how are you?",
            isGroup = false,
            timestampMs = now
        )
        assertNotNull("Valid notification should produce an event", result)
        assertEquals("alice", result!!.sender)
        assertEquals("Hello there, how are you?", result.message)
        assertEquals(now, result.timestampMs)
        assertEquals(false, result.isGroup)
        assertEquals(WhatsAppNotificationParser.WHATSAPP_PACKAGE, result.packageName)
    }

    @Test
    fun bigTextPreferredOverText() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "Bob",
            text = "Hi",
            bigText = "Hi! This is the full message body.",
            isGroup = false,
            timestampMs = now
        )
        assertNotNull(result)
        assertEquals("Hi! This is the full message body.", result!!.message)
    }

    @Test
    fun textFallbackWhenBigTextMissing() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "Carol",
            text = "Fallback message",
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNotNull(result)
        assertEquals("Fallback message", result!!.message)
    }

    @Test
    fun groupNotificationIsMarked() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "Family Group",
            text = "Dinner at 7?",
            bigText = null,
            isGroup = true,
            timestampMs = now
        )
        assertNotNull(result)
        assertEquals("family group", result!!.sender)
        assertTrue(result.isGroup)
    }

    @Test
    fun senderIsNormalizedLowercase() {
        val result = WhatsAppNotificationParser.parse(
            packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE,
            title = "  Alice Smith  ",
            text = "Hello",
            bigText = null,
            isGroup = false,
            timestampMs = now
        )
        assertNotNull(result)
        assertEquals("alice smith", result!!.sender)
    }

    @Test
    fun normalizeSenderTrimsAndLowercases() {
        assertEquals("alice", WhatsAppNotificationParser.normalizeSender("  Alice  "))
        assertEquals("", WhatsAppNotificationParser.normalizeSender(null))
        assertEquals("", WhatsAppNotificationParser.normalizeSender("   "))
        assertEquals("bob jones", WhatsAppNotificationParser.normalizeSender("BoB Jones"))
    }

    @Test
    fun eventDataClassIsImmutable() {
        val event = WhatsAppMessageEvent(
            sender = "alice",
            message = "hi",
            timestampMs = now,
            isGroup = false,
            packageName = "com.whatsapp"
        )
        assertEquals("alice", event.sender)
        assertEquals("hi", event.message)
        assertEquals(now, event.timestampMs)
        assertEquals(false, event.isGroup)
        assertEquals("com.whatsapp", event.packageName)
    }
}
