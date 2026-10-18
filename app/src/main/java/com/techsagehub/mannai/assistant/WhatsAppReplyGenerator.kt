package com.techsagehub.mannai.assistant

import com.techsagehub.mannai.data.db.WhatsAppContactEntity
import com.techsagehub.mannai.data.db.WhatsAppMessageEntity

/**
 * Result of generating a WhatsApp reply draft.
 */
data class WhatsAppDraftResult(
    val draft: String?,
    val error: String?,
    val context: WhatsAppReplyContext
) {
    val success: Boolean get() = draft?.isNotBlank() == true && error == null
}

/**
 * Orchestrates context selection, prompt construction, and AI generation for a
 * WhatsApp reply draft.
 *
 * No message is sent here. This only produces an editable draft for the user to
 * review. The provider is never switched silently on failure.
 */
class WhatsAppReplyGenerator(
    private val selector: WhatsAppContextSelector = WhatsAppContextSelector.DEFAULT,
    private val promptBuilder: ReplyPromptBuilder = ReplyPromptBuilder
) {

    suspend fun generateDraft(
        event: WhatsAppMessageEvent,
        tone: ReplyTone,
        contacts: List<WhatsAppContactEntity>,
        messagesByContact: Map<Long, List<WhatsAppMessageEntity>>,
        provider: AiProvider
    ): WhatsAppDraftResult {

        // 1. Select local context (privacy boundary: only matched contact's history).
        val context = selector.select(event, contacts, messagesByContact)

        // 2. Build the prompt (kept separate from UI and network).
        val prompt = promptBuilder.build(context, event, tone)

        // 3. Never auto-switch providers; report configuration as an error.
        if (!provider.isConfigured) {
            return WhatsAppDraftResult(
                draft = null,
                error = "Online AI is not configured. Add a Gemini API key in Settings, or use the offline draft.",
                context = context
            )
        }

        // 4. Generate. Errors are reported as-is; no silent provider fallback.
        return provider.chat(prompt.system, prompt.user).fold(
            onSuccess = { draft ->
                WhatsAppDraftResult(draft = draft, error = null, context = context)
            },
            onFailure = { e ->
                WhatsAppDraftResult(
                    draft = null,
                    error = "Draft generation failed: ${e.message ?: "unknown error"}",
                    context = context
                )
            }
        )
    }
}
