package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.ReplyTone
import com.techsagehub.mannai.assistant.WhatsAppContextSelector
import com.techsagehub.mannai.assistant.WhatsAppMessageEvent
import com.techsagehub.mannai.assistant.WhatsAppReplyContext
import com.techsagehub.mannai.data.db.WhatsAppContactEntity
import com.techsagehub.mannai.data.db.WhatsAppMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppContextSelectorTest {

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

    @Test
    fun exactContactMatchReturnsPriorHistoryOldestFirst() {
        val c = contact(1, "Alice")
        val events = mapOf(
            1L to listOf(
                message(1, "Alice", "Hi", now - 20000, isUser = false),
                message(1, "You", "Hello", now - 10000, isUser = true),
                message(1, "Alice", "How are you?", now - 5000, isUser = false)
            )
        )
        val evt = WhatsAppMessageEvent("Alice", "What's up?", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertFalse("should not be context-free when contact matched", ctx.contextFree)
        assertEquals("Alice", ctx.matchedContact?.displayName)
        assertFalse(ctx.isAmbiguous)
        assertEquals(3, ctx.contextMessages.size)
        // oldest first
        assertEquals("Alice", ctx.contextMessages[0].senderName)
        assertEquals("Hi", ctx.contextMessages[0].text)
        assertEquals("You", ctx.contextMessages[1].senderName)
        assertEquals("Alice", ctx.contextMessages[2].senderName)
        assertTrue(ctx.hasContext)
    }

    @Test
    fun unmatchedSenderIsContextFreeAndDoesNotGuess() {
        val c = contact(1, "Alice")
        val events = mapOf<Long, List<WhatsAppMessageEntity>>(1L to emptyList())
        val evt = WhatsAppMessageEvent("Stranger", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertTrue(ctx.contextFree)
        assertNull(ctx.matchedContact)
        assertFalse(ctx.isAmbiguous)
        assertTrue(ctx.contextMessages.isEmpty())
        assertTrue(ctx.matchNote.contains("No imported conversation"))
    }

    @Test
    fun ambiguousMatchDoesNotUseAnyonesHistory() {
        val a = contact(1, "Alice")
        val b = contact(2, "ALICE") // same normalized name as "alice"
        val events = mapOf<Long, List<WhatsAppMessageEntity>>(
            1L to listOf(message(1, "Alice", "secret history", now - 1000, isUser = false)),
            2L to listOf(message(2, "ALICE", "other secret", now - 1000, isUser = false))
        )
        val evt = WhatsAppMessageEvent("alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(a, b), events)

        assertTrue(ctx.contextFree)
        assertTrue(ctx.isAmbiguous)
        assertNull(ctx.matchedContact)
        assertTrue(ctx.contextMessages.isEmpty())
        assertTrue(ctx.matchNote.contains("Multiple imported contacts"))
    }

    @Test
    fun groupMessageIsContextFreeConservatively() {
        val c = contact(1, "Family")
        val events = mapOf<Long, List<WhatsAppMessageEntity>>(1L to listOf(message(1, "Family", "dinner?", now - 1000, isUser = false)))
        val evt = WhatsAppMessageEvent("Family Group", "Dinner at 7?", now, true, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertTrue(ctx.contextFree)
        assertTrue(ctx.isGroup)
        assertNull(ctx.matchedContact)
        assertTrue(ctx.contextMessages.isEmpty())
    }

    @Test
    fun contextIsLimitedToMaxRecentMessages() {
        val c = contact(1, "Alice")
        val msgs = (1..5).mapTo(mutableListOf()) { i ->
            message(1, "Alice", "msg $i", now - (5 - i) * 1000, isUser = false)
        }
        val events = mapOf(1L to msgs)
        val evt = WhatsAppMessageEvent("Alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector(maxRecentMessages = 2).select(evt, listOf(c), events)

        assertEquals(2, ctx.contextMessages.size)
        assertEquals("msg 4", ctx.contextMessages[0].text)
        assertEquals("msg 5", ctx.contextMessages[1].text)
    }

    @Test
    fun messagesAfterIncomingMessageAreExcluded() {
        val c = contact(1, "Alice")
        val events = mapOf(
            1L to listOf(
                message(1, "Alice", "old", now - 10000, isUser = false),
                message(1, "Alice", "future", now + 10000, isUser = false)
            )
        )
        val evt = WhatsAppMessageEvent("Alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertEquals(1, ctx.contextMessages.size)
        assertEquals("old", ctx.contextMessages[0].text)
    }

    @Test
    fun incomingMessageAlreadyInHistoryIsDeduplicated() {
        val c = contact(1, "Alice")
        val events = mapOf(
            1L to listOf(
                message(1, "Alice", "Hey", now - 1000, isUser = false),
                message(1, "Alice", "older", now - 20000, isUser = false)
            )
        )
        val evt = WhatsAppMessageEvent("Alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertTrue(ctx.incomingDeduplicated)
        assertEquals(1, ctx.contextMessages.size)
        assertEquals("older", ctx.contextMessages[0].text)
    }

    @Test
    fun explicitUserAttributionIsNeverInferred() {
        val c = contact(1, "Alice")
        // Both a contact-sent message and a user-sent message exist; attribution is explicit via isUser.
        val events = mapOf(
            1L to listOf(
                message(1, "Alice", "contact message", now - 5000, isUser = false),
                message(1, "Alice", "my message", now - 2000, isUser = true)
            )
        )
        val evt = WhatsAppMessageEvent("Alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        val userMsgs = ctx.contextMessages.filter { it.role == "user" }
        val contactMsgs = ctx.contextMessages.filter { it.role == "contact" }
        assertEquals(1, userMsgs.size)
        assertEquals("my message", userMsgs[0].text)
        assertEquals(1, contactMsgs.size)
        assertEquals("contact message", contactMsgs[0].text)
    }

    @Test
    fun emptyHistoryForMatchedContactStillAllowsContextFreeDraft() {
        val c = contact(1, "Alice")
        val events = mapOf<Long, List<WhatsAppMessageEntity>>(1L to emptyList())
        val evt = WhatsAppMessageEvent("Alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        // Contact matched, so not ambiguous, but no context messages.
        assertFalse(ctx.isAmbiguous)
        assertEquals("Alice", ctx.matchedContact?.displayName)
        assertTrue(ctx.contextMessages.isEmpty())
        assertFalse(ctx.hasContext)
        assertTrue(ctx.matchNote.contains("No prior imported messages"))
    }

    @Test
    fun caseInsensitiveSenderMatching() {
        val c = contact(1, "Alice")
        val events = mapOf<Long, List<WhatsAppMessageEntity>>(1L to listOf(message(1, "Alice", "hi", now - 1000, isUser = false)))
        val evt = WhatsAppMessageEvent("  ALICE  ", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertFalse(ctx.contextFree)
        assertEquals("Alice", ctx.matchedContact?.displayName)
    }

    @Test
    fun matchNoteDescribesContextUsed() {
        val c = contact(1, "Alice")
        val events = mapOf<Long, List<WhatsAppMessageEntity>>(1L to listOf(message(1, "Alice", "hi", now - 1000, isUser = false)))
        val evt = WhatsAppMessageEvent("Alice", "Hey", now, false, "com.whatsapp")

        val ctx = WhatsAppContextSelector().select(evt, listOf(c), events)

        assertNotNull(ctx.matchNote)
        assertTrue(ctx.matchNote.contains("Alice"))
        assertTrue(ctx.matchNote.contains("message(s)"))
    }
}
