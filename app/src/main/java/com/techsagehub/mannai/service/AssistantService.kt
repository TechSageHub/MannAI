package com.techsagehub.mannai.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.techsagehub.mannai.MainActivity
import com.techsagehub.mannai.R
import com.techsagehub.mannai.assistant.CommandExecutor
import com.techsagehub.mannai.assistant.CommandParser
import com.techsagehub.mannai.assistant.WakePhrase
import com.techsagehub.mannai.data.ReminderRepository
import com.techsagehub.mannai.data.NoteRepository
import com.techsagehub.mannai.data.SettingsStore
import com.techsagehub.mannai.data.TaskRepository
import com.techsagehub.mannai.data.db.MannAIDatabase
import com.techsagehub.mannai.notify.MannAIChannels
import com.techsagehub.mannai.voice.SpeechInput
import com.techsagehub.mannai.voice.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Listening states surfaced in the app and the persistent notification. */
enum class AssistantState(val label: String) {
    DISABLED("Listening disabled"),
    STANDBY("Waiting for wake phrase"),
    WAKE_DETECTED("Wake phrase detected"),
    LISTENING_COMMAND("Listening for command"),
    PROCESSING("Processing"),
    SPEAKING("Speaking"),
    UNAVAILABLE("Temporarily unavailable")
}

/**
 * Foreground microphone service. Waits for the configured wake phrase
 * ("Hey <name>"), recognizes one command, executes it offline, speaks the
 * result, then returns to standby. Must be started from the visible app
 * (Android forbids background mic starts); a persistent notification with a
 * Stop action is always shown while running.
 */
class AssistantService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settings: SettingsStore
    private lateinit var executor: CommandExecutor
    private var wakeInput: SpeechInput? = null
    private var commandInput: SpeechInput? = null
    private var tts: TtsSpeaker? = null

    @Volatile private var busy = false
    @Volatile private var lastActivation = 0L
    @Volatile private var errorStreak = 0

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        val db = MannAIDatabase.get(this)
        executor = CommandExecutor(
            this, TaskRepository(db.taskDao()), NoteRepository(db.noteDao()),
            ReminderRepository(db.reminderDao()), settings
        )
        tts = TtsSpeaker(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown("Stopped by user.")
                return START_NOT_STICKY
            }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        if (!hasMicPermission()) {
            setState(AssistantState.UNAVAILABLE)
            shutdown("Microphone permission missing.")
            return
        }
        try {
            promoteToForeground("Waiting for \"${settings.wakePhrase}\"")
        } catch (e: SecurityException) {
            // Android 14+: mic foreground services cannot start from background.
            setState(AssistantState.UNAVAILABLE)
            shutdown("System blocked background mic start. Reopen MannAI to resume.")
            return
        }
        isRunning.value = true
        beginWakeLoop()
    }

    private fun beginWakeLoop() {
        if (!settings.wakeEnabledValue) {
            setState(AssistantState.DISABLED)
            updateNotification("Listening paused — enable wake phrase in Settings")
            return
        }
        setState(AssistantState.STANDBY)
        updateNotification("Waiting for \"${settings.wakePhrase}\"")
        startWakeRecognition()
    }

    private fun startWakeRecognition() {
        if (!isRunning.value || busy) return
        wakeInput?.destroy()
        val input = SpeechInput(this) { heard, isFinal ->
            if (!isRunning.value || busy) return@SpeechInput
            if (WakePhrase.matches(heard, settings.assistantNameValue)) {
                val now = System.currentTimeMillis()
                if (now - lastActivation < ACTIVATION_COOLDOWN_MS) return@SpeechInput
                lastActivation = now
                onWakeDetected()
            } else if (isFinal && heard.isNotBlank()) {
                // Not the wake phrase — keep waiting (handles chatter without reset storms).
                restartWakeDelayed(300)
            }
        }
        wakeInput = input
        // Observe errors to restart the loop with backoff.
        scope.launch {
            input.error.collect { err ->
                if (err != null && !busy && isRunning.value) {
                    errorStreak++
                    if (errorStreak > MAX_ERROR_STREAK) {
                        setState(AssistantState.UNAVAILABLE)
                        updateNotification("Voice recognition unavailable — tap to reopen")
                    } else {
                        restartWakeDelayed(800L * errorStreak)
                    }
                }
            }
        }
        input.start(preferOffline = true, partialResults = true)
    }

    private fun restartWakeDelayed(ms: Long) {
        scope.launch {
            delay(ms)
            if (isRunning.value && !busy && currentState.value == AssistantState.STANDBY) {
                startWakeRecognition()
            }
        }
    }

    private fun onWakeDetected() {
        busy = true
        errorStreak = 0
        wakeInput?.stop()
        setState(AssistantState.WAKE_DETECTED)
        updateNotification("Yes? Listening for your command…")
        tts?.speak("Yes?", settings.speakResponsesValue)
        scope.launch {
            delay(700) // let the cue finish before capturing the command
            listenForCommand()
        }
    }

    private fun listenForCommand() {
        setState(AssistantState.LISTENING_COMMAND)
        updateNotification("Listening for your command…")
        commandInput?.destroy()
        val input = SpeechInput(this) { heard, isFinal ->
            if (isFinal) processCommand(heard)
        }
        commandInput = input
        scope.launch {
            input.error.collect { err ->
                if (err != null && currentState.value == AssistantState.LISTENING_COMMAND) {
                    tts?.speak("I couldn't understand that. Please try again.", settings.speakResponsesValue)
                    finishTurn()
                }
            }
        }
        input.start(preferOffline = true, partialResults = false)
        // Safety timeout: SpeechRecognizer usually ends on its own; recover if it hangs.
        scope.launch {
            delay(COMMAND_TIMEOUT_MS)
            if (currentState.value == AssistantState.LISTENING_COMMAND) {
                commandInput?.stop()
                finishTurn()
            }
        }
    }

    private fun processCommand(heard: String) {
        if (heard.isBlank()) {
            tts?.speak("I didn't hear a command. Say \"${settings.wakePhrase}\" to try again.",
                settings.speakResponsesValue)
            finishTurn()
            return
        }
        setState(AssistantState.PROCESSING)
        updateNotification("Working on it…")
        scope.launch(Dispatchers.IO) {
            val cmd = CommandParser.parse(heard, LocalDateTime.now())
            val result = runCatching { executor.execute(cmd) }.getOrElse {
                com.techsagehub.mannai.assistant.CommandResult("Something went wrong. Please try again.")
            }
            scope.launch(Dispatchers.Main) {
                // Confirmation prompts from voice: speak the question, then treat the
                // *next* command as the answer only if it parses to Confirm/Cancel.
                if (result.confirm != null) {
                    pendingVoiceConfirm = result.confirm
                    setState(AssistantState.SPEAKING)
                    tts?.speak(result.text, settings.speakResponsesValue)
                    scope.launch {
                        delay(2500)
                        finishTurn()
                    }
                    return@launch
                }
                setState(AssistantState.SPEAKING)
                tts?.speak(result.text, settings.speakResponsesValue)
                scope.launch {
                    delay(2500)
                    finishTurn()
                }
            }
        }
    }

    private fun finishTurn() {
        commandInput?.stop()
        busy = false
        if (isRunning.value) beginWakeLoop()
    }

    private fun shutdown(reason: String) {
        isRunning.value = false
        busy = false
        wakeInput?.destroy()
        commandInput?.destroy()
        tts?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun promoteToForeground(statusLine: String) {
        val notification = buildNotification(statusLine)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(statusLine: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(statusLine))
    }

    private fun buildNotification(statusLine: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, MannAIChannels.SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MannAI is listening")
            .setContentText(statusLine)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(R.mipmap.ic_launcher, "Stop listening", stop)
            .build()
    }

    override fun onDestroy() {
        isRunning.value = false
        wakeInput?.destroy()
        commandInput?.destroy()
        tts?.shutdown()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.techsagehub.mannai.START_LISTENING"
        const val ACTION_STOP = "com.techsagehub.mannai.STOP_LISTENING"
        const val NOTIF_ID = 1001
        private const val ACTIVATION_COOLDOWN_MS = 3000L
        private const val COMMAND_TIMEOUT_MS = 15_000L
        private const val MAX_ERROR_STREAK = 6

        private val _state = MutableStateFlow(AssistantState.DISABLED)
        val currentState: StateFlow<AssistantState> = _state.asStateFlow()

        val isRunning = MutableStateFlow(false)

        @Volatile private var instance: AssistantService? = null

        /** Voice confirmation captured from a service turn (single-slot). */
        @Volatile var pendingVoiceConfirm: com.techsagehub.mannai.assistant.PendingConfirm? = null
            private set

        fun setState(s: AssistantState) {
            _state.value = s
        }

        /** Must be called from the visible app (foreground) on Android 12+. */
        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (instance != null) {
                context.startService(Intent(context, AssistantService::class.java).setAction(ACTION_STOP))
            } else {
                _state.value = AssistantState.DISABLED
                isRunning.value = false
            }
        }
    }
}

private fun AssistantService.setState(s: AssistantState) = AssistantService.setState(s)
