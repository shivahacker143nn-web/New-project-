package com.example.di

import android.content.Context
import com.example.data.firebase.AuthManager
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.firebase.GeminiAiService
import com.example.data.local.AishuDatabase
import com.example.data.local.LocalModelManager
import com.example.data.local.SecureKeyStore
import com.example.data.local.SettingsRepository
import com.example.data.network.NetworkMonitor
import com.example.data.network.OfflineAssistantEngine
import com.example.data.repository.AishuRepository
import com.example.voice.CustomVoiceRecorder
import com.example.voice.TtsVoiceEngine
import com.example.voice.VoiceCharacteristicsAnalyzer
import com.example.voice.VoiceSpeechManager

/**
 * Process-wide application container so [com.example.ui.AishuViewModel] and
 * [com.example.ui.VoiceSettingsViewModel] share the exact same database, settings store,
 * custom voice recorder, acoustic analyzer, Gemini AI service, and Text-to-Speech engine.
 */
class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database = AishuDatabase.getInstance(appContext)
    val secureKeyStore = SecureKeyStore(appContext)
    val settingsRepository = SettingsRepository(appContext, secureKeyStore)
    val networkMonitor = NetworkMonitor(appContext)
    val offlineEngine = OfflineAssistantEngine()
    val geminiAiService = GeminiAiService()
    val firestoreSync = FirestoreSyncRepository(appContext)
    val voiceAnalyzer = VoiceCharacteristicsAnalyzer()

    val authManager = AuthManager(appContext)
    val localModelManager = LocalModelManager(appContext)

    val repository = AishuRepository(
        dao = database.aishuDao(),
        settingsRepository = settingsRepository,
        offlineEngine = offlineEngine,
        networkMonitor = networkMonitor,
        geminiAiService = geminiAiService,
        firestoreSync = firestoreSync
    )

    val speechManager = VoiceSpeechManager(appContext)
    val ttsEngine = TtsVoiceEngine(appContext)
    val customVoiceRecorder = CustomVoiceRecorder(appContext)

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun get(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
