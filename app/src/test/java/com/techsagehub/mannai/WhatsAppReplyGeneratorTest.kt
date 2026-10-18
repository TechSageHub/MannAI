package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.AiProvider
import com.techsagehub.mannai.assistant.ReplyTone
import com.techsagehub.mannai.assistant.WhatsAppDraftResult
import com.techsagehub.mannai.assistant.WhatsAppMessageEvent
import com.techsagehub.mannai.assistant.WhatsAppReplyGenerator
import com.techsagehub.mannai.data.db.WhatsAppContactEntity
import com.techsagehub.mannai.data.db.WhatsAppMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class WhatsAppReplyGeneratorTest {

    private fun contact(id: Long, name: String) = WhatsAppContactEntity(
        id = id, displayName = name, normalizedName = name.trim().lowercase()
    )

    private fun message(
        contactId: Long,
        senderName: String,
        text: String,
        timestampMs: Long,
        isUser: Boolean = false
    ) = WhatsAppMessageEntity(
        contactId = contactId, senderName = senderName, message = text,
        timestampMs = timestampMs, isUser = isUser
    )

    private val now = 1_700_000_000_000L
    private val c = contact(1, "Alice")
    private val events = mapOf(
        1L to listOf(
            message(1, "Alice", "Hi", now - 10000, isUser = false),
            message(1, "You", "Hello", now - 5000, isUser = true)
        )
    )

    private class FakeProvider(
        override val isConfigured: Boolean = true,
        val result: Result<String> = Result.success("Sure, here is a draft.")
    ) : AiProvider {
        var calls = 0
        var lastSystem: String? = null
        var lastUser: String? = null
        override suspend fun chat(system: String, user: String): Result<String> {
            calls++
            lastSystem = system
            lastUser = user
            return result
        }
    }

    private val evt = WhatsAppMessageEvent("Alice", "What's up?", now, false, "com.whatsapp")

    @Test
    fun configuredProviderReturnsDraftAndPropagatesContext() = runTest {
        val fake = FakeProvider()
        val gen = WhatsAppReplyGenerator()
        val res = gen.generateDraft(evt, ReplyTone.Friendly, listOf(c), events, fake)

        assertTrue(res.success)
        assertEquals("Sure, here is a draft.", res.draft)
        assertNull(res.error)
        assertEquals(1, fake.calls)
        assertNotNull(fake.lastUser)
        assertTrue(res.context.hasContext)
        assertEquals("Alice", res.context.matchedContact?.displayName)
    }

    @Test
    fun unconfiguredProviderReturnsErrorAndNeverCallsChat() = runTest {
        val fake = FakeProvider(isConfigured = false)
        val gen = WhatsAppReplyGenerator()
        val res = gen.generateDraft(evt, ReplyTone.Friendly, listOf(c), events, fake)

        assertFalse(res.success)
        assertNotNull(res.error)
        assertNull(res.draft)
        assertEquals(0, fake.calls)
        // Context still resolved even though generation is skipped.
        assertEquals("Alice", res.context.matchedContact?.displayName)
    }

    @Test
    fun providerFailureIsReportedAsErrorWithoutSending() = runTest {
        val fake = FakeProvider(result = Result.failure(Exception("boom")))
        val gen = WhatsAppReplyGenerator()
        val res = gen.generateDraft(evt, ReplyTone.Friendly, listOf(c), events, fake)

        assertFalse(res.success)
        assertNotNull(res.error)
        assertNull(res.draft)
        assertEquals(1, fake.calls)
        // No sending side effect exists in the generator.
    }

    @Test
    fun unmatchedSenderStillProducesContextFreeDraftWhenConfigured() = runTest {
        val fake = FakeProvider()
        val gen = WhatsAppReplyGenerator()
        val stranger = WhatsAppMessageEvent("Stranger", "Hey", now, false, "com.whatsapp")
        val res = gen.generateDraft(stranger, ReplyTone.Friendly, listOf(c), emptyMap(), fake)

        assertTrue(res.success)
        assertTrue(res.context.contextFree)
        assertNull(res.context.matchedContact)
    }

    @Test
    fun emptyHistoryYieldsContextButNoContextMessages() = runTest {
        val fake = FakeProvider()
        val gen = WhatsAppReplyGenerator()
        val res = gen.generateDraft(evt, ReplyTone.Friendly, listOf(c), mapOf<Long, List<WhatsAppMessageEntity>>(1L to emptyList()), fake)

        assertTrue(res.success)
        assertTrue(res.context.contextMessages.isEmpty())
    }

    private fun runTest(block: suspend () -> Unit) = runBlocking { block() }
}
