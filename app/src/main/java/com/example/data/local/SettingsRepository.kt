package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiProvider
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.EmotionStyle
import com.example.data.model.ResponseLength
import com.example.data.model.SpeakingStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aishuDataStore: DataStore<Preferences> by preferencesDataStore(name = "aishu_settings")

data class AishuSettings(
    val onboardingDone: Boolean = false,
    val userName: String = "",
    val personalAssistantName: String = "AI SHU",
    val provider: AiProvider = AiProvider.FIREBASE_GEMINI,
    val apiEndpoint: String = AiProvider.FIREBASE_GEMINI.defaultEndpoint,
    val apiModel: String = AiProvider.FIREBASE_GEMINI.defaultModel,
    val isApiVerified: Boolean = false,
    val lastVerifiedLatencyMs: Long = 0L,
    val googleSearchEnabled: Boolean = true,
    val selectedVoiceId: String = "natural",
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val speakingStyle: SpeakingStyle = SpeakingStyle.NATURAL_HUMAN,
    val useCustomVoice: Boolean = false,
    val emotionStyle: EmotionStyle = EmotionStyle.FRIENDLY,
    val autoAdaptEmotion: Boolean = true,
    val responseLength: ResponseLength = ResponseLength.BALANCED,
    val personalizationNotes: String = "",
    val assistantMode: AssistantMode = AssistantMode.MANUAL,
    val wakeWord: String = "Hey AISHU",
    val wakeWordSensitivity: Int = 7,
    val wakeWordEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.BILINGUAL,
    val autoSaveMemory: Boolean = true,
    val useMemoryContext: Boolean = true
)

class SettingsRepository(
    context: Context,
    val secureKeyStore: SecureKeyStore
) {
    private val dataStore = context.aishuDataStore

    val settingsFlow: Flow<AishuSettings> = dataStore.data.map { prefs ->
        val provider = AiProvider.FIREBASE_GEMINI

        AishuSettings(
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
            userName = prefs[KEY_USER_NAME] ?: "",
            personalAssistantName = prefs[KEY_ASSISTANT_NAME] ?: "AI SHU",
            provider = provider,
            apiEndpoint = provider.defaultEndpoint,
            apiModel = provider.defaultModel,
            isApiVerified = prefs[KEY_API_VERIFIED] ?: false,
            lastVerifiedLatencyMs = prefs[KEY_API_LATENCY] ?: 0L,
            googleSearchEnabled = prefs[KEY_GOOGLE_SEARCH] ?: true,
            selectedVoiceId = prefs[KEY_VOICE_ID] ?: "natural",
            speechSpeed = prefs[KEY_SPEECH_SPEED] ?: 1.0f,
            speechPitch = prefs[KEY_SPEECH_PITCH] ?: 1.0f,
            speechVolume = prefs[KEY_SPEECH_VOLUME] ?: 1.0f,
            speakingStyle = runCatching {
                SpeakingStyle.valueOf(prefs[KEY_SPEAKING_STYLE] ?: SpeakingStyle.NATURAL_HUMAN.name)
            }.getOrDefault(SpeakingStyle.NATURAL_HUMAN),
            useCustomVoice = prefs[KEY_USE_CUSTOM_VOICE] ?: false,
            emotionStyle = runCatching {
                EmotionStyle.valueOf(prefs[KEY_EMOTION] ?: EmotionStyle.FRIENDLY.name)
            }.getOrDefault(EmotionStyle.FRIENDLY),
            autoAdaptEmotion = prefs[KEY_AUTO_EMOTION] ?: true,
            responseLength = runCatching {
                ResponseLength.valueOf(prefs[KEY_RESPONSE_LENGTH] ?: ResponseLength.BALANCED.name)
            }.getOrDefault(ResponseLength.BALANCED),
            personalizationNotes = prefs[KEY_PERSONALIZATION_NOTES] ?: "",
            assistantMode = runCatching {
                AssistantMode.valueOf(prefs[KEY_ASSISTANT_MODE] ?: AssistantMode.MANUAL.name)
            }.getOrDefault(AssistantMode.MANUAL),
            wakeWord = prefs[KEY_WAKE_WORD] ?: "Hey AISHU",
            wakeWordSensitivity = prefs[KEY_WAKE_SENSITIVITY] ?: 7,
            wakeWordEnabled = prefs[KEY_WAKE_ENABLED] ?: false,
            language = runCatching {
                AppLanguage.valueOf(prefs[KEY_LANGUAGE] ?: AppLanguage.BILINGUAL.name)
            }.getOrDefault(AppLanguage.BILINGUAL),
            autoSaveMemory = prefs[KEY_AUTO_MEMORY] ?: true,
            useMemoryContext = prefs[KEY_USE_MEMORY] ?: true
        )
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun updateCustomSettings(
        userName: String? = null,
        assistantName: String? = null,
        responseLength: ResponseLength? = null,
        personalizationNotes: String? = null
    ) {
        dataStore.edit { prefs ->
            userName?.let { prefs[KEY_USER_NAME] = it.trim() }
            assistantName?.let { prefs[KEY_ASSISTANT_NAME] = it.trim().ifEmpty { "AI SHU" } }
            responseLength?.let { prefs[KEY_RESPONSE_LENGTH] = it.name }
            personalizationNotes?.let { prefs[KEY_PERSONALIZATION_NOTES] = it.trim() }
        }
    }

    suspend fun updateGeminiSearchEnabled(googleSearchEnabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PROVIDER] = AiProvider.FIREBASE_GEMINI.name
            prefs[KEY_API_ENDPOINT] = AiProvider.FIREBASE_GEMINI.defaultEndpoint
            prefs[KEY_API_MODEL] = AiProvider.FIREBASE_GEMINI.defaultModel
            prefs[KEY_GOOGLE_SEARCH] = googleSearchEnabled
        }
    }

    suspend fun markConnectionVerified(verified: Boolean, latencyMs: Long = 0L) {
        dataStore.edit { prefs ->
            prefs[KEY_API_VERIFIED] = verified
            prefs[KEY_API_LATENCY] = latencyMs
        }
    }

    suspend fun updateVoiceSettings(
        voiceId: String? = null,
        speed: Float? = null,
        pitch: Float? = null,
        volume: Float? = null,
        speakingStyle: SpeakingStyle? = null,
        useCustomVoice: Boolean? = null
    ) {
        dataStore.edit { prefs ->
            voiceId?.let { prefs[KEY_VOICE_ID] = it }
            speed?.let { prefs[KEY_SPEECH_SPEED] = it.coerceIn(0.5f, 2.0f) }
            pitch?.let { prefs[KEY_SPEECH_PITCH] = it.coerceIn(0.5f, 2.0f) }
            volume?.let { prefs[KEY_SPEECH_VOLUME] = it.coerceIn(0.0f, 1.0f) }
            speakingStyle?.let { prefs[KEY_SPEAKING_STYLE] = it.name }
            useCustomVoice?.let { prefs[KEY_USE_CUSTOM_VOICE] = it }
        }
    }

    suspend fun updateEmotion(style: EmotionStyle? = null, autoAdapt: Boolean? = null) {
        dataStore.edit { prefs ->
            style?.let { prefs[KEY_EMOTION] = it.name }
            autoAdapt?.let { prefs[KEY_AUTO_EMOTION] = it }
        }
    }

    suspend fun updateWakeWordSettings(
        wakeWord: String? = null,
        sensitivity: Int? = null,
        enabled: Boolean? = null,
        mode: AssistantMode? = null
    ) {
        dataStore.edit { prefs ->
            wakeWord?.let { prefs[KEY_WAKE_WORD] = it.trim().ifEmpty { "Hey AISHU" } }
            sensitivity?.let { prefs[KEY_WAKE_SENSITIVITY] = it.coerceIn(1, 10) }
            enabled?.let { prefs[KEY_WAKE_ENABLED] = it }
            mode?.let { prefs[KEY_ASSISTANT_MODE] = it.name }
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    suspend fun updateMemorySettings(autoSave: Boolean? = null, useContext: Boolean? = null) {
        dataStore.edit { prefs ->
            autoSave?.let { prefs[KEY_AUTO_MEMORY] = it }
            useContext?.let { prefs[KEY_USE_MEMORY] = it }
        }
    }

    suspend fun clearAllSettingsAndSecrets() {
        secureKeyStore.clearAll()
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        private val KEY_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val KEY_API_MODEL = stringPreferencesKey("api_model")
        private val KEY_API_VERIFIED = booleanPreferencesKey("api_verified")
        private val KEY_API_LATENCY = longPreferencesKey("api_latency")
        private val KEY_GOOGLE_SEARCH = booleanPreferencesKey("google_search_enabled")
        private val KEY_VOICE_ID = stringPreferencesKey("voice_id")
        private val KEY_SPEECH_SPEED = floatPreferencesKey("speech_speed")
        private val KEY_SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        private val KEY_SPEECH_VOLUME = floatPreferencesKey("speech_volume")
        private val KEY_SPEAKING_STYLE = stringPreferencesKey("speaking_style")
        private val KEY_USE_CUSTOM_VOICE = booleanPreferencesKey("use_custom_voice")
        private val KEY_EMOTION = stringPreferencesKey("emotion_style")
        private val KEY_AUTO_EMOTION = booleanPreferencesKey("auto_adapt_emotion")
        private val KEY_RESPONSE_LENGTH = stringPreferencesKey("response_length")
        private val KEY_PERSONALIZATION_NOTES = stringPreferencesKey("personalization_notes")
        private val KEY_ASSISTANT_MODE = stringPreferencesKey("assistant_mode")
        private val KEY_WAKE_WORD = stringPreferencesKey("wake_word")
        private val KEY_WAKE_SENSITIVITY = intPreferencesKey("wake_sensitivity")
        private val KEY_WAKE_ENABLED = booleanPreferencesKey("wake_enabled")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_AUTO_MEMORY = booleanPreferencesKey("auto_save_memory")
        private val KEY_USE_MEMORY = booleanPreferencesKey("use_memory_context")
    }
}
