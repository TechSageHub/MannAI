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

    /** Last event published by this listener instance (observed by the UI when foreground). */
    private val _lastEvent = MutableStateFlow<WhatsAppMessageEvent?>(null)
    val lastEvent: StateFlow<WhatsAppMessageEvent?> = _lastEvent.asStateFlow()

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
            _lastEvent.value = event
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
