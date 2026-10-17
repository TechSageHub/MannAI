package com.techsagehub.mannai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Android TextToSpeech wrapper. Speaks short confirmations/answers only;
 * never speaks long or sensitive content unprompted. Safe to call before
 * init finishes — utterances queue until ready, failures are reported.
 */
class TtsSpeaker(context: Context) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var initFailed = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val res = t.setLanguage(Locale.getDefault())
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale.ENGLISH)
                }
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { _speaking.value = true }
                    override fun onDone(utteranceId: String?) { _speaking.value = false }
                    override fun onError(utteranceId: String?) {
                        _speaking.value = false
                        _lastError.value = "Speech output failed."
                    }
                })
                _ready.value = true
            } else {
                initFailed = true
                _lastError.value = "Speech output isn't available on this device."
            }
        }
    }

    /** Speak [text] if [enabled]; truncates long text for safety. */
    fun speak(text: String, enabled: Boolean) {
        if (!enabled) return
        val t = tts
        if (initFailed || t == null) {
            _lastError.value = "Speech output isn't available on this device."
            return
        }
        val safe = text.take(MAX_CHARS)
        runCatching {
            t.speak(safe, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        }.onFailure {
            _lastError.value = "Speech output failed."
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        _speaking.value = false
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }

    companion object {
        const val MAX_CHARS = 300
    }
}
