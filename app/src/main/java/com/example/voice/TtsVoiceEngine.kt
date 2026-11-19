package com.example.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.local.CustomVoiceProfileEntity
import com.example.data.model.AppLanguage
import com.example.data.model.SpeakingStyle
import com.example.data.model.VoiceGenderHint
import com.example.data.model.VoiceOption
import com.example.data.network.EmotionAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class EffectiveSynthesisParams(
    val effectivePitch: Float,
    val effectiveRate: Float,
    val genderHint: VoiceGenderHint,
    val warmth: Float,
    val expressiveness: Float,
    val stability: Float,
    val similarityBoost: Float,
    val usesCustomCharacteristics: Boolean
)

/**
 * Purely on-device Android Text-to-Speech synthesis engine supporting bilingual English & Telugu
 * output plus local acoustic characteristic calibration from saved custom voice profiles.
 */
class TtsVoiceEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var onCompleteCallback: (() -> Unit)? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _hasTeluguVoice = MutableStateFlow(false)
    val hasTeluguVoice: StateFlow<Boolean> = _hasTeluguVoice.asStateFlow()

    private val _hasEnglishVoice = MutableStateFlow(false)
    val hasEnglishVoice: StateFlow<Boolean> = _hasEnglishVoice.asStateFlow()

    private val _engineVoiceCount = MutableStateFlow(0)
    val engineVoiceCount: StateFlow<Int> = _engineVoiceCount.asStateFlow()

    private val _activeSynthesisStatus = MutableStateFlow("Device Text-to-Speech Ready")
    val activeSynthesisStatus: StateFlow<String> = _activeSynthesisStatus.asStateFlow()

    private var cachedVoices: List<Voice> = emptyList()

    @Volatile
    private var pendingFinalUtteranceId: String = ""

    init {
        mainHandler.post {
            runCatching {
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        configureListener()
                        inspectInstalledVoices()
                        _isReady.value = true
                    } else {
                        _isReady.value = false
                    }
                }
            }
        }
    }

    private fun configureListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == pendingFinalUtteranceId) {
                    _isSpeaking.value = false
                    val cb = onCompleteCallback
                    onCompleteCallback = null
                    mainHandler.post { cb?.invoke() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                val cb = onCompleteCallback
                onCompleteCallback = null
                mainHandler.post { cb?.invoke() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                val cb = onCompleteCallback
                onCompleteCallback = null
                mainHandler.post { cb?.invoke() }
            }
        })
    }

    private fun inspectInstalledVoices() {
        val engine = tts ?: return
        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        cachedVoices = voices
        _engineVoiceCount.value = voices.size
        _hasTeluguVoice.value = voices.any { it.locale.language == "te" } ||
            engine.isLanguageAvailable(Locale("te", "IN")) >= TextToSpeech.LANG_AVAILABLE
        _hasEnglishVoice.value = voices.any { it.locale.language == "en" } ||
            engine.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE
    }

    fun getVoiceAvailabilitySummary(option: VoiceOption, language: AppLanguage): String {
        val matched = matchDeviceVoice(option.genderHint, option.id, language, warmth = 0.5f)
        return if (matched != null) {
            "Engine: ${matched.name.take(26)} (${matched.locale.toLanguageTag()})"
        } else {
            "Engine: Device default TTS + calibrated profile"
        }
    }

    fun speak(
        text: String,
        voiceOption: VoiceOption,
        speed: Float,
        pitch: Float,
        volume: Float,
        language: AppLanguage,
        speakingStyle: SpeakingStyle = SpeakingStyle.NATURAL_HUMAN,
        useCustomVoice: Boolean = false,
        customProfile: CustomVoiceProfileEntity? = null,
        onSynthesisNotice: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            onComplete()
            return
        }

        stop()

        val wantsCustomVoice = useCustomVoice || speakingStyle == SpeakingStyle.CUSTOM || customProfile != null
        val params = computeEffectiveSynthesisParams(
            baseSpeed = speed,
            basePitch = pitch,
            baseOption = voiceOption,
            speakingStyle = speakingStyle,
            customProfile = if (wantsCustomVoice) customProfile else null
        )

        if (wantsCustomVoice && customProfile != null) {
            val pitchHzInfo = if (customProfile.estimatedPitchHz > 0f) {
                " ~${customProfile.estimatedPitchHz.toInt()} Hz"
            } else {
                ""
            }
            _activeSynthesisStatus.value =
                "On-Device Acoustic Profile (${customProfile.name}$pitchHzInfo • Pitch ${"%.2f".format(params.effectivePitch)}x • Warmth ${(params.warmth * 100).toInt()}%)"
            mainHandler.post {
                onSynthesisNotice(
                    "Speaking on-device using calibrated characteristics from \"${customProfile.name}\" (Pitch ${"%.2f".format(params.effectivePitch)}x, Rate ${"%.2f".format(params.effectiveRate)}x)."
                )
            }
        } else {
            _activeSynthesisStatus.value = "Device TTS (${voiceOption.displayName} • ${speakingStyle.displayName})"
        }

        speakWithDeviceTts(
            text = cleanText,
            voiceId = voiceOption.id,
            params = params,
            volume = volume,
            language = language,
            profileLabel = if (wantsCustomVoice) customProfile?.name else null,
            onComplete = onComplete
        )
    }

    private fun speakWithDeviceTts(
        text: String,
        voiceId: String,
        params: EffectiveSynthesisParams,
        volume: Float,
        language: AppLanguage,
        profileLabel: String?,
        onComplete: () -> Unit
    ) {
        mainHandler.post {
            val engine = tts
            if (engine == null || !_isReady.value) {
                _isSpeaking.value = false
                onComplete()
                return@post
            }

            val useTelugu = language == AppLanguage.TELUGU ||
                (language == AppLanguage.BILINGUAL && EmotionAnalyzer.containsTeluguScript(text))
            val targetLocale = if (useTelugu) Locale("te", "IN") else Locale.US
            engine.language = targetLocale

            val matchedVoice = matchDeviceVoice(
                genderHint = params.genderHint,
                optionId = voiceId,
                language = if (useTelugu) AppLanguage.TELUGU else AppLanguage.ENGLISH,
                warmth = params.warmth
            )
            if (matchedVoice != null) {
                runCatching { engine.voice = matchedVoice }
            }

            if (profileLabel != null) {
                _activeSynthesisStatus.value =
                    "Custom Acoustic TTS ($profileLabel • Pitch ${"%.2f".format(params.effectivePitch)}x • ${params.genderHint.name})"
            }

            onCompleteCallback = onComplete
            val bundle = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
            }

            val clauses = if (params.expressiveness >= 0.68f && text.length > 50) {
                splitIntoClauses(text)
            } else {
                listOf(text)
            }

            _isSpeaking.value = true
            var anyFailed = false
            clauses.forEachIndexed { idx, clause ->
                val isFirst = idx == 0
                val isLast = idx == clauses.lastIndex
                val utteranceId = UUID.randomUUID().toString()
                if (isLast) {
                    pendingFinalUtteranceId = utteranceId
                }

                val inflectionDelta = when {
                    clause.endsWith("?") -> 1.06f
                    clause.endsWith("!") -> 1.04f
                    idx % 2 == 1 && params.expressiveness > 0.72f -> 0.97f
                    else -> 1.0f
                }
                engine.setPitch((params.effectivePitch * inflectionDelta).coerceIn(0.55f, 1.95f))
                engine.setSpeechRate(params.effectiveRate.coerceIn(0.55f, 1.95f))

                val queueMode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val res = engine.speak(clause, queueMode, bundle, utteranceId)
                if (res == TextToSpeech.ERROR) {
                    anyFailed = true
                }
                if (!isLast && params.expressiveness >= 0.68f) {
                    engine.playSilentUtterance(110L, TextToSpeech.QUEUE_ADD, "pause_$utteranceId")
                }
            }

            if (anyFailed) {
                _isSpeaking.value = false
                onCompleteCallback = null
                onComplete()
            }
        }
    }

    private fun splitIntoClauses(text: String): List<String> {
        val parts = text.split(Regex("(?<=[.!?।])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parts.isEmpty()) listOf(text) else parts
    }

    private fun matchDeviceVoice(
        genderHint: VoiceGenderHint,
        optionId: String,
        language: AppLanguage,
        warmth: Float
    ): Voice? {
        if (cachedVoices.isEmpty()) return null
        val langCode = if (language == AppLanguage.TELUGU) "te" else "en"
        val localeMatches = cachedVoices.filter {
            it.locale.language.equals(langCode, ignoreCase = true) && !it.isNetworkConnectionRequired
        }.ifEmpty {
            cachedVoices.filter { it.locale.language.equals(langCode, ignoreCase = true) }
        }
        if (localeMatches.isEmpty()) return null

        val genderKeywords = when (genderHint) {
            VoiceGenderHint.MALE -> listOf("male", "-m-", "_m_", "iom", "iog", "sfg", "tpf", "man")
            VoiceGenderHint.FEMALE -> listOf("female", "-f-", "_f_", "iof", "sfb", "tpd", "woman")
            VoiceGenderHint.NEUTRAL -> emptyList()
        }

        val genderMatched = if (genderKeywords.isNotEmpty()) {
            localeMatches.filter { voice ->
                val lowerName = voice.name.lowercase()
                genderKeywords.any { lowerName.contains(it) }
            }
        } else {
            localeMatches
        }

        val pool = genderMatched.ifEmpty { localeMatches }.sortedBy { it.name }
        val warmthBucket = if (pool.size > 1) ((warmth * (pool.size - 1)).toInt()).coerceIn(0, pool.lastIndex) else 0
        val indexOffset = when (optionId) {
            "male_1", "female_1" -> 0
            "male_2", "female_2" -> 1
            "male_3", "female_3" -> 2
            "male_4", "female_4" -> 3
            else -> warmthBucket
        }
        return pool[indexOffset % pool.size]
    }

    fun stop() {
        onCompleteCallback = null
        pendingFinalUtteranceId = ""
        _isSpeaking.value = false
        mainHandler.post {
            runCatching { tts?.stop() }
        }
    }

    fun shutdown() {
        stop()
        mainHandler.post {
            runCatching { tts?.shutdown() }
            tts = null
        }
    }

    companion object {
        fun computeEffectiveSynthesisParams(
            baseSpeed: Float,
            basePitch: Float,
            baseOption: VoiceOption,
            speakingStyle: SpeakingStyle,
            customProfile: CustomVoiceProfileEntity?
        ): EffectiveSynthesisParams {
            if (customProfile != null) {
                val warmthOffset = 1.05f - (customProfile.warmth.coerceIn(0f, 1f) * 0.10f)
                val resolvedGender = runCatching {
                    VoiceGenderHint.valueOf(customProfile.baseGenderHint)
                }.getOrDefault(VoiceGenderHint.NEUTRAL)

                val effPitch = (basePitch * customProfile.pitchMultiplier * speakingStyle.pitchMultiplier * warmthOffset)
                    .coerceIn(0.55f, 1.85f)
                val effRate = (baseSpeed * customProfile.rateMultiplier * speakingStyle.rateMultiplier)
                    .coerceIn(0.55f, 1.85f)
                val stability = (1.0f - customProfile.expressiveness * 0.72f).coerceIn(0.15f, 0.92f)

                return EffectiveSynthesisParams(
                    effectivePitch = effPitch,
                    effectiveRate = effRate,
                    genderHint = resolvedGender,
                    warmth = customProfile.warmth,
                    expressiveness = customProfile.expressiveness,
                    stability = stability,
                    similarityBoost = customProfile.similarityBoost,
                    usesCustomCharacteristics = true
                )
            }

            return EffectiveSynthesisParams(
                effectivePitch = (basePitch * baseOption.basePitchOffset * speakingStyle.pitchMultiplier).coerceIn(0.55f, 1.85f),
                effectiveRate = (baseSpeed * baseOption.baseRateOffset * speakingStyle.rateMultiplier).coerceIn(0.55f, 1.85f),
                genderHint = baseOption.genderHint,
                warmth = 0.5f,
                expressiveness = 0.5f,
                stability = 0.5f,
                similarityBoost = 0.8f,
                usesCustomCharacteristics = false
            )
        }
    }
}
