package com.example.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthUserInfo
import com.example.data.firebase.FirebaseMaskedConfig
import com.example.data.firebase.GeminiAiService
import com.example.data.firebase.PhoneOtpState
import com.example.data.firebase.readMaskedFirebaseConfig
import com.example.data.local.AishuSettings
import com.example.data.local.AssistantRuleEntity
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.CustomVoiceProfileEntity
import com.example.data.local.LocalModelManager
import com.example.data.local.MemoryFactEntity
import com.example.data.local.ModelDownloadState
import com.example.data.local.OfflineModelCatalogItem
import com.example.data.local.SavedPhotoEntity
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.ConnectionStatus
import com.example.data.model.EmotionStyle
import com.example.data.model.ResponseLength
import com.example.data.model.SpeakingStyle
import com.example.data.model.VoiceOption
import com.example.data.model.VoiceSessionState
import com.example.data.repository.AishuRepository
import com.example.di.AppContainer
import com.example.voice.CustomVoiceRecorder
import com.example.voice.TtsVoiceEngine
import com.example.voice.VoiceSpeechManager
import com.example.voice.WakeWordBus
import com.example.voice.WakeWordForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AishuViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val container = AppContainer.get(application)
    private val secureKeyStore = container.secureKeyStore
    private val settingsRepository = container.settingsRepository
    private val networkMonitor = container.networkMonitor

    val authManager = container.authManager
    val localModelManager = container.localModelManager
    val repository = container.repository

    val speechManager: VoiceSpeechManager = container.speechManager
    val ttsEngine: TtsVoiceEngine = container.ttsEngine
    val customVoiceRecorder: CustomVoiceRecorder = container.customVoiceRecorder

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    val settings: StateFlow<AishuSettings> = repository.settingsFlow
        .map { loaded ->
            _settingsLoaded.value = true
            loaded
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AishuSettings())

    val currentUser: StateFlow<AuthUserInfo?> = authManager.currentUser
    val phoneOtpState: StateFlow<PhoneOtpState> = authManager.phoneOtpState

    val isNetworkAvailable: StateFlow<Boolean> = repository.isNetworkAvailable
    val isEffectiveOffline: StateFlow<Boolean> = isNetworkAvailable.map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    val messages: StateFlow<List<ConversationMessageEntity>> = repository.messagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredHistory: StateFlow<List<ConversationMessageEntity>> = _historySearchQuery
        .flatMapLatest { query -> repository.searchMessages(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryFactEntity>> = repository.memoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customVoices: StateFlow<List<CustomVoiceProfileEntity>> = repository.customVoicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<AssistantRuleEntity>> = repository.rulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ruleConflicts: StateFlow<List<String>> = rules
        .map { AishuRepository.detectRuleConflicts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPhotos: StateFlow<List<SavedPhotoEntity>> = repository.savedPhotosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val modelDownloadState: StateFlow<ModelDownloadState> = localModelManager.downloadState
    val modelCatalog: List<OfflineModelCatalogItem> = localModelManager.catalog

    private val _sessionState = MutableStateFlow(VoiceSessionState.IDLE)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    private val _isHandsFreeActive = MutableStateFlow(false)
    val isHandsFreeActive: StateFlow<Boolean> = _isHandsFreeActive.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _activeEmotion = MutableStateFlow(EmotionStyle.FRIENDLY)
    val activeEmotion: StateFlow<EmotionStyle> = _activeEmotion.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.NotConfigured)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    private val _allowOfflineLocalSession = MutableStateFlow(false)
    val allowOfflineLocalSession: StateFlow<Boolean> = _allowOfflineLocalSession.asStateFlow()

    private val _pendingAttachmentPath = MutableStateFlow<String?>(null)
    val pendingAttachmentPath: StateFlow<String?> = _pendingAttachmentPath.asStateFlow()

    private val _isAnalyzingPhotoId = MutableStateFlow<Long?>(null)
    val isAnalyzingPhotoId: StateFlow<Long?> = _isAnalyzingPhotoId.asStateFlow()

    val wakeWordServiceRunning: StateFlow<Boolean> = WakeWordBus.serviceRunning
    val wakeWordServiceStatus: StateFlow<String> = WakeWordBus.statusText

    private var activeTurnJob: Job? = null
    private var reListenJob: Job? = null
    private var lastListenStartMs: Long = 0L
    private var consecutiveFastErrors = 0

    init {
        viewModelScope.launch {
            repository.seedDefaultRulesIfEmpty()
            authManager.attemptSilentGoogleSignIn()
        }
        observeSavedConnectionState()
        observeSpeechEvents()
        observeWakeWordTriggers()
    }

    private fun observeSavedConnectionState() {
        viewModelScope.launch {
            settings.collect { s ->
                if (_connectionStatus.value !is ConnectionStatus.Testing) {
                    _connectionStatus.value = if (isNetworkAvailable.value) {
                        ConnectionStatus.Connected(
                            providerName = "Google Gemini",
                            model = GeminiAiService.DEFAULT_MODEL,
                            latencyMs = s.lastVerifiedLatencyMs
                        )
                    } else {
                        ConnectionStatus.NotConfigured
                    }
                }
                if (!_isHandsFreeActive.value) {
                    _activeEmotion.value = s.emotionStyle
                }
            }
        }
    }

    private fun observeSpeechEvents() {
        viewModelScope.launch {
            speechManager.finalResults.collect { spokenText ->
                consecutiveFastErrors = 0
                handleRecognizedSpeech(
                    userText = spokenText,
                    continueVoiceLoopAfter = settings.value.assistantMode == AssistantMode.AUTOMATIC &&
                        _isHandsFreeActive.value
                )
            }
        }
        viewModelScope.launch {
            speechManager.errors.collect { err ->
                if (err == "NO_MATCH" || err == "SPEECH_TIMEOUT") {
                    val isAutoActive = settings.value.assistantMode == AssistantMode.AUTOMATIC &&
                        _isHandsFreeActive.value &&
                        !_isMicMuted.value
                    val elapsedSinceStart = System.currentTimeMillis() - lastListenStartMs
                    val isRapidFailure = elapsedSinceStart in 1L..299L

                    if (isAutoActive && (!isRapidFailure || consecutiveFastErrors < 2)) {
                        consecutiveFastErrors = if (isRapidFailure) consecutiveFastErrors + 1 else 0
                        reListenJob?.cancel()
                        reListenJob = viewModelScope.launch {
                            delay(420L)
                            if (settings.value.assistantMode == AssistantMode.AUTOMATIC &&
                                _isHandsFreeActive.value &&
                                !_isMicMuted.value
                            ) {
                                beginListeningStep()
                            }
                        }
                    } else {
                        _isHandsFreeActive.value = false
                        _sessionState.value = VoiceSessionState.IDLE
                        _statusBanner.value = if (settings.value.assistantMode == AssistantMode.AUTOMATIC) {
                            "Automatic listening paused (no speech detected). Tap Speak to resume."
                        } else {
                            "No speech detected. Tap Speak to try again."
                        }
                    }
                } else {
                    _isHandsFreeActive.value = false
                    _sessionState.value = VoiceSessionState.IDLE
                    _statusBanner.value = err
                }
            }
        }
    }

    private fun observeWakeWordTriggers() {
        viewModelScope.launch {
            WakeWordBus.wakeDetectedEvents.collect {
                onWakeWordActivated()
            }
        }
    }

    // ---- Authentication Actions ----

    fun signInWithGoogle(activity: Activity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authMessage.value = null
            val res = authManager.signInWithGoogleInteractive(activity)
            _isAuthBusy.value = false
            res.fold(
                onSuccess = {
                    repository.syncSettingsAndRulesToCloud()
                    onSuccess()
                },
                onFailure = { err ->
                    _authMessage.value = err.localizedMessage ?: "Google Sign-In was cancelled or failed."
                }
            )
        }
    }

    fun startPhoneOtpVerification(activity: Activity, phoneNumber: String, onAutoVerified: () -> Unit) {
        _authMessage.value = null
        authManager.startPhoneNumberVerification(activity, phoneNumber) {
            viewModelScope.launch {
                repository.syncSettingsAndRulesToCloud()
                onAutoVerified()
            }
        }
    }

    fun verifyPhoneOtp(verificationId: String, otpCode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authMessage.value = null
            val res = authManager.verifyPhoneOtpCode(verificationId, otpCode)
            _isAuthBusy.value = false
            res.fold(
                onSuccess = {
                    repository.syncSettingsAndRulesToCloud()
                    onSuccess()
                },
                onFailure = { err ->
                    _authMessage.value = err.localizedMessage ?: "OTP verification failed."
                }
            )
        }
    }

    fun resetPhoneOtp() {
        authManager.resetPhoneOtpState()
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _isAuthBusy.value = true
            val res = authManager.sendPasswordReset(email)
            _isAuthBusy.value = false
            res.fold(
                onSuccess = {
                    _authMessage.value = "Password reset link sent to $email (requires Email provider enabled in Firebase Console)."
                },
                onFailure = { err ->
                    _authMessage.value = err.localizedMessage ?: "Could not send password reset email."
                }
            )
        }
    }

    fun setAuthNotice(msg: String?) {
        _authMessage.value = msg
    }

    fun enterOfflineLocalMode() {
        _allowOfflineLocalSession.value = true
    }

    fun updateUserDisplayName(name: String) {
        viewModelScope.launch {
            val res = authManager.updateDisplayName(name)
            _statusBanner.value = res.fold(
                onSuccess = { "Account display name updated." },
                onFailure = { it.localizedMessage ?: "Failed to update display name." }
            )
        }
    }

    fun syncCloudProfileAndRules() {
        viewModelScope.launch {
            if (!authManager.isSignedIn()) {
                _statusBanner.value = "Sign in first to sync settings and rules to Cloud Firestore."
                return@launch
            }
            repository.syncSettingsAndRulesToCloud()
            _statusBanner.value = "Profile and permanent rules synced to Cloud Firestore."
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        stopAllVoiceActivity()
        WakeWordForegroundService.stop(context)
        _allowOfflineLocalSession.value = false
        viewModelScope.launch {
            authManager.signOut()
            onLoggedOut()
        }
    }

    // ---- Voice Session & Assistant Conversation ----

    fun onWakeWordActivated() {
        val current = settings.value
        val greeting = if (current.language == AppLanguage.TELUGU) {
            "నమస్కారం, చెప్పండి. నేను వింటున్నాను."
        } else {
            "${current.personalAssistantName} online. Listening."
        }
        _statusBanner.value = "Wake word detected: \"${current.wakeWord}\""
        _isHandsFreeActive.value = true
        _sessionState.value = VoiceSessionState.SPEAKING
        speakAssistantText(
            text = greeting,
            onComplete = {
                if (_isHandsFreeActive.value) {
                    beginListeningStep()
                }
            }
        )
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone(true)
        }
    }

    fun startVoiceSession() {
        _statusBanner.value = null
        consecutiveFastErrors = 0
        reListenJob?.cancel()
        activeTurnJob?.cancel()
        if (_isMicMuted.value) {
            _isMicMuted.value = false
        }
        if (!speechManager.hasMicrophonePermission()) {
            _statusBanner.value = "Microphone permission is not granted. Enable it in Settings > Permissions."
            return
        }
        if (!speechManager.isSpeechRecognitionAvailable()) {
            _statusBanner.value = "Speech recognition service is unavailable on this device. Use the text command box below."
            return
        }
        if (wakeWordServiceRunning.value) {
            WakeWordForegroundService.pause(context)
        }
        // Automatic Mode enables continuous hands-free loop; Manual Mode executes one turn only.
        _isHandsFreeActive.value = settings.value.assistantMode == AssistantMode.AUTOMATIC
        ttsEngine.stop()
        beginListeningStep()
    }

    fun toggleMuteMic() {
        val nextMuted = !_isMicMuted.value
        _isMicMuted.value = nextMuted
        if (nextMuted) {
            reListenJob?.cancel()
            speechManager.stopListening()
            if (wakeWordServiceRunning.value) {
                WakeWordForegroundService.pause(context)
            }
            if (_sessionState.value == VoiceSessionState.LISTENING || _sessionState.value == VoiceSessionState.WAKE_WORD_STANDBY) {
                _sessionState.value = VoiceSessionState.IDLE
            }
            _statusBanner.value = "Microphone muted. Tap Unmute to resume listening."
        } else {
            _statusBanner.value = "Microphone unmuted."
            if (_isHandsFreeActive.value && speechManager.hasMicrophonePermission()) {
                beginListeningStep()
            } else if (settings.value.assistantMode == AssistantMode.AUTOMATIC &&
                settings.value.wakeWordEnabled &&
                speechManager.hasMicrophonePermission() &&
                hasNotificationPermissionOrLegacy()
            ) {
                WakeWordForegroundService.resume(context)
                _sessionState.value = VoiceSessionState.WAKE_WORD_STANDBY
            }
        }
    }

    private fun beginListeningStep() {
        if (_isMicMuted.value) {
            _sessionState.value = VoiceSessionState.IDLE
            return
        }
        reListenJob?.cancel()
        reListenJob = viewModelScope.launch {
            // Prevent microphone echo from picking up trailing TTS output
            while (ttsEngine.isSpeaking.value) {
                delay(80L)
            }
            delay(220L)
            if (_isMicMuted.value) {
                _sessionState.value = VoiceSessionState.IDLE
                return@launch
            }
            if (settings.value.assistantMode == AssistantMode.AUTOMATIC && !_isHandsFreeActive.value) {
                return@launch
            }
            val s = settings.value
            lastListenStartMs = System.currentTimeMillis()
            _sessionState.value = VoiceSessionState.LISTENING
            speechManager.startListening(
                language = s.language,
                preferOffline = isEffectiveOffline.value
            )
        }
    }

    fun stopAllVoiceActivity() {
        _isHandsFreeActive.value = false
        _isMicMuted.value = false
        consecutiveFastErrors = 0
        reListenJob?.cancel()
        activeTurnJob?.cancel()
        speechManager.stopListening()
        ttsEngine.stop()
        if (wakeWordServiceRunning.value) {
            WakeWordForegroundService.stop(context)
        }
        _sessionState.value = VoiceSessionState.IDLE
    }

    fun onAppBackgrounded() {
        val currentMode = settings.value.assistantMode
        val wasActiveLoop = _isHandsFreeActive.value ||
            _sessionState.value == VoiceSessionState.LISTENING ||
            _sessionState.value == VoiceSessionState.PROCESSING ||
            _sessionState.value == VoiceSessionState.SPEAKING

        if (!wasActiveLoop) return

        reListenJob?.cancel()
        activeTurnJob?.cancel()
        speechManager.stopListening()
        ttsEngine.stop()
        _isHandsFreeActive.value = false

        if (currentMode == AssistantMode.AUTOMATIC) {
            val canRunForegroundStandby = settings.value.wakeWordEnabled &&
                speechManager.hasMicrophonePermission() &&
                hasNotificationPermissionOrLegacy()
            if (canRunForegroundStandby) {
                WakeWordForegroundService.resume(context)
                _sessionState.value = VoiceSessionState.WAKE_WORD_STANDBY
            } else {
                _sessionState.value = VoiceSessionState.IDLE
                _statusBanner.value = "Automatic Mode stopped while AI SHU is in the background because Android restricts background microphone capture without foreground service notification permission."
            }
        } else {
            _sessionState.value = VoiceSessionState.IDLE
        }
    }

    fun onAssistantScreenLeft() {
        if (settings.value.assistantMode == AssistantMode.MANUAL) {
            stopAllVoiceActivity()
        }
    }

    fun sendTextCommand(text: String, speakAloud: Boolean = true) {
        val cleaned = text.trim()
        val attachment = _pendingAttachmentPath.value
        if (cleaned.isEmpty() && attachment == null) return
        val effectivePrompt = cleaned.ifEmpty { "Describe this image in detail." }
        _pendingAttachmentPath.value = null
        _statusBanner.value = null
        handleRecognizedSpeech(
            userText = effectivePrompt,
            attachedImagePath = attachment,
            continueVoiceLoopAfter = settings.value.assistantMode == AssistantMode.AUTOMATIC && _isHandsFreeActive.value,
            speakAloud = speakAloud
        )
    }

    private fun handleRecognizedSpeech(
        userText: String,
        attachedImagePath: String? = _pendingAttachmentPath.value,
        continueVoiceLoopAfter: Boolean = settings.value.assistantMode == AssistantMode.AUTOMATIC && _isHandsFreeActive.value,
        speakAloud: Boolean = true
    ) {
        if (attachedImagePath != null) {
            _pendingAttachmentPath.value = null
        }
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            _sessionState.value = VoiceSessionState.PROCESSING
            val result = repository.processUserTurn(userText, attachedImagePath)
            _activeEmotion.value = result.usedEmotion
            if (result.errorBanner != null) {
                _statusBanner.value = result.errorBanner
            }

            if (speakAloud) {
                _sessionState.value = VoiceSessionState.SPEAKING
                speakAssistantText(
                    text = result.replyText,
                    onComplete = {
                        val shouldContinue = continueVoiceLoopAfter &&
                            settings.value.assistantMode == AssistantMode.AUTOMATIC &&
                            _isHandsFreeActive.value &&
                            !_isMicMuted.value
                        if (shouldContinue) {
                            beginListeningStep()
                        } else {
                            _isHandsFreeActive.value = false
                            _sessionState.value = VoiceSessionState.IDLE
                        }
                    }
                )
            } else {
                val shouldContinue = continueVoiceLoopAfter &&
                    settings.value.assistantMode == AssistantMode.AUTOMATIC &&
                    _isHandsFreeActive.value &&
                    !_isMicMuted.value
                if (shouldContinue) {
                    beginListeningStep()
                } else {
                    _isHandsFreeActive.value = false
                    _sessionState.value = VoiceSessionState.IDLE
                }
            }
        }
    }

    private fun speakAssistantText(text: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val s = settings.value
            val voice = VoiceOption.findById(s.selectedVoiceId)
            val customProfile = if (s.useCustomVoice) repository.getSelectedCustomVoice() else null

            ttsEngine.speak(
                text = text,
                voiceOption = voice,
                speed = s.speechSpeed,
                pitch = s.speechPitch,
                volume = s.speechVolume,
                language = s.language,
                speakingStyle = s.speakingStyle,
                useCustomVoice = s.useCustomVoice,
                customProfile = customProfile,
                onSynthesisNotice = { notice ->
                    if (_statusBanner.value.isNullOrBlank()) {
                        _statusBanner.value = notice
                    }
                },
                onComplete = onComplete
            )
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    // ---- Gemini API & Firebase Configuration ----

    fun getMaskedFirebaseConfig(): FirebaseMaskedConfig = readMaskedFirebaseConfig(context)

    fun updateGoogleSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateGeminiSearchEnabled(enabled)
            _statusBanner.value = if (enabled) {
                "Google Search grounding enabled for Gemini."
            } else {
                "Google Search grounding disabled for Gemini."
            }
        }
    }

    fun testApiConnection() {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Testing
            val result = repository.testConfiguredApiConnection()
            _connectionStatus.value = result
        }
    }

    // ---- Voice Tuning ----

    fun updateVoiceTuning(
        speed: Float? = null,
        pitch: Float? = null,
        volume: Float? = null,
        speakingStyle: SpeakingStyle? = null
    ) {
        viewModelScope.launch {
            val useCustomOverride = if (speakingStyle == SpeakingStyle.CUSTOM) true else null
            settingsRepository.updateVoiceSettings(
                speed = speed,
                pitch = pitch,
                volume = volume,
                speakingStyle = speakingStyle,
                useCustomVoice = useCustomOverride
            )
        }
    }

    // ---- Custom Settings & Personalization ----

    fun saveOnboardingPersonalization(userName: String, assistantName: String) {
        viewModelScope.launch {
            val cleanUser = userName.trim()
            val cleanAssistant = assistantName.trim().ifEmpty { "AI SHU" }
            settingsRepository.updateCustomSettings(
                userName = cleanUser,
                assistantName = cleanAssistant
            )
            if (cleanUser.isNotBlank()) {
                repository.saveMemoryFact("User's name is $cleanUser", category = "PROFILE", pinned = true)
            }
            settingsRepository.setOnboardingDone(true)
            if (authManager.isSignedIn()) {
                repository.syncSettingsAndRulesToCloud()
            }
        }
    }

    fun updateCustomSettings(
        userName: String? = null,
        assistantName: String? = null,
        responseLength: ResponseLength? = null,
        personalizationNotes: String? = null
    ) {
        viewModelScope.launch {
            settingsRepository.updateCustomSettings(userName, assistantName, responseLength, personalizationNotes)
            if (!userName.isNullOrBlank()) {
                repository.saveMemoryFact("User's name is ${userName.trim()}", category = "PROFILE", pinned = true)
            }
            if (authManager.isSignedIn()) {
                repository.syncSettingsAndRulesToCloud()
            }
            _statusBanner.value = "Custom assistant preferences saved."
        }
    }

    fun updateEmotionStyle(style: EmotionStyle) {
        _activeEmotion.value = style
        viewModelScope.launch { settingsRepository.updateEmotion(style = style) }
    }

    fun toggleAutoAdaptEmotion(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateEmotion(autoAdapt = enabled) }
    }

    fun updateWakeWordConfig(wakeWord: String? = null, sensitivity: Int? = null) {
        viewModelScope.launch {
            settingsRepository.updateWakeWordSettings(wakeWord = wakeWord, sensitivity = sensitivity)
            val s = settingsFlowValue()
            if (s.wakeWordEnabled && s.assistantMode == AssistantMode.AUTOMATIC && speechManager.hasMicrophonePermission()) {
                WakeWordForegroundService.start(
                    context = context,
                    wakeWord = wakeWord ?: s.wakeWord,
                    sensitivity = sensitivity ?: s.wakeWordSensitivity,
                    languageTag = s.language.speechLocaleTag
                )
            }
        }
    }

    fun hasNotificationPermissionOrLegacy(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun setAssistantModeAndWakeWord(mode: AssistantMode, wakeWordEnabled: Boolean) {
        viewModelScope.launch {
            if (mode == AssistantMode.MANUAL) {
                stopAllVoiceActivity()
                settingsRepository.updateWakeWordSettings(mode = AssistantMode.MANUAL, enabled = false)
                _statusBanner.value = "Manual Mode active. Tap Speak for a single-turn voice reply."
                return@launch
            }

            val hasMic = speechManager.hasMicrophonePermission()
            val hasNotif = hasNotificationPermissionOrLegacy()
            val allowBgService = wakeWordEnabled && hasMic && hasNotif

            settingsRepository.updateWakeWordSettings(mode = AssistantMode.AUTOMATIC, enabled = allowBgService)
            val s = settingsFlowValue()

            if (!hasMic) {
                _statusBanner.value = "Automatic Mode selected. Grant Microphone permission to start hands-free voice conversations."
                return@launch
            }

            if (allowBgService) {
                runCatching {
                    WakeWordForegroundService.start(
                        context = context,
                        wakeWord = s.wakeWord,
                        sensitivity = s.wakeWordSensitivity,
                        languageTag = s.language.speechLocaleTag
                    )
                }.onSuccess {
                    _sessionState.value = VoiceSessionState.WAKE_WORD_STANDBY
                    _statusBanner.value = "Automatic Mode active. Tap Speak for continuous hands-free conversation or say \"${s.wakeWord}\"."
                }.onFailure {
                    _sessionState.value = VoiceSessionState.IDLE
                    _statusBanner.value = "Automatic Mode ready inside app (background wake service restricted by OS)."
                }
            } else {
                _sessionState.value = VoiceSessionState.IDLE
                _statusBanner.value = "Automatic Mode ready inside app. Allow Notification permission in Permissions to keep standby listening in background."
            }
        }
    }

    fun stopWakeWordListeningImmediately() {
        WakeWordForegroundService.stop(context)
        viewModelScope.launch {
            settingsRepository.updateWakeWordSettings(enabled = false, mode = AssistantMode.MANUAL)
        }
        _sessionState.value = VoiceSessionState.IDLE
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    // ---- Permanent Assistant Rules ----

    fun addRule(title: String, instruction: String, isProtected: Boolean) {
        viewModelScope.launch {
            val res = repository.addRule(title, instruction, isProtected)
            _statusBanner.value = res.fold(
                onSuccess = { "Permanent assistant rule added." },
                onFailure = { it.message ?: "Could not add rule." }
            )
        }
    }

    fun updateRule(rule: AssistantRuleEntity) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }

    fun deleteRule(rule: AssistantRuleEntity) {
        viewModelScope.launch {
            val deleted = repository.deleteRule(rule)
            if (!deleted) {
                _statusBanner.value = "Protected rules cannot be deleted while locked. Unlock protection in edit mode first if needed."
            }
        }
    }

    // ---- Photos & Camera (Zero-Permission Photo Picker + Contextual Camera) ----

    fun createCameraPhotoFile(): File {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "cam_${System.currentTimeMillis()}.jpg")
    }

    fun onCameraPhotoCaptured(file: File, attachToChat: Boolean = false) {
        if (!file.exists() || file.length() == 0L) return
        if (attachToChat) {
            _pendingAttachmentPath.value = file.absolutePath
        }
        viewModelScope.launch {
            repository.savePhoto(file.absolutePath, "Camera Photo")
            _statusBanner.value = if (attachToChat) {
                "Photo captured & attached to message."
            } else {
                "Camera photo saved. Tap Analyze with Gemini Vision to inspect it."
            }
        }
    }

    fun importPhotoFromPicker(uri: Uri, attachToChat: Boolean = false) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "photos")
                    if (!dir.exists()) dir.mkdirs()
                    val dest = File(dir, "pick_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("Failed to read picked photo.")
                    dest.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) {
                if (attachToChat) {
                    _pendingAttachmentPath.value = savedPath
                }
                repository.savePhoto(savedPath, "Selected Photo")
                _statusBanner.value = if (attachToChat) {
                    "Image attached. Ask a question about it below."
                } else {
                    "Photo added to Photos & Camera gallery."
                }
            } else {
                _statusBanner.value = "Unable to load selected photo."
            }
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachmentPath.value = null
    }

    fun analyzeSavedPhoto(photo: SavedPhotoEntity, customPrompt: String, speakAloud: Boolean = false) {
        viewModelScope.launch {
            _isAnalyzingPhotoId.value = photo.id
            val res = repository.analyzePhoto(photo, customPrompt)
            _isAnalyzingPhotoId.value = null
            res.fold(
                onSuccess = { analysis ->
                    if (speakAloud) {
                        speakAssistantText(analysis)
                    }
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Vision analysis failed."
                }
            )
        }
    }

    fun deleteSavedPhoto(photo: SavedPhotoEntity) {
        viewModelScope.launch { repository.deletePhoto(photo) }
    }

    // ---- Optional Offline Models (Consent Required) ----

    fun isLocalModelDownloaded(item: OfflineModelCatalogItem): Boolean =
        localModelManager.isModelDownloaded(item)

    fun downloadLocalModelWithConsent(item: OfflineModelCatalogItem) {
        viewModelScope.launch {
            localModelManager.downloadModelWithConsent(item)
        }
    }

    fun deleteLocalModel(item: OfflineModelCatalogItem) {
        localModelManager.deleteModel(item)
    }

    fun cancelLocalModelDownload() {
        localModelManager.cancelDownload()
    }

    // ---- History & Memory ----

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun deleteHistoryMessage(id: Long) {
        viewModelScope.launch { repository.deleteMessage(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { repository.clearAllMessages() }
    }

    fun addManualMemory(fact: String) {
        viewModelScope.launch {
            repository.saveMemoryFact(fact, category = "MANUAL", pinned = true)
        }
    }

    fun bookmarkMessageAsMemory(message: ConversationMessageEntity) {
        viewModelScope.launch {
            repository.saveMemoryFact(message.content.take(240), category = "BOOKMARKED")
            _statusBanner.value = "Saved message to persistent memory."
        }
    }

    fun editMemory(id: Long, newText: String) {
        viewModelScope.launch {
            repository.updateMemoryText(id, newText)
            _statusBanner.value = "Memory updated."
        }
    }

    fun toggleMemoryPin(memory: MemoryFactEntity) {
        viewModelScope.launch { repository.togglePinMemory(memory.id, memory.isPinned) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun deleteSelectedMemories(ids: List<Long>) {
        viewModelScope.launch {
            repository.deleteMemoriesByIds(ids)
            _statusBanner.value = "Deleted ${ids.size} selected memory item(s)."
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch { repository.clearAllMemories() }
    }

    fun updateMemoryPreferences(autoSave: Boolean? = null, useContext: Boolean? = null) {
        viewModelScope.launch { settingsRepository.updateMemorySettings(autoSave, useContext) }
    }

    fun wipeAllPrivateData() {
        stopAllVoiceActivity()
        WakeWordForegroundService.stop(context)
        viewModelScope.launch {
            repository.wipeAllUserData()
            _connectionStatus.value = ConnectionStatus.NotConfigured
            _statusBanner.value = "All conversations, memories, and encrypted keys wiped."
        }
    }

    fun isHardwareKeystoreActive(): Boolean = secureKeyStore.isHardwareKeystoreActive()

    private suspend fun settingsFlowValue(): AishuSettings = repository.settingsFlow.first()

    override fun onCleared() {
        speechManager.stopListening()
        ttsEngine.shutdown()
        customVoiceRecorder.stopPlayback()
        networkMonitor.unregister()
        authManager.cleanup()
        super.onCleared()
    }
}
