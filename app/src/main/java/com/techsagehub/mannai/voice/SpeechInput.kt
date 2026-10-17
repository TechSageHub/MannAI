package com.techsagehub.mannai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Recognition state machine shared by tap-to-talk and the background service. */
enum class VoiceState {
    IDLE, STARTING, LISTENING, PROCESSING, ERROR, UNAVAILABLE
}

/**
 * One-shot speech recognition over [SpeechRecognizer]. Call [start], observe
 * [state] / [error], receive results via [onResult]. Call [stop] to cancel and
 * [destroy] when the owner is torn down. Serializes calls: [start] while
 * listening restarts the session.
 */
class SpeechInput(
    private val context: Context,
    private val onResult: (text: String, isFinal: Boolean) -> Unit
) {
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(preferOffline: Boolean = true, partialResults: Boolean = true) {
        if (!isAvailable()) {
            _state.value = VoiceState.UNAVAILABLE
            _error.value = "Speech recognition isn't available on this device."
            return
        }
        stopInternal()
        _error.value = null
        _state.value = VoiceState.STARTING
        val sr = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = VoiceState.LISTENING }
            override fun onBeginningOfSpeech() { _state.value = VoiceState.LISTENING }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { _state.value = VoiceState.PROCESSING }
            override fun onError(error: Int) {
                _state.value = VoiceState.ERROR
                _error.value = describeError(error)
            }
            override fun onResults(results: Bundle?) {
                _state.value = VoiceState.IDLE
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                onResult(text, true)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onResult(text, false)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prefer on-device recognition when the device supports it; falls
            // back to online automatically. Never assumed offline.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            sr.startListening(intent)
        } catch (e: SecurityException) {
            _state.value = VoiceState.ERROR
            _error.value = "Microphone permission is required for voice input."
        }
    }

    fun stop() {
        stopInternal()
        _state.value = VoiceState.IDLE
    }

    private fun stopInternal() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun destroy() = stop()

    companion object {
        /** Tap-to-talk fallback intent (system speech dialog). */
        fun dictationIntent(prompt: String): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

        fun describeError(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio problem. Try again."
            SpeechRecognizer.ERROR_CLIENT -> "Voice input was interrupted."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition needs internet on this device. Check your connection."
            SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't understand that. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
            SpeechRecognizer.ERROR_SERVER -> "Recognition service error. Try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Try again."
            else -> "Voice input error ($code)."
        }
    }
}
