package com.example.data.model

enum class AiProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val authHeaderHint: String,
    val purpose: String,
    val isRequired: Boolean
) {
    FIREBASE_GEMINI(
        displayName = "Google Gemini (Firebase AI Logic + Search)",
        defaultEndpoint = "Firebase AI Logic (App Check Protected)",
        defaultModel = "gemini-3.8-flash",
        authHeaderHint = "Configured automatically via Firebase App Check & google-services.json",
        purpose = "Primary online intelligence, English & Telugu reasoning, live Google Search grounding, and Gemini Vision.",
        isRequired = true
    )
}

enum class EmotionStyle(
    val displayName: String,
    val systemPromptDirective: String,
    val badgeCode: String
) {
    HAPPY("Happy", "Use an upbeat, joyful, energetic conversational tone.", "HAP"),
    SAD("Sad / Gentle", "Use a quiet, gentle, comforting, low-key conversational tone when discussing difficult topics.", "SAD"),
    CALM("Calm", "Use a composed, soothing, unhurried tone.", "CLM"),
    EXCITED("Excited", "Use an enthusiastic, lively, high-energy tone.", "EXC"),
    SERIOUS("Serious", "Use a direct, focused, concise, and serious tone.", "SER"),
    FRIENDLY("Friendly", "Use a warm, approachable, conversational tone.", "FRD"),
    EMPATHETIC("Empathetic", "Use an understanding, sensitive, validating tone without claiming human consciousness or real biological feelings.", "EMP"),
    SUPPORTIVE("Supportive", "Use an encouraging, reassuring, helpful tone.", "SUP"),
    PROFESSIONAL("Professional", "Use an articulate, structured, executive-grade tone.", "PRO"),
    CARING("Caring", "Use an attentive, considerate, thoughtful tone.", "CAR"),
    CURIOUS("Curious", "Use an inquisitive, thoughtful, exploratory tone.", "CUR")
}

enum class SpeakingStyle(
    val displayName: String,
    val pitchMultiplier: Float,
    val rateMultiplier: Float
) {
    NATURAL_HUMAN("Natural Human Voice", 1.00f, 1.00f),
    ROBOTIC("Robotic Voice", 0.80f, 0.94f),
    FRIENDLY("Friendly Voice", 1.05f, 1.02f),
    PROFESSIONAL("Professional Voice", 0.95f, 0.98f),
    CALM("Calm Voice", 0.96f, 0.89f),
    EMOTIONAL("Emotional Voice", 1.08f, 0.96f),
    CUSTOM("Custom Voice", 1.00f, 1.00f)
}

enum class ResponseLength(val displayName: String) {
    CONCISE("Concise (Quick Voice)"),
    BALANCED("Balanced"),
    DETAILED("Detailed")
}

enum class VoiceGenderHint { MALE, FEMALE, NEUTRAL }

data class VoiceOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val genderHint: VoiceGenderHint,
    val basePitchOffset: Float,
    val baseRateOffset: Float
) {
    companion object {
        val ALL_VOICES = listOf(
            VoiceOption("male_1", "Male Voice 1", "Deep Command", VoiceGenderHint.MALE, 0.84f, 0.96f),
            VoiceOption("male_2", "Male Voice 2", "Tactical Crisp", VoiceGenderHint.MALE, 0.92f, 1.05f),
            VoiceOption("male_3", "Male Voice 3", "Warm Baritone", VoiceGenderHint.MALE, 0.88f, 0.92f),
            VoiceOption("male_4", "Male Voice 4", "Cyber Resonance", VoiceGenderHint.MALE, 0.78f, 1.00f),
            VoiceOption("female_1", "Female Voice 1", "Clear Neural", VoiceGenderHint.FEMALE, 1.12f, 1.02f),
            VoiceOption("female_2", "Female Voice 2", "Bright Companion", VoiceGenderHint.FEMALE, 1.20f, 1.06f),
            VoiceOption("female_3", "Female Voice 3", "Velvet Calm", VoiceGenderHint.FEMALE, 1.06f, 0.93f),
            VoiceOption("female_4", "Female Voice 4", "Sharp Analyst", VoiceGenderHint.FEMALE, 1.16f, 1.08f),
            VoiceOption("natural", "Natural Voice", "Balanced Conversational", VoiceGenderHint.NEUTRAL, 1.00f, 1.00f),
            VoiceOption("professional", "Professional Voice", "Executive Precision", VoiceGenderHint.NEUTRAL, 0.97f, 0.98f)
        )

        fun findById(id: String): VoiceOption =
            ALL_VOICES.firstOrNull { it.id == id } ?: ALL_VOICES[8]
    }
}

enum class AppLanguage(
    val displayName: String,
    val speechLocaleTag: String,
    val shortCode: String,
    val promptInstruction: String
) {
    ENGLISH(
        displayName = "English",
        speechLocaleTag = "en-US",
        shortCode = "EN",
        promptInstruction = "Respond clearly in English."
    ),
    TELUGU(
        displayName = "తెలుగు (Telugu)",
        speechLocaleTag = "te-IN",
        shortCode = "TE",
        promptInstruction = "Respond naturally in Telugu script (తెలుగు) using clear conversational Telugu pronunciation."
    ),
    BILINGUAL(
        displayName = "Auto-Detect (EN + తెలుగు)",
        speechLocaleTag = "en-IN",
        shortCode = "AUTO",
        promptInstruction = "Automatically detect whether the user spoke or wrote in Telugu (including Telugu script or Romanized Telugu) or English. Reply in that same language (using natural తెలుగు script for Telugu) unless the user explicitly asks to switch languages."
    )
}

enum class AssistantMode(val displayName: String) {
    MANUAL("Manual Mode"),
    AUTOMATIC("Automatic Mode")
}

sealed class ConnectionStatus {
    data object NotConfigured : ConnectionStatus()
    data object Testing : ConnectionStatus()
    data class Connected(val providerName: String, val model: String, val latencyMs: Long) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

enum class VoiceSessionState {
    IDLE,
    WAKE_WORD_STANDBY,
    LISTENING,
    PROCESSING,
    SPEAKING
}
