package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AishuSettings
import com.example.data.local.CustomVoiceProfileEntity
import com.example.data.model.AppLanguage
import com.example.data.model.SpeakingStyle
import com.example.data.model.VoiceGenderHint
import com.example.data.model.VoiceOption
import com.example.di.AppContainer
import com.example.voice.CustomVoiceRecorder
import com.example.voice.VoiceAcousticProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class RecordingPhase {
    IDLE,
    RECORDING,
    ANALYZING,
    STAGED,
    SAVED,
    ERROR
}

data class RecordingProgressState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val maxDurationMs: Long = CustomVoiceRecorder.MAX_RECORDING_DURATION_MS,
    val progressFraction: Float = 0f,
    val amplitude: Float = 0f,
    val waveformSamples: List<Float> = emptyList(),
    val activeFilePath: String? = null,
    val voiceStorageDirectory: String = "",
    val totalLocalStorageBytes: Long = 0L,
    val storedSampleCount: Int = 0,
    val errorMessage: String? = null
)

data class StagedVoiceDraft(
    val filePath: String,
    val durationMs: Long,
    val sourceType: String,
    val formatLabel: String = "M4A",
    val fileSizeBytes: Long = 0L,
    val acousticProfile: VoiceAcousticProfile = VoiceAcousticProfile(),
    val similarityBoost: Float = 0.82f
)

/**
 * Dedicated ViewModel for Custom Voice Studio & Text-to-Speech synthesis:
 * - Recording microphone audio & uploading external audio files via SAF
 * - Managing recording progress state & sanitized local filesystem paths (`filesDir/custom_voices`)
 * - Analyzing real vocal characteristics (pitch Hz, warmth, clarity, cadence, timbre)
 * - Tuning, storing & managing custom voice profiles
 * - Synthesizing speech via characteristic-calibrated device TTS or cloud voice cloning
 */
class VoiceSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    private val repository = container.repository
    private val settingsRepository = container.settingsRepository
    private val voiceAnalyzer = container.voiceAnalyzer

    val ttsEngine = container.ttsEngine
    val customVoiceRecorder = container.customVoiceRecorder

    val settings: StateFlow<AishuSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AishuSettings())

    val customVoices: StateFlow<List<CustomVoiceProfileEntity>> = repository.customVoicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // TTS Engine states
    val ttsReady: StateFlow<Boolean> = ttsEngine.isReady
    val ttsSpeaking: StateFlow<Boolean> = ttsEngine.isSpeaking
    val hasEnglishVoice: StateFlow<Boolean> = ttsEngine.hasEnglishVoice
    val hasTeluguVoice: StateFlow<Boolean> = ttsEngine.hasTeluguVoice
    val engineVoiceCount: StateFlow<Int> = ttsEngine.engineVoiceCount
    val activeSynthesisStatus: StateFlow<String> = ttsEngine.activeSynthesisStatus

    // Recorder states
    val isRecording: StateFlow<Boolean> = customVoiceRecorder.isRecording
    val recordingDurationMs: StateFlow<Long> = customVoiceRecorder.recordingDurationMs
    val recordingAmplitude: StateFlow<Float> = customVoiceRecorder.recordingAmplitude
    val waveformSamples: StateFlow<List<Float>> = customVoiceRecorder.waveformSamples
    val currentRecordingFilePath: StateFlow<String?> = customVoiceRecorder.currentOutputFilePath
    val playingFilePath: StateFlow<String?> = customVoiceRecorder.playingFilePath

    private val _stagedDraft = MutableStateFlow<StagedVoiceDraft?>(null)
    val stagedDraft: StateFlow<StagedVoiceDraft?> = _stagedDraft.asStateFlow()

    private val _replacingProfileId = MutableStateFlow<Long?>(null)
    val replacingProfileId: StateFlow<Long?> = _replacingProfileId.asStateFlow()

    private val _isAnalyzingAudio = MutableStateFlow(false)
    val isAnalyzingAudio: StateFlow<Boolean> = _isAnalyzingAudio.asStateFlow()

    private val _recordingPhaseOverride = MutableStateFlow<RecordingPhase?>(null)
    private val _recordingError = MutableStateFlow<String?>(null)
    private val _storageStatsRefreshTrigger = MutableStateFlow(0)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val recordingProgress: StateFlow<RecordingProgressState> = combine(
        isRecording,
        recordingDurationMs,
        recordingAmplitude,
        waveformSamples,
        currentRecordingFilePath,
        _isAnalyzingAudio,
        _stagedDraft,
        _recordingPhaseOverride,
        _recordingError,
        _storageStatsRefreshTrigger
    ) { values ->
        val recording = values[0] as Boolean
        val elapsed = values[1] as Long
        val amp = values[2] as Float
        @Suppress("UNCHECKED_CAST")
        val wave = values[3] as List<Float>
        val activePath = values[4] as String?
        val analyzing = values[5] as Boolean
        val draft = values[6] as StagedVoiceDraft?
        val phaseOverride = values[7] as RecordingPhase?
        val err = values[8] as String?

        val phase = when {
            recording -> RecordingPhase.RECORDING
            analyzing -> RecordingPhase.ANALYZING
            err != null -> RecordingPhase.ERROR
            draft != null -> RecordingPhase.STAGED
            phaseOverride != null -> phaseOverride
            else -> RecordingPhase.IDLE
        }

        val maxMs = CustomVoiceRecorder.MAX_RECORDING_DURATION_MS
        val fraction = if (recording && maxMs > 0L) {
            (elapsed.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val files = customVoiceRecorder.listStoredSampleFiles()
        RecordingProgressState(
            phase = phase,
            isRecording = recording,
            elapsedMs = elapsed,
            maxDurationMs = maxMs,
            progressFraction = fraction,
            amplitude = amp,
            waveformSamples = wave,
            activeFilePath = draft?.filePath ?: activePath,
            voiceStorageDirectory = customVoiceRecorder.voiceStorageDir.absolutePath,
            totalLocalStorageBytes = files.sumOf { it.length() },
            storedSampleCount = files.size,
            errorMessage = err
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RecordingProgressState(
            voiceStorageDirectory = customVoiceRecorder.voiceStorageDir.absolutePath,
            totalLocalStorageBytes = customVoiceRecorder.getTotalStorageBytes(),
            storedSampleCount = customVoiceRecorder.listStoredSampleFiles().size
        )
    )

    fun hasMicrophonePermission(): Boolean = customVoiceRecorder.hasMicrophonePermission()

    fun getLocalStorageDirectory(): File = customVoiceRecorder.voiceStorageDir

    fun verifyLocalSampleExists(profile: CustomVoiceProfileEntity): Boolean =
        customVoiceRecorder.verifySampleExists(profile.filePath)

    fun clearStatusMessage() {
        _statusMessage.value = null
        _recordingError.value = null
    }

    fun startRecording(desiredName: String = "voice") {
        _recordingError.value = null
        _recordingPhaseOverride.value = null
        if (!hasMicrophonePermission()) {
            val msg = "Microphone permission is required to record your custom voice."
            _statusMessage.value = msg
            _recordingError.value = msg
            return
        }
        ttsEngine.stop()
        customVoiceRecorder.startRecording(
            desiredName = desiredName,
            onMaxDurationReached = { stopAndStageRecording() }
        ).onFailure { err ->
            val msg = "Failed to start recording: ${err.localizedMessage ?: "Mic busy"}"
            _statusMessage.value = msg
            _recordingError.value = msg
        }.onSuccess {
            _storageStatsRefreshTrigger.value++
        }
    }

    fun stopAndStageRecording() {
        val result = customVoiceRecorder.stopRecording()
        if (result == null) {
            val msg = "Recording was too short or empty. Please record at least 2 seconds."
            _statusMessage.value = msg
            _recordingError.value = msg
            _storageStatsRefreshTrigger.value++
            return
        }
        val (file, duration) = result
        analyzeAndStageFile(file = file, durationMs = duration, sourceType = "RECORDED")
    }

    fun cancelActiveRecording() {
        customVoiceRecorder.cancelRecording()
        _recordingPhaseOverride.value = RecordingPhase.IDLE
        _recordingError.value = null
        _storageStatsRefreshTrigger.value++
        _statusMessage.value = "Recording cancelled and temporary audio file removed."
    }

    fun importAudioUri(uri: Uri, desiredName: String = "imported") {
        ttsEngine.stop()
        _recordingError.value = null
        _recordingPhaseOverride.value = null
        customVoiceRecorder.importAudioFromUri(uri, desiredName).onSuccess { (file, duration) ->
            analyzeAndStageFile(file = file, durationMs = duration, sourceType = "IMPORTED")
        }.onFailure { err ->
            val msg = "Could not import audio: ${err.localizedMessage ?: "Unsupported file"}"
            _statusMessage.value = msg
            _recordingError.value = msg
        }
    }

    /**
     * Stores an existing local audio file into the app's managed `filesDir/custom_voices` directory,
     * analyzes its vocal characteristics, and persists a [CustomVoiceProfileEntity] in Room.
     */
    suspend fun storeVoiceSampleFile(
        sourceFile: File,
        profileName: String,
        sourceType: String = "IMPORTED",
        consentConfirmed: Boolean,
        selectImmediately: Boolean = true
    ): Result<CustomVoiceProfileEntity> {
        return runCatching {
            val managedFile = customVoiceRecorder.copyIntoManagedStorage(sourceFile, profileName).getOrThrow()
            val acoustic = voiceAnalyzer.analyzeAudioFile(managedFile)
            val id = repository.addCustomVoice(
                name = profileName,
                filePath = managedFile.absolutePath,
                durationMs = acoustic.analyzedDurationMs,
                sourceType = sourceType,
                consentConfirmed = consentConfirmed,
                selectImmediately = selectImmediately,
                pitchMultiplier = acoustic.pitchMultiplier,
                rateMultiplier = acoustic.rateMultiplier,
                warmth = acoustic.warmth,
                expressiveness = acoustic.expressiveness,
                similarityBoost = 0.82f,
                baseGenderHint = acoustic.detectedGenderHint.name,
                estimatedPitchHz = acoustic.estimatedPitchHz,
                sampleRateHz = acoustic.sampleRateHz,
                clarityScore = acoustic.clarityScore
            )
            if (selectImmediately) {
                settingsRepository.updateVoiceSettings(
                    speakingStyle = SpeakingStyle.CUSTOM,
                    useCustomVoice = true
                )
            }
            _recordingPhaseOverride.value = RecordingPhase.SAVED
            _storageStatsRefreshTrigger.value++
            repository.getSelectedCustomVoice()?.takeIf { it.id == id }
                ?: CustomVoiceProfileEntity(
                    id = id,
                    name = profileName.trim().ifEmpty { "Custom Voice" },
                    filePath = managedFile.absolutePath,
                    durationMs = acoustic.analyzedDurationMs,
                    isSelected = selectImmediately,
                    sourceType = sourceType,
                    consentConfirmed = consentConfirmed,
                    fileSizeBytes = managedFile.length(),
                    formatLabel = managedFile.extension.uppercase().ifBlank { "M4A" },
                    pitchMultiplier = acoustic.pitchMultiplier,
                    rateMultiplier = acoustic.rateMultiplier,
                    warmth = acoustic.warmth,
                    expressiveness = acoustic.expressiveness,
                    similarityBoost = 0.82f,
                    baseGenderHint = acoustic.detectedGenderHint.name,
                    estimatedPitchHz = acoustic.estimatedPitchHz,
                    sampleRateHz = acoustic.sampleRateHz,
                    clarityScore = acoustic.clarityScore
                )
        }
    }

    private fun analyzeAndStageFile(file: File, durationMs: Long, sourceType: String) {
        viewModelScope.launch {
            _isAnalyzingAudio.value = true
            _recordingError.value = null
            val acoustic = voiceAnalyzer.analyzeAudioFile(file)
            _isAnalyzingAudio.value = false
            val resolvedDuration = if (durationMs > 0L) durationMs else acoustic.analyzedDurationMs
            _stagedDraft.value = StagedVoiceDraft(
                filePath = file.absolutePath,
                durationMs = resolvedDuration,
                sourceType = sourceType,
                formatLabel = file.extension.uppercase().ifBlank { "M4A" },
                fileSizeBytes = file.length(),
                acousticProfile = acoustic
            )
            _storageStatsRefreshTrigger.value++
            _statusMessage.value =
                "Analyzed ${sourceType.lowercase()} sample (~${acoustic.estimatedPitchHz.toInt()} Hz • ${acoustic.detectedGenderHint.name}). Preview raw audio or test TTS synthesis before saving."
        }
    }

    fun updateStagedCharacteristics(
        pitchMultiplier: Float? = null,
        rateMultiplier: Float? = null,
        warmth: Float? = null,
        expressiveness: Float? = null,
        similarityBoost: Float? = null,
        genderHint: VoiceGenderHint? = null
    ) {
        val current = _stagedDraft.value ?: return
        val ap = current.acousticProfile
        _stagedDraft.value = current.copy(
            similarityBoost = similarityBoost?.coerceIn(0.1f, 1f) ?: current.similarityBoost,
            acousticProfile = ap.copy(
                pitchMultiplier = pitchMultiplier?.coerceIn(0.65f, 1.55f) ?: ap.pitchMultiplier,
                rateMultiplier = rateMultiplier?.coerceIn(0.70f, 1.50f) ?: ap.rateMultiplier,
                warmth = warmth?.coerceIn(0f, 1f) ?: ap.warmth,
                expressiveness = expressiveness?.coerceIn(0f, 1f) ?: ap.expressiveness,
                detectedGenderHint = genderHint ?: ap.detectedGenderHint
            )
        )
    }

    fun togglePreviewStagedRawAudio() {
        val draft = _stagedDraft.value ?: return
        ttsEngine.stop()
        customVoiceRecorder.togglePreview(draft.filePath) { err ->
            _statusMessage.value = err
        }
    }

    fun previewStagedSynthesizedSpeech(samplePhrase: String = "") {
        val draft = _stagedDraft.value ?: return
        customVoiceRecorder.stopPlayback()
        val s = settings.value
        val ap = draft.acousticProfile
        val syntheticProfile = CustomVoiceProfileEntity(
            name = "Staged Draft",
            filePath = draft.filePath,
            durationMs = draft.durationMs,
            pitchMultiplier = ap.pitchMultiplier,
            rateMultiplier = ap.rateMultiplier,
            warmth = ap.warmth,
            expressiveness = ap.expressiveness,
            similarityBoost = draft.similarityBoost,
            baseGenderHint = ap.detectedGenderHint.name,
            estimatedPitchHz = ap.estimatedPitchHz,
            sampleRateHz = ap.sampleRateHz,
            clarityScore = ap.clarityScore
        )
        val phrase = samplePhrase.trim().ifEmpty { defaultPreviewPhrase(s) }
        ttsEngine.speak(
            text = phrase,
            voiceOption = VoiceOption.findById(s.selectedVoiceId),
            speed = s.speechSpeed,
            pitch = s.speechPitch,
            volume = s.speechVolume,
            language = s.language,
            speakingStyle = SpeakingStyle.CUSTOM,
            useCustomVoice = true,
            customProfile = syntheticProfile,
            onSynthesisNotice = { _statusMessage.value = it }
        )
    }

    fun beginReplacingProfile(profile: CustomVoiceProfileEntity) {
        _replacingProfileId.value = profile.id
        _statusMessage.value = "Replacing audio sample for \"${profile.name}\". Record a new voice sample or import an audio file above."
    }

    fun cancelReplacingProfile() {
        _replacingProfileId.value = null
    }

    fun discardStagedVoice() {
        val draft = _stagedDraft.value ?: return
        customVoiceRecorder.deleteFile(draft.filePath)
        _stagedDraft.value = null
        _replacingProfileId.value = null
        _recordingPhaseOverride.value = RecordingPhase.IDLE
        _storageStatsRefreshTrigger.value++
        _statusMessage.value = "Staged voice draft discarded and local temp file removed."
    }

    fun commitStagedVoice(name: String, consentConfirmed: Boolean) {
        val draft = _stagedDraft.value ?: return
        if (!consentConfirmed) {
            _statusMessage.value = "Please confirm explicit voice authorization before saving a custom voice profile."
            return
        }
        val ap = draft.acousticProfile
        val replaceTargetId = _replacingProfileId.value
        viewModelScope.launch {
            if (replaceTargetId != null) {
                val previous = customVoices.value.firstOrNull { it.id == replaceTargetId }
                repository.replaceCustomVoiceSample(
                    id = replaceTargetId,
                    filePath = draft.filePath,
                    durationMs = draft.durationMs,
                    sourceType = draft.sourceType,
                    pitchMultiplier = ap.pitchMultiplier,
                    rateMultiplier = ap.rateMultiplier,
                    warmth = ap.warmth,
                    expressiveness = ap.expressiveness,
                    baseGenderHint = ap.detectedGenderHint.name,
                    estimatedPitchHz = ap.estimatedPitchHz,
                    sampleRateHz = ap.sampleRateHz,
                    clarityScore = ap.clarityScore
                )
                if (previous != null && previous.filePath != draft.filePath) {
                    customVoiceRecorder.deleteFile(previous.filePath)
                }
                _replacingProfileId.value = null
                _stagedDraft.value = null
                _recordingPhaseOverride.value = RecordingPhase.SAVED
                _storageStatsRefreshTrigger.value++
                _statusMessage.value = "Replaced sample for \"${previous?.name ?: name}\" cleanly."
            } else {
                repository.addCustomVoice(
                    name = name,
                    filePath = draft.filePath,
                    durationMs = draft.durationMs,
                    sourceType = draft.sourceType,
                    consentConfirmed = consentConfirmed,
                    selectImmediately = true,
                    pitchMultiplier = ap.pitchMultiplier,
                    rateMultiplier = ap.rateMultiplier,
                    warmth = ap.warmth,
                    expressiveness = ap.expressiveness,
                    similarityBoost = draft.similarityBoost,
                    baseGenderHint = ap.detectedGenderHint.name,
                    estimatedPitchHz = ap.estimatedPitchHz,
                    sampleRateHz = ap.sampleRateHz,
                    clarityScore = ap.clarityScore
                )
                settingsRepository.updateVoiceSettings(
                    speakingStyle = SpeakingStyle.CUSTOM,
                    useCustomVoice = true
                )
                _stagedDraft.value = null
                _recordingPhaseOverride.value = RecordingPhase.SAVED
                _storageStatsRefreshTrigger.value++
                _statusMessage.value = "Custom voice \"$name\" saved at ${File(draft.filePath).name} & activated."
            }
        }
    }

    fun pruneOrphanedLocalSamples() {
        viewModelScope.launch {
            val activePaths = buildSet {
                customVoices.value.forEach { add(it.filePath) }
                _stagedDraft.value?.filePath?.let { add(it) }
                currentRecordingFilePath.value?.let { add(it) }
            }
            var removed = 0
            customVoiceRecorder.listStoredSampleFiles().forEach { file ->
                if (file.absolutePath !in activePaths && file.delete()) {
                    removed++
                }
            }
            _storageStatsRefreshTrigger.value++
            _statusMessage.value = if (removed > 0) {
                "Cleaned $removed unreferenced audio file(s) from local storage."
            } else {
                "Local voice storage is clean."
            }
        }
    }

    fun updateSavedProfileCharacteristics(
        profileId: Long,
        pitchMultiplier: Float,
        rateMultiplier: Float,
        warmth: Float,
        expressiveness: Float,
        similarityBoost: Float,
        baseGenderHint: VoiceGenderHint
    ) {
        viewModelScope.launch {
            repository.updateCustomVoiceCharacteristics(
                id = profileId,
                pitchMultiplier = pitchMultiplier,
                rateMultiplier = rateMultiplier,
                warmth = warmth,
                expressiveness = expressiveness,
                similarityBoost = similarityBoost,
                baseGenderHint = baseGenderHint.name
            )
            _statusMessage.value = "Updated custom voice synthesis characteristics."
        }
    }

    fun synthesizeCustomProfileSample(
        profile: CustomVoiceProfileEntity,
        customPhrase: String = ""
    ) {
        customVoiceRecorder.stopPlayback()
        val s = settings.value
        val phrase = customPhrase.trim().ifEmpty { defaultPreviewPhrase(s) }

        ttsEngine.speak(
            text = phrase,
            voiceOption = VoiceOption.findById(s.selectedVoiceId),
            speed = s.speechSpeed,
            pitch = s.speechPitch,
            volume = s.speechVolume,
            language = s.language,
            speakingStyle = s.speakingStyle,
            useCustomVoice = true,
            customProfile = profile,
            onSynthesisNotice = { _statusMessage.value = it }
        )
    }

    fun toggleRawSamplePlayback(profile: CustomVoiceProfileEntity) {
        ttsEngine.stop()
        customVoiceRecorder.togglePreview(profile.filePath) { err ->
            _statusMessage.value = err
        }
    }

    fun selectCustomVoiceProfile(id: Long) {
        viewModelScope.launch {
            repository.selectCustomVoice(id)
            settingsRepository.updateVoiceSettings(speakingStyle = SpeakingStyle.CUSTOM, useCustomVoice = true)
            _statusMessage.value = "Custom voice profile selected as priority assistant voice."
        }
    }

    fun disableCustomVoicePriority() {
        viewModelScope.launch {
            settingsRepository.updateVoiceSettings(
                useCustomVoice = false,
                speakingStyle = SpeakingStyle.NATURAL_HUMAN
            )
            _statusMessage.value = "Switched back to built-in assistant voice."
        }
    }

    fun renameCustomVoice(id: Long, newName: String) {
        viewModelScope.launch { repository.renameCustomVoice(id, newName) }
    }

    fun deleteCustomVoice(profile: CustomVoiceProfileEntity) {
        customVoiceRecorder.deleteFile(profile.filePath)
        viewModelScope.launch {
            repository.deleteCustomVoice(profile.id)
            _storageStatsRefreshTrigger.value++
        }
    }

    fun selectBuiltInVoice(voiceId: String) {
        viewModelScope.launch {
            settingsRepository.updateVoiceSettings(voiceId = voiceId, useCustomVoice = false)
        }
    }

    fun previewBuiltInVoice(option: VoiceOption, customPhrase: String = "") {
        customVoiceRecorder.stopPlayback()
        val s = settings.value
        val phrase = customPhrase.trim().ifEmpty {
            if (s.language == AppLanguage.TELUGU) {
                "నమస్కారం, నేను ${s.personalAssistantName}. ఇది ${option.displayName} ప్రివ్యూ."
            } else {
                "System online. This is ${s.personalAssistantName} speaking with ${option.displayName}."
            }
        }
        ttsEngine.speak(
            text = phrase,
            voiceOption = option,
            speed = s.speechSpeed,
            pitch = s.speechPitch,
            volume = s.speechVolume,
            language = s.language,
            speakingStyle = s.speakingStyle
        )
    }

    fun updateGlobalVoiceTuning(
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

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    fun stopSynthesis() {
        ttsEngine.stop()
        customVoiceRecorder.stopPlayback()
    }

    private fun defaultPreviewPhrase(s: AishuSettings): String {
        return if (s.language == AppLanguage.TELUGU) {
            "నమస్కారం! నేను ${s.personalAssistantName}. మీ కస్టమ్ వాయిస్ లక్షణాలతో మాట్లాడుతున్నాను."
        } else {
            "Hello! This is ${s.personalAssistantName} synthesizing speech calibrated to your custom voice characteristics."
        }
    }
}
