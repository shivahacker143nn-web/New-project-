package com.example.voice

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object WakeWordMatcher {

    /**
     * Checks whether [transcript] contains the configured [wakeWord].
     * [sensitivity] ranges from 1 (strict exact match) to 10 (tolerant fuzzy match).
     */
    fun matches(transcript: String, wakeWord: String, sensitivity: Int): Boolean {
        val cleanTranscript = normalize(transcript)
        val cleanWake = normalize(wakeWord)
        if (cleanTranscript.isBlank() || cleanWake.isBlank()) return false

        if (cleanTranscript.contains(cleanWake)) return true

        // Common speech-to-text phonetic variants for "AISHU"
        if (cleanWake.contains("aishu")) {
            val variants = listOf("aishu", "ishu", "aish", "ayshu", "ashu", "issue", "aai shu", "ఆయిషు")
            val prefixRequired = cleanWake.startsWith("hey") || cleanWake.startsWith("hi") || cleanWake.startsWith("ok")
            if (prefixRequired) {
                val hasGreeting = listOf("hey", "hi", "ok", "okay", "hello").any { cleanTranscript.contains(it) }
                if (hasGreeting && variants.any { cleanTranscript.contains(it) }) return true
            } else if (variants.any { cleanTranscript.contains(it) }) {
                return true
            }
        }

        if (sensitivity <= 3) return false

        val wakeWords = cleanWake.split(" ").filter { it.isNotBlank() }
        val transcriptWords = cleanTranscript.split(" ").filter { it.isNotBlank() }
        if (transcriptWords.size < wakeWords.size) return false

        val maxAllowedDistance = when {
            sensitivity >= 9 -> max(2, cleanWake.length / 3)
            sensitivity >= 6 -> max(1, cleanWake.length / 4)
            else -> 1
        }

        for (i in 0..(transcriptWords.size - wakeWords.size)) {
            val window = transcriptWords.subList(i, i + wakeWords.size).joinToString(" ")
            if (abs(window.length - cleanWake.length) <= maxAllowedDistance + 1) {
                if (levenshtein(window, cleanWake) <= maxAllowedDistance) {
                    return true
                }
            }
        }
        return false
    }

    private fun normalize(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\u0C00-\\u0C7F\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
