package com.techsagehub.mannai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.techsagehub.mannai.assistant.WakePhrase
import com.techsagehub.mannai.data.SettingsStore
import com.techsagehub.mannai.data.WhatsAppImportRepository
import com.techsagehub.mannai.service.AssistantService
import com.techsagehub.mannai.service.AssistantState
import com.techsagehub.mannai.service.WhatsAppNotificationListener
import com.techsagehub.mannai.voice.SpeechInput
import com.techsagehub.mannai.voice.TtsSpeaker
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    whatsAppImport: WhatsAppImportRepository,
    serviceState: AssistantState,
    serviceRunning: Boolean,
    speechAvailable: Boolean,
    ttsReady: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    val name by settings.assistantName.collectAsState()
    val wakeEnabled by settings.wakeEnabled.collectAsState()
    val speak by settings.speakResponses.collectAsState()
    val aiEndpoint by settings.aiEndpoint.collectAsState()
    val aiModel by settings.aiModel.collectAsState()
    val aiProvider by settings.aiProvider.collectAsState()
    val aiConfigured = settings.isAiConfigured
    val allowedSenders by settings.allowedSenders.collectAsState()

    var nameDraft by remember(name) { mutableStateOf(name) }
    var providerDraft by remember(aiProvider) { mutableStateOf(aiProvider) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notifEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    val refreshPermissions = {
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }
    val notificationListenerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // WhatsApp allowlist input draft.
    var newSender by remember { mutableStateOf("") }

    // Wake-phrase self test.
    var testHeard by remember { mutableStateOf<String?>(null) }
    var testMatch by remember { mutableStateOf<Boolean?>(null) }
    var testListening by remember { mutableStateOf(false) }
    val tester = remember {
        SpeechInput(context) { heard, isFinal ->
            if (isFinal) {
                testHeard = heard
                testMatch = WakePhrase.matches(heard, settings.assistantNameValue)
                testListening = false
            }
        }
    }
    DisposableEffect(Unit) { onDispose { tester.destroy() } }

    // AI config drafts.
    var endpointDraft by remember(aiEndpoint) { mutableStateOf(aiEndpoint) }
    var keyDraft by remember { mutableStateOf("") }
    var modelDraft by remember(aiModel) { mutableStateOf(aiModel) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("Settings", "Your assistant, your rules")

        // ---- assistant name ----
        SettingsCard("Assistant name", "Wake phrase becomes \"Hey $name\"") {
            Field(value = nameDraft, onChange = { nameDraft = it }, label = "Name (short, easy to say)")
            Button(
                onClick = { settings.assistantNameValue = nameDraft },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
            ) { Text("Save name", fontSize = 12.sp) }
            Text("Current wake phrase: \"${WakePhrase.phraseFor(name)}\"",
                color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (testListening) {
                Text("🎙 Say \"${WakePhrase.phraseFor(name)}\" now…", color = Accent, fontSize = 13.sp)
            }
            testHeard?.let { heard ->
                Text("Heard: \"$heard\" — " + if (testMatch == true) "✓ detected" else "✗ not detected",
                    color = if (testMatch == true) Green else Red, fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                testHeard = null
                testMatch = null
                if (!micGranted) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@OutlinedButton
                }
                testListening = true
                tester.start()
            }) { Text("Test wake phrase", color = Accent, fontSize = 12.sp) }
        }

        // ---- listening ----
        SettingsCard("Background listening", "State: ${serviceState.label}") {
            ToggleRow("Wake-word listening", wakeEnabled) {
                settings.wakeEnabledValue = it
                if (it) onStartListening() else onStopListening()
            }
            if (serviceRunning) {
                Button(
                    onClick = onStopListening,
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Background)
                ) { Text("Stop assistant now", fontSize = 12.sp) }
            } else if (wakeEnabled) {
                Button(
                    onClick = onStartListening,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                ) { Text("Start listening", fontSize = 12.sp) }
            }
            Text(
                "Honest limits: detection uses on-device speech recognition while the listener " +
                    "runs. It pauses if the mic is busy, battery saver kills the service, or you " +
                    "force-stop the app. After reboot, reopen MannAI once to resume — Android does " +
                    "not allow auto-starting the microphone.",
                color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        // ---- voice ----
        SettingsCard("Voice", null) {
            ToggleRow("Spoken responses", speak) { settings.speakResponsesValue = it }
            StatusLine("Speech recognition", speechAvailable,
                if (speechAvailable) "Available" else "Not available on this device")
            StatusLine("Speech output (TTS)", ttsReady, if (ttsReady) "Ready" else "Starting…")
        }

        // ---- permissions ----
        SettingsCard("Permissions", "Requested only when a feature needs them") {
            StatusLine("Microphone", micGranted, if (micGranted) "Granted" else "Not granted")
            if (!micGranted) {
                OutlinedButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant microphone", color = Accent, fontSize = 12.sp)
                }
            }
            StatusLine("Notifications", notifEnabled, if (notifEnabled) "Enabled" else "Disabled")
            if (!notifEnabled && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Enable notifications", color = Accent, fontSize = 12.sp)
                }
            }
            OutlinedButton(onClick = onOpenBatterySettings) {
                Text("Battery & autostart settings", color = Accent, fontSize = 12.sp)
            }
            Text(
                "On Infinix/XOS: allow MannAI in Phone Master → Battery saving, and enable " +
                    "Auto-start, or reminders and listening may be delayed.",
                color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        // ---- online AI (optional) ----
        SettingsCard("Online AI (optional)", if (aiConfigured) "Configured • offline still works" else "Not configured • offline still works") {
            Text(
                "MannAI works fully offline. Optionally add your own OpenAI-compatible endpoint " +
                    "for open-ended questions and AI reply refinement. The key stays on this phone.",
                color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI provider", color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { providerDraft = SettingsStore.PROVIDER_GEMINI },
                        enabled = providerDraft != SettingsStore.PROVIDER_GEMINI,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (providerDraft == SettingsStore.PROVIDER_GEMINI) Accent else PanelLight,
                            contentColor = if (providerDraft == SettingsStore.PROVIDER_GEMINI) Background else PrimaryText
                        )
                    ) { Text("Gemini", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { providerDraft = SettingsStore.PROVIDER_OPENAI_COMPATIBLE },
                        enabled = providerDraft != SettingsStore.PROVIDER_OPENAI_COMPATIBLE,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (providerDraft == SettingsStore.PROVIDER_OPENAI_COMPATIBLE) Accent else PanelLight,
                            contentColor = if (providerDraft == SettingsStore.PROVIDER_OPENAI_COMPATIBLE) Background else PrimaryText
                        )
                    ) { Text("OpenAI-compatible", fontSize = 11.sp) }
                }
            }
            if (providerDraft == SettingsStore.PROVIDER_GEMINI) {
                Text(
                    "Gemini uses a fixed Google endpoint; only the API key and model are required. " +
                        "Key stays on this phone.",
                    color = MutedText, fontSize = 10.sp, lineHeight = 15.sp
                )
            }
            Field(value = endpointDraft, onChange = { endpointDraft = it }, label = "Endpoint (https://…/v1)")
            Field(value = keyDraft, onChange = { keyDraft = it }, label = "API key (stored on device only)")
            Field(value = modelDraft, onChange = { modelDraft = it }, label = "Model")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        settings.saveAiConfig(endpointDraft, keyDraft.ifBlank { settings.aiKey.value }, modelDraft, providerDraft)
                        providerDraft = settings.aiProvider.value
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                ) { Text("Save", fontSize = 12.sp) }
                if (aiConfigured) {
                    OutlinedButton(onClick = {
                        settings.clearAiConfig()
                        endpointDraft = ""
                        keyDraft = ""
                        providerDraft = settings.aiProvider.value
                    }) { Text("Remove", color = Red, fontSize = 12.sp) }
                }
            }
        }

        // ---- WhatsApp notifications ----
        SettingsCard("WhatsApp notifications", "Notification access and selected-sender allowlist") {
            val listenerPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            val hasAccess = listenerPackages.contains(context.packageName)
            val accessLabel = if (hasAccess) "Granted" else "Not granted"

            StatusLine("Notification access", hasAccess, accessLabel)
            if (!hasAccess) {
                Text(
                    "Grant MannAI notification access in system settings so it can read WhatsApp " +
                        "notifications. This must be enabled manually — it cannot be requested from the app.",
                    color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        notificationListenerLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                ) { Text("Open notification access settings", fontSize = 12.sp) }
            }

            Divider()

            Text(
                "Selected-sender allowlist",
                color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "Only notifications from senders in this list are processed. " +
                    "Senders are matched case-insensitively. Unlisted senders are ignored.",
                color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSender,
                    onValueChange = { newSender = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Sender name or group") },
                    singleLine = true,
                    maxLines = 1
                )
                Button(
                    onClick = {
                        if (newSender.isNotBlank()) {
                            settings.addAllowedSender(newSender)
                            newSender = ""
                        }
                    },
                    enabled = newSender.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                ) { Text("Add", fontSize = 12.sp) }
            }
            if (allowedSenders.isNotEmpty()) {
                Text("Allowed senders:", color = MutedText, fontSize = 11.sp)
                Column(modifier = Modifier.fillMaxWidth()) {
                    allowedSenders.sorted().forEach { sender ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sender, fontSize = 11.sp, color = MutedText)
                            OutlinedButton(
                                onClick = { settings.removeAllowedSender(sender) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PanelLight,
                                    contentColor = MutedText
                                )
                            ) { Text("Remove", fontSize = 10.sp) }
                        }
                    }
                }
            } else {
                Text("No senders allowed yet. Add a sender above.", color = MutedText, fontSize = 11.sp)
            }
            Text(
                "Notifications from unlisted or unidentified senders are ignored. " +
                    "No messages are sent automatically — every reply needs your approval.",
                color = MutedText, fontSize = 10.sp, lineHeight = 15.sp
            )
        }

        // ---- WhatsApp chat import ----
        SettingsCard("WhatsApp chat import", "Import exported .txt conversations") {
            WhatsAppImportScreen(
                repository = whatsAppImport,
                onImportComplete = { }
            )
        }

        // ---- privacy ----
        SettingsCard("Privacy & data", null) {
            Text(
                "• Tasks, notes, reminders and settings stay on this phone.\n" +
                    "• Raw microphone audio is never stored.\n" +
                    "• Speech recognition may use the network (Google service) unless on-device " +
                    "recognition handles it.\n" +
                    "• Online AI is only contacted when you configure it, and only for the text you send.\n" +
                    "• WhatsApp notification contents are not persisted to disk.",
                color = MutedText, fontSize = 11.sp, lineHeight = 17.sp
            )
            OutlinedButton(onClick = onClearHistory) {
                Text("Clear conversation history", color = Red, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = MutedText, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = PrimaryText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent)
        )
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusDot(active = ok)
        Text(label, color = PrimaryText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(detail, color = MutedText, fontSize = 12.sp)
    }
}
