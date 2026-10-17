package com.techsagehub.mannai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techsagehub.mannai.service.AssistantService
import com.techsagehub.mannai.ui.Accent
import com.techsagehub.mannai.ui.Background
import com.techsagehub.mannai.ui.ChatScreen
import com.techsagehub.mannai.ui.ChatViewModel
import com.techsagehub.mannai.ui.DashboardScreen
import com.techsagehub.mannai.ui.MutedText
import com.techsagehub.mannai.ui.NotesScreen
import com.techsagehub.mannai.ui.NotesViewModel
import com.techsagehub.mannai.ui.Panel
import com.techsagehub.mannai.ui.RemindersScreen
import com.techsagehub.mannai.ui.RemindersViewModel
import com.techsagehub.mannai.ui.ReplyScreen
import com.techsagehub.mannai.ui.ReplyViewModel
import com.techsagehub.mannai.ui.SettingsScreen
import com.techsagehub.mannai.ui.TasksScreen
import com.techsagehub.mannai.ui.TasksViewModel
import com.techsagehub.mannai.ui.UiEvent
import com.techsagehub.mannai.ui.VmFactory
import com.techsagehub.mannai.voice.TtsSpeaker
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private lateinit var tts: TtsSpeaker
    private var ttsReady by mutableStateOf(false)
    private var currentScreen by mutableStateOf(SCREEN_HOME)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Screens re-check statuses on resume; nothing forced here.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TtsSpeaker(this)
        currentScreen = intent?.getStringExtra(EXTRA_SCREEN) ?: SCREEN_HOME

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MannAIApp_Content()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SCREEN)?.let { currentScreen = it }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    // ---------- assistant controls ----------

    fun startListening() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) missing.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) missing.add(Manifest.permission.POST_NOTIFICATIONS)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        // Start only if mic is already granted; otherwise the user grants first,
        // then taps Start again (explicit activation, as Android requires).
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            AssistantService.start(this)
        }
    }

    fun stopListening() = AssistantService.stop(this)

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    fun speak(text: String) {
        val app = application as MannAIApp
        tts.speak(text, app.settings.speakResponsesValue)
    }

    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share reply via…"))
    }

    fun openBatterySettings() {
        // App info page: no special permission needed, works on Infinix/XOS.
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { startActivity(intent) }
    }

    // ---------- UI ----------

    @Composable
    private fun MannAIApp_Content() {
        val app = application as MannAIApp
        val factory = remember { VmFactory(app) }
        val tasksVm: TasksViewModel = viewModel(factory = factory)
        val notesVm: NotesViewModel = viewModel(factory = factory)
        val remindersVm: RemindersViewModel = viewModel(factory = factory)
        val chatVm: ChatViewModel = viewModel(factory = factory)
        val replyVm: ReplyViewModel = viewModel(factory = factory)

        val snackbar = remember { SnackbarHostState() }
        val serviceState by AssistantService.currentState.collectAsState()
        val serviceRunning by AssistantService.isRunning.collectAsState()
        val assistantName by app.settings.assistantName.collectAsState()
        val context = LocalContext.current

        HandleEvents(chatVm, snackbar)
        HandleEvents(tasksVm, snackbar)
        HandleEvents(notesVm, snackbar)
        HandleEvents(remindersVm, snackbar)
        HandleEvents(replyVm, snackbar)

        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Scaffold(
                containerColor = Background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    MannAINavBar(current = currentScreen, onPick = { currentScreen = it })
                }
            ) { inner ->
                Column(Modifier.padding(inner)) {
                    when (currentScreen) {
                        SCREEN_HOME -> DashboardScreen(
                            assistantName = assistantName,
                            tasksVm = tasksVm, notesVm = notesVm, remindersVm = remindersVm,
                            serviceState = serviceState, serviceRunning = serviceRunning,
                            onOpen = { currentScreen = it },
                            onQuickChat = { currentScreen = SCREEN_CHAT }
                        )
                        SCREEN_TASKS -> TasksScreen(tasksVm)
                        SCREEN_NOTES -> NotesScreen(notesVm)
                        SCREEN_REMINDERS -> RemindersScreen(remindersVm,
                            onGrantNotifications = { ensureNotificationPermission() })
                        SCREEN_CHAT -> ChatScreen(chatVm, assistantName, onSpeak = { speak(it) })
                        SCREEN_REPLY -> ReplyScreen(replyVm, onShare = { shareText(it) })
                        SCREEN_SETTINGS -> SettingsScreen(
                            settings = app.settings,
                            whatsAppImport = app.whatsAppImport,
                            serviceState = serviceState,
                            serviceRunning = serviceRunning,
                            speechAvailable = android.speech.SpeechRecognizer.isRecognitionAvailable(context),
                            ttsReady = ttsReady,
                            onStartListening = { startListening() },
                            onStopListening = { stopListening() },
                            onClearHistory = { chatVm.clearHistory() },
                            onOpenBatterySettings = { openBatterySettings() }
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            tts.ready.collect { ttsReady = it }
        }
    }

    @Composable
    private fun HandleEvents(vm: com.techsagehub.mannai.ui.BaseViewModel, snackbar: SnackbarHostState) {
        val event by vm.events.collectAsState()
        LaunchedEffect(event) {
            when (val e = event) {
                is UiEvent.Message -> snackbar.showSnackbar(e.text)
                is UiEvent.OpenScreen -> currentScreen = e.screen
                is UiEvent.RequestStartListening -> startListening()
                is UiEvent.RequestStopListening -> stopListening()
                is UiEvent.RequestMic, is UiEvent.RequestNotifications -> ensureNotificationPermission()
                null -> {}
            }
            if (event != null) vm.consumeEvent()
        }
    }

    companion object {
        const val EXTRA_SCREEN = "mannai_screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_TASKS = "tasks"
        const val SCREEN_NOTES = "notes"
        const val SCREEN_REMINDERS = "reminders"
        const val SCREEN_CHAT = "chat"
        const val SCREEN_REPLY = "reply"
        const val SCREEN_SETTINGS = "settings"
    }
}

private data class NavDest(val id: String, val label: String, val glyph: String)

@Composable
private fun MannAINavBar(current: String, onPick: (String) -> Unit) {
    val dests = listOf(
        NavDest(MainActivity.SCREEN_HOME, "Home", "⌂"),
        NavDest(MainActivity.SCREEN_TASKS, "Tasks", "✓"),
        NavDest(MainActivity.SCREEN_CHAT, "Chat", "💬"),
        NavDest(MainActivity.SCREEN_REPLY, "Reply", "✉"),
        NavDest(MainActivity.SCREEN_SETTINGS, "Settings", "⚙")
    )
    NavigationBar(containerColor = Panel, tonalElevation = 8.dp) {
        dests.forEach { d ->
            NavigationBarItem(
                selected = current == d.id ||
                    (d.id == MainActivity.SCREEN_HOME && current !in dests.map { it.id }),
                onClick = { onPick(d.id) },
                icon = { Text(d.glyph, fontSize = 20.sp) },
                label = { Text(d.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Background,
                    selectedTextColor = Accent,
                    indicatorColor = Accent,
                    unselectedIconColor = MutedText,
                    unselectedTextColor = MutedText
                )
            )
        }
    }
}
