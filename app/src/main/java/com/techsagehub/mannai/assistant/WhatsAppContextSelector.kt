package com.techsagehub.mannai.assistant

import com.techsagehub.mannai.data.db.WhatsAppContactEntity
import com.techsagehub.mannai.data.db.WhatsAppMessageEntity

/**
 * The result of selecting local conversation context for a WhatsApp reply draft.
 *
 * The selector never guesses the user's identity and never uses another contact's
 * history. When matching is ambiguous it reports ambiguity instead of silently
 * picking the wrong person.
 */
data class WhatsAppReplyContext(
    /** The matched imported contact, or null when no reliable match exists. */
    val matchedContact: WhatsAppContactEntity?,
    /** True when more than one contact could match the notification sender. */
    val isAmbiguous: Boolean,
    /** True when the notification identifies a group conversation. */
    val isGroup: Boolean,
    /**
     * True when context should not be used (no match, ambiguous match, or group).
     * A context-free draft may still be generated from the incoming message alone.
     */
    val contextFree: Boolean,
    /** Ordered context messages (oldest first) drawn from the matched contact's history. */
    val contextMessages: List<ContextMessage>,
    /** True when the incoming message was already present in imported history and was not duplicated. */
    val incomingDeduplicated: Boolean,
    /** Human-readable note surfaced in the UI about what context was used. */
    val matchNote: String
) {
    /** Whether any usable conversation context was found. */
    val hasContext: Boolean get() = contextMessages.isNotEmpty() && !contextFree
}

/** A single context message with explicit, never-inferred attribution. */
data class ContextMessage(
    /** "contact" when sent by the matched contact, "user" when sent by the imported user. */
    val role: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long
)

/**
 * Selects relevant local conversation history for a WhatsApp notification event.
 *
 * Pure and testable: receives already-loaded data rather than touching Room,
 * so it can be unit-tested without a database.
 */
class WhatsAppContextSelector(
    /** Maximum number of recent context messages to include. Default 20. */
    private val maxRecentMessages: Int = 20
) {

    fun select(
        event: WhatsAppMessageEvent,
        contacts: List<WhatsAppContactEntity>,
        messagesByContact: Map<Long, List<WhatsAppMessageEntity>>
    ): WhatsAppReplyContext {

        // Group messages are handled conservatively: never match to an individual
        // contact's personal history for a group reply.
        if (event.isGroup) {
            return WhatsAppReplyContext(
                matchedContact = null,
                isAmbiguous = false,
                isGroup = true,
                contextFree = true,
                contextMessages = emptyList(),
                incomingDeduplicated = false,
                matchNote = "Group conversation — drafting from this message only."
            )
        }

        val normalizedSender = WhatsAppNotificationParser.normalizeSender(event.sender)

        val matches = contacts.filter { it.normalizedName == normalizedSender }

        val context = when {
            matches.isEmpty() -> {
                // No imported contact matches this sender. Context-free draft is allowed.
                WhatsAppReplyContext(
                    matchedContact = null,
                    isAmbiguous = false,
                    isGroup = false,
                    contextFree = true,
                    contextMessages = emptyList(),
                    incomingDeduplicated = false,
                    matchNote = "No imported conversation found for \"${event.sender}\" — drafting from this message only."
                )
            }
            matches.size == 1 -> buildContextForContact(matches[0], event, messagesByContact)
            else -> {
                // Ambiguous: do not silently use another person's history.
                WhatsAppReplyContext(
                    matchedContact = null,
                    isAmbiguous = true,
                    isGroup = false,
                    contextFree = true,
                    contextMessages = emptyList(),
                    incomingDeduplicated = false,
                    matchNote = "Multiple imported contacts match \"${event.sender}\". Please map the contact, or draft from this message only."
                )
            }
        }

        return context
    }

    private fun buildContextForContact(
        contact: WhatsAppContactEntity,
        event: WhatsAppMessageEvent,
        messagesByContact: Map<Long, List<WhatsAppMessageEntity>>
    ): WhatsAppReplyContext {
        val allMessages = messagesByContact[contact.id] ?: emptyList()

        // Only history at or before the incoming message is relevant context.
        val prior = allMessages.filter { it.timestampMs <= event.timestampMs }

        // Recent window, oldest first.
        val recent = prior.takeLast(maxRecentMessages)

        val incomingLower = event.message.trim().lowercase()
        var incomingDeduplicated = false

        val contextMessages = recent.mapNotNull { msg ->
            val role = if (msg.isUser) "user" else "contact"
            val sender = if (msg.isUser) "You" else msg.senderName
            // Deduplicate an incoming contact message already present in history.
            if (!msg.isUser && msg.message.trim().lowercase() == incomingLower) {
                incomingDeduplicated = true
                null // don't repeat the incoming message as context
            } else {
                ContextMessage(role, sender, msg.message, msg.timestampMs)
            }
        }

        val note = if (contextMessages.isEmpty()) {
            "No prior imported messages for \"${contact.displayName}\" — drafting from this message only."
        } else {
            "Using ${contextMessages.size} imported message(s) from \"${contact.displayName}\" as context."
        }

        return WhatsAppReplyContext(
            matchedContact = contact,
            isAmbiguous = false,
            isGroup = false,
            contextFree = false,
            contextMessages = contextMessages,
            incomingDeduplicated = incomingDeduplicated,
            matchNote = note
        )
    }

    companion object {
        val DEFAULT = WhatsAppContextSelector()
    }
}
