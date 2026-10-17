package com.techsagehub.mannai.assistant

/**
 * A single inbound WhatsApp notification event that has passed package and
 * allowlist checks. No AI generation or sending happens here — this is purely
 * the event boundary between the notification listener and the app.
 */
data class WhatsAppMessageEvent(
    /** Normalized sender identifier (contact name or group name). */
    val sender: String,
    /** Message body extracted from the notification. */
    val message: String,
    /** Epoch millis when the notification was posted. */
    val timestampMs: Long,
    /** True when the notification identifies a group conversation. */
    val isGroup: Boolean,
    /** Package that posted the notification (e.g. com.whatsapp). */
    val packageName: String
)
