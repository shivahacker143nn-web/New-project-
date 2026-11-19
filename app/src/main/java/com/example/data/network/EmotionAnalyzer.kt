package com.example.data.network

import com.example.data.model.EmotionStyle

object EmotionAnalyzer {

    fun detectAdaptiveEmotion(userText: String, fallback: EmotionStyle): EmotionStyle {
        val lower = userText.lowercase()
        return when {
            containsAny(
                lower,
                "sad", "depressed", "lonely", "upset", "hurt", "heartbroken",
                "బాధ", "విచారం", "ఒంటరి"
            ) -> EmotionStyle.SAD

            containsAny(
                lower,
                "stressed", "anxious", "worried", "tired", "cry", "ఆందోళన"
            ) -> EmotionStyle.SUPPORTIVE

            containsAny(
                lower,
                "sorry", "tough day", "failed", "lost", "scared", "pain", "కష్టం", "భయం"
            ) -> EmotionStyle.EMPATHETIC

            containsAny(
                lower,
                "awesome", "amazing", "won", "celebrate", "excited", "yay", "woohoo", "great news",
                "సూపర్", "అద్భుతం", "ఆనందం", "సంతోషం"
            ) -> EmotionStyle.EXCITED

            containsAny(
                lower,
                "joke", "funny", "haha", "lol", "laugh", "smile", "happy", "తమాషా", "నవ్వు"
            ) -> EmotionStyle.HAPPY

            containsAny(
                lower,
                "security", "exploit", "error", "crash", "legal", "medical", "emergency",
                "urgent", "critical", "budget", "contract", "అత్యవసరం", "సమస్య"
            ) -> EmotionStyle.SERIOUS

            containsAny(
                lower,
                "why", "how does", "explain", "curious", "wonder", "what if", "ఎలా", "ఎందుకు"
            ) -> EmotionStyle.CURIOUS

            containsAny(
                lower,
                "report", "schedule", "meeting", "email", "professional", "summary", "plan"
            ) -> EmotionStyle.PROFESSIONAL

            containsAny(
                lower,
                "calm", "breathe", "relax", "sleep", "meditate", "peace", "ప్రశాంతం"
            ) -> EmotionStyle.CALM

            containsAny(
                lower,
                "thank", "care", "help me", "family", "health", "ధన్యవాదాలు"
            ) -> EmotionStyle.CARING

            else -> fallback
        }
    }

    /**
     * Extracts explicit memory statements such as:
     * "Remember that my favorite color is red" or "My name is Kiran" or Telugu "గుర్తుంచుకో ..."
     */
    fun extractExplicitMemory(userText: String): String? {
        val trimmed = userText.trim()
        val lower = trimmed.lowercase()

        val prefixes = listOf(
            "remember that ",
            "remember ",
            "please remember ",
            "don't forget that ",
            "save to memory: ",
            "note that ",
            "గుర్తుంచుకో ",
            "ఇది గుర్తుపెట్టుకో ",
            "నా పేరు "
        )
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val extracted = if (prefix == "నా పేరు ") trimmed else trimmed.substring(prefix.length).trim()
                if (extracted.length >= 3) return extracted
            }
        }

        val myInfoRegex = Regex(
            """^(my name is|i am called|i live in|i work as|my favorite|call me)\s+(.{2,100})$""",
            RegexOption.IGNORE_CASE
        )
        val match = myInfoRegex.find(trimmed)
        if (match != null) {
            return trimmed
        }

        return null
    }

    fun containsTeluguScript(text: String): Boolean {
        return text.any { ch -> ch in '\u0C00'..'\u0C7F' }
    }

    fun containsTeluguHints(text: String): Boolean {
        if (containsTeluguScript(text)) return true
        val lower = text.lowercase()
        val romanizedTokens = listOf(
            "namaskaram", "ela unnaru", "bagunnara", "bagunna", "cheppandi",
            "kavali", "telugu", "emiti", "enti", "nenu", "meeru", "manchi", "dhanyavadalu"
        )
        return romanizedTokens.any { lower.contains(it) }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}
