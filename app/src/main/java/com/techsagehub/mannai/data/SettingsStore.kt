package com.techsagehub.mannai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local user settings. Backed by private SharedPreferences; no account, no cloud.
 * Raw audio is never stored.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("mannai_settings", Context.MODE_PRIVATE)

    private val _assistantName = MutableStateFlow(prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME)
    val assistantName: StateFlow<String> = _assistantName.asStateFlow()

    private val _wakeEnabled = MutableStateFlow(prefs.getBoolean(KEY_WAKE, false))
    val wakeEnabled: StateFlow<Boolean> = _wakeEnabled.asStateFlow()

    private val _speakResponses = MutableStateFlow(prefs.getBoolean(KEY_SPEAK, true))
    val speakResponses: StateFlow<Boolean> = _speakResponses.asStateFlow()

    private val _aiEndpoint = MutableStateFlow(prefs.getString(KEY_AI_ENDPOINT, "") ?: "")
    val aiEndpoint: StateFlow<String> = _aiEndpoint.asStateFlow()

    private val _aiKey = MutableStateFlow(prefs.getString(KEY_AI_KEY, "") ?: "")
    val aiKey: StateFlow<String> = _aiKey.asStateFlow()

    private val _aiModel = MutableStateFlow(prefs.getString(KEY_AI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiProvider = MutableStateFlow(prefs.getString(KEY_AI_PROVIDER, DEFAULT_AI_PROVIDER) ?: DEFAULT_AI_PROVIDER)
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    var assistantNameValue: String
        get() = _assistantName.value
        set(v) {
            val clean = v.trim().ifEmpty { DEFAULT_NAME }.take(24)
            prefs.edit { putString(KEY_NAME, clean) }
            _assistantName.value = clean
        }

    /** Canonical wake phrase derived from the configured name, e.g. "Hey Manny". */
    val wakePhrase: String get() = "Hey ${_assistantName.value}"

    var wakeEnabledValue: Boolean
        get() = _wakeEnabled.value
        set(v) {
            prefs.edit { putBoolean(KEY_WAKE, v) }
            _wakeEnabled.value = v
        }

    var speakResponsesValue: Boolean
        get() = _speakResponses.value
        set(v) {
            prefs.edit { putBoolean(KEY_SPEAK, v) }
            _speakResponses.value = v
        }

    fun saveAiConfig(endpoint: String, key: String, model: String) {
        saveAiConfig(endpoint, key, model, _aiProvider.value)
    }

    fun saveAiConfig(endpoint: String, key: String, model: String, provider: String) {
        val p = provider.ifBlank { DEFAULT_AI_PROVIDER }
        prefs.edit {
            putString(KEY_AI_ENDPOINT, endpoint.trim())
            putString(KEY_AI_KEY, key.trim())
            putString(KEY_AI_MODEL, model.trim().ifEmpty { defaultModelFor(p) })
            putString(KEY_AI_PROVIDER, p)
        }
        _aiEndpoint.value = endpoint.trim()
        _aiKey.value = key.trim()
        _aiModel.value = model.trim().ifEmpty { defaultModelFor(p) }
        _aiProvider.value = p
    }

    fun setAiProvider(provider: String) {
        val p = provider.ifBlank { DEFAULT_AI_PROVIDER }
        prefs.edit { putString(KEY_AI_PROVIDER, p) }
        _aiProvider.value = p
        // Reset model to the default for the newly selected provider if it is still the other provider's default.
        val current = _aiModel.value
        if (current == DEFAULT_GEMINI_MODEL || current == DEFAULT_MODEL) {
            _aiModel.value = defaultModelFor(p)
            prefs.edit { putString(KEY_AI_MODEL, defaultModelFor(p)) }
        }
    }

    /** Default model for a given provider. */
    fun defaultModelFor(provider: String): String = when (provider) {
        PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL
        else -> DEFAULT_MODEL
    }

    fun clearAiConfig() = saveAiConfig("", "", DEFAULT_MODEL, DEFAULT_AI_PROVIDER)

    val isAiConfigured: Boolean
        get() = _aiEndpoint.value.isNotBlank() && _aiKey.value.isNotBlank()

    // ---- WhatsApp selected-sender allowlist ----

    private val _allowedSenders = MutableStateFlow(
        (prefs.getString(KEY_ALLOWED_SENDERS, "") ?: "")
            .split(",")
            .filter { it.isNotBlank() }
            .map { it.trim().lowercase() }
            .toSet()
    )
    val allowedSenders: StateFlow<Set<String>> = _allowedSenders.asStateFlow()

    fun addAllowedSender(sender: String) {
        val n = normalizeSender(sender)
        if (n.isNotBlank() && n !in _allowedSenders.value) {
            _allowedSenders.value = _allowedSenders.value + n
            persistAllowedSenders()
        }
    }

    fun removeAllowedSender(sender: String) {
        val n = normalizeSender(sender)
        _allowedSenders.value = _allowedSenders.value - n
        persistAllowedSenders()
    }

    fun isSenderAllowed(sender: String): Boolean {
        val n = normalizeSender(sender)
        return n.isNotBlank() && n in _allowedSenders.value
    }

    fun normalizeSender(sender: String): String = sender.trim().lowercase()

    private fun persistAllowedSenders() {
        prefs.edit {
            putString(KEY_ALLOWED_SENDERS, _allowedSenders.value.sorted().joinToString(","))
        }
    }

    companion object {
        const val DEFAULT_NAME = "MannAI"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_GEMINI_MODEL = "gemini-1.5-flash"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI_COMPATIBLE = "openai_compatible"
        const val DEFAULT_AI_PROVIDER = PROVIDER_GEMINI
        const val KEY_NAME = "assistant_name"
        const val KEY_WAKE = "wake_enabled"
        const val KEY_SPEAK = "speak_responses"
        const val KEY_AI_ENDPOINT = "ai_endpoint"
        const val KEY_AI_KEY = "ai_key"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_ALLOWED_SENDERS = "allowed_senders"
    }
}
