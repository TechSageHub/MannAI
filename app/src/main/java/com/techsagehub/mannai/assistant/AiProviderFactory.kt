package com.techsagehub.mannai.assistant

import com.techsagehub.mannai.data.SettingsStore

/**
 * Selects and builds the configured AI provider.
 *
 * The choice of provider is purely local user configuration. No provider is
 * contacted unless [AiProvider.isConfigured] is true and the user initiates a
 * request. Failures are never silently routed to a different provider.
 */
object AiProviderFactory {
    fun create(settings: SettingsStore): AiProvider {
        return if (!settings.isAiConfigured) {
            DisabledAiProvider()
        } else when (settings.aiProvider.value) {
            SettingsStore.PROVIDER_GEMINI -> GeminiProvider(settings.aiKey.value, settings.aiModel.value)
            else -> OpenAiCompatibleProvider(
                settings.aiEndpoint.value, settings.aiKey.value, settings.aiModel.value
            )
        }
    }
}
