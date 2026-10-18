package com.techsagehub.mannai.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.techsagehub.mannai.assistant.WhatsAppMessageEvent
import com.techsagehub.mannai.assistant.WhatsAppNotificationParser
import com.techsagehub.mannai.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for WhatsApp notifications, filters to the official WhatsApp package,
 * applies the selected-sender allowlist, and publishes qualifying events.
 *
 * No foreground service is started. No AI replies are generated. No messages
 * are sent automatically. Notification contents are not persisted.
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    private val settings: SettingsStore by lazy { SettingsStore(applicationContext) }

    companion object {
        /**
         * Shared last allowlisted WhatsApp event. All listener instances write here,
         * so the UI can observe incoming messages without a service-instance reference.
         */
        private val _lastEventShared = MutableStateFlow<WhatsAppMessageEvent?>(null)
        val lastEventShared: StateFlow<WhatsAppMessageEvent?> = _lastEventShared.asStateFlow()

        /** Publishes an allowlisted event from any listener instance. */
        fun publish(event: WhatsAppMessageEvent) {
            _lastEventShared.value = event
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val event = parseAndCheck(sbn)
        if (event != null) {
            publish(event)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }

    private fun parseAndCheck(sbn: StatusBarNotification): WhatsAppMessageEvent? {
        val extras = sbn.notification.extras

        val title = extras?.getCharSequence("android.title")
        val text = extras?.getCharSequence("android.text")
        val bigText = extras?.getCharSequence("android.bigText")
        val isGroup = false
        val timestampMs = System.currentTimeMillis()

        return WhatsAppNotificationParser.parse(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText,
            isGroup = isGroup,
            timestampMs = timestampMs
        )?.takeIf { settings.isSenderAllowed(it.sender) }
    }
}
