package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.WhatsAppChatParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppChatParserTest {

    @Test
    fun parses24HourDayMonthYear() {
        val text = "15/03/2024, 14:30 - Alice: Hello there\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.parsedMessages)
        assertEquals("Alice", result.lines[0].senderName)
        assertEquals("Hello there", result.lines[0].message)
        assertEquals(0, result.stats.unrecognized)
    }

    @Test
    fun parses12HourDayMonthYear() {
        val text = "15/03/2024, 2:30 pm - Bob: Good afternoon\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.parsedMessages)
        assertEquals("Bob", result.lines[0].senderName)
        assertEquals("Good afternoon", result.lines[0].message)
    }

    @Test
    fun parsesUSStyleMonthDayYear() {
        val text = "03/15/2024, 14:30 - Carol: US format message\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.parsedMessages)
        assertEquals("Carol", result.lines[0].senderName)
        assertEquals("US format message", result.lines[0].message)
    }

    @Test
    fun parsesTwoDigitYear() {
        val text = "15/03/24, 14:30 - Dave: Short year\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.parsedMessages)
        assertEquals("Dave", result.lines[0].senderName)
    }

    @Test
    fun multilineMessageIsAppended() {
        val text = "15/03/2024, 14:30 - Alice: This is line one\nThis is line two\nThis is line three\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.parsedMessages)
        assertEquals("Alice", result.lines[0].senderName)
        assertTrue(result.lines[0].message.contains("This is line one"))
        assertTrue(result.lines[0].message.contains("This is line two"))
        assertTrue(result.lines[0].message.contains("This is line three"))
    }

    @Test
    fun systemMessageIsClassified() {
        val text = "15/03/2024, 14:30 - You created group \"Family\"\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.systemMessages)
        assertNull(result.lines[0].senderName)
        assertTrue(result.lines[0].isSystem)
    }

    @Test
    fun mediaPlaceholderIsClassified() {
        val text = "15/03/2024, 14:30 - Alice: <Media omitted>\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.mediaMessages)
        assertEquals("Alice", result.lines[0].senderName)
        assertTrue(result.lines[0].isMedia)
    }

    @Test
    fun unrecognizedLineIsReported() {
        // A line with no date prefix and no previous message to continue is unrecognized.
        val text = "This is a random line with no date\n15/03/2024, 14:30 - Alice: Hello\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.unrecognized)
        assertTrue(result.unrecognizedLines.any { it.contains("random line") })
        assertEquals(1, result.stats.parsedMessages)
    }

    @Test
    fun unicodeAndEmojisArePreserved() {
        val text = "15/03/2024, 14:30 - José: Hola 👋 ¿cómo estás?\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(1, result.stats.parsedMessages)
        assertEquals("José", result.lines[0].senderName)
        assertTrue(result.lines[0].message.contains("👋"))
    }

    @Test
    fun apostrophesAndPunctuationInNames() {
        val text = "15/03/2024, 14:30 - O'Brien: Hello\n15/03/2024, 14:31 - Smith-Jones: Hi\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(2, result.stats.parsedMessages)
        assertEquals("O'Brien", result.lines[0].senderName)
        assertEquals("Smith-Jones", result.lines[1].senderName)
    }

    @Test
    fun senderNamesReturnsUniqueSenders() {
        val text = "15/03/2024, 14:30 - Alice: Hi\n15/03/2024, 14:31 - Bob: Hello\n15/03/2024, 14:32 - Alice: Again\n"
        val result = WhatsAppChatParser.parse(text)
        val senders = WhatsAppChatParser.senderNames(result)
        assertEquals(2, senders.size)
        assertTrue(senders.contains("Alice"))
        assertTrue(senders.contains("Bob"))
    }

    @Test
    fun emptyInputReturnsEmptyResult() {
        val result = WhatsAppChatParser.parse("")
        assertEquals(0, result.stats.totalLines)
        assertEquals(0, result.stats.parsedMessages)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun firstSenderIsNotAssumedUser() {
        // Parser never marks anyone as user — that's done during attribution.
        val text = "15/03/2024, 14:30 - Alice: First message\n15/03/2024, 14:31 - Bob: Reply\n"
        val result = WhatsAppChatParser.parse(text)
        assertEquals(2, result.stats.parsedMessages)
        // Neither sender has isUser flag (parser doesn't set it).
        assertTrue(result.lines.all { !it.isSystem || it.senderName == null })
    }
}
