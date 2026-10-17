package com.techsagehub.mannai.assistant

/**
 * Pure, testable parser for WhatsApp notification content.
 *
 * Extracts sender and message from notification extras defensively. Returns
 * null when the sender or message cannot be identified reliably (fail closed).
 * No private message contents are logged.
 */
object WhatsAppNotificationParser {
    /** Official WhatsApp package. Configurable here for future compatibility. */
    const val WHATSAPP_PACKAGE = "com.whatsapp"

    data class Parsed(
        val sender: String,
        val message: String,
        val isGroup: Boolean
    )

    /**
     * Parses a WhatsApp notification from its exposed extras.
     *
     * @param packageName the posting package (must equal WHATSAPP_PACKAGE)
     * @param title notification title (usually sender or group name)
     * @param text notification text line
     * @param bigText expanded notification text (preferred over [text])
     * @param isGroup whether [sbn.isGroupConversation] was true
     * @param timestampMs when the notification was posted
     * @return a [Parsed] result, or null if the notification should be ignored
     */
    fun parse(
        packageName: String?,
        title: CharSequence?,
        text: CharSequence?,
        bigText: CharSequence?,
        isGroup: Boolean,
        timestampMs: Long
    ): WhatsAppMessageEvent? {
        if (packageName != WHATSAPP_PACKAGE) return null

        val sender = normalizeSender(title?.toString())
        if (sender.isBlank()) return null // fail closed: cannot identify sender

        val message = (bigText?.toString() ?: text?.toString() ?: "").trim()
        if (message.isBlank()) return null // fail closed: no message body

        return WhatsAppMessageEvent(
            sender = sender,
            message = message,
            timestampMs = timestampMs,
            isGroup = isGroup,
            packageName = packageName
        )
    }

    /** Normalizes a sender identifier for matching: trim + lowercase. */
    fun normalizeSender(sender: String?): String = sender?.trim()?.lowercase() ?: ""
}
