package com.example.data.network

import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.model.AppLanguage
import com.example.data.model.EmotionStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local Offline Assistant Engine used when internet is unavailable or Offline Mode is forced.
 * Operates strictly on local Room memories, conversation history, and deterministic local logic.
 * Never claims cloud-level AI or live web search while offline.
 */
class OfflineAssistantEngine {

    fun generateOfflineResponse(
        userQuery: String,
        memories: List<MemoryFactEntity>,
        recentHistory: List<ConversationMessageEntity>,
        emotion: EmotionStyle,
        language: AppLanguage,
        justSavedMemory: String?,
        assistantName: String = "AI SHU",
        userName: String = ""
    ): String {
        val trimmed = userQuery.trim()
        val lower = trimmed.lowercase()
        val isTelugu = language == AppLanguage.TELUGU ||
            (language == AppLanguage.BILINGUAL && EmotionAnalyzer.containsTeluguHints(trimmed))
        val userGreetingPrefix = if (userName.isNotBlank()) "$userName, " else ""

        if (justSavedMemory != null) {
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] మీ సమాచారాన్ని స్థానిక మెమరీలో భద్రపరిచాను: \"$justSavedMemory\"."
            } else {
                "[Offline Mode] Stored in your local encrypted vault memory: \"$justSavedMemory\"."
            }
        }

        // Who am I / Assistant name
        if (containsAny(lower, "what is my name", "who am i", "నా పేరు ఏమిటి")) {
            val savedName = if (userName.isNotBlank()) userName else memories.firstOrNull {
                it.factText.contains("name is", ignoreCase = true)
            }?.factText
            return if (savedName != null) {
                if (isTelugu) "[ఆఫ్‌లైన్ మోడ్] మీ పేరు: $savedName." else "[Offline Mode] Your name is $savedName."
            } else {
                if (isTelugu) "[ఆఫ్‌లైన్ మోడ్] మీ పేరు ఇంకా సేవ్ చేయబడలేదు." else "[Offline Mode] Your name isn't saved yet. Set it in Custom Settings or say \"My name is ...\"."
            }
        }

        // Time & date
        if (containsAny(lower, "time", "clock", "సమయం", "టైమ్")) {
            val now = SimpleDateFormat("hh:mm a, EEE MMM dd", Locale.getDefault()).format(Date())
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] ప్రస్తుత సమయం మరియు తేదీ: $now."
            } else {
                "[Offline Mode] Current local time and date: $now."
            }
        }

        if (containsAny(lower, "date", "today", "తేదీ", "ఈరోజు")) {
            val today = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] ఈరోజు తేదీ: $today."
            } else {
                "[Offline Mode] Today's date is $today."
            }
        }

        // Memory recall queries
        if (containsAny(
                lower,
                "remember", "memory", "what do you know", "my name", "saved",
                "గుర్తుందా", "మెమరీ", "నా పేరు"
            )
        ) {
            if (memories.isEmpty()) {
                return if (isTelugu) {
                    "[ఆఫ్‌లైన్ మోడ్] మీ స్థానిక మెమరీలో ఇంకా సమాచారం సేవ్ చేయబడలేదు. \"గుర్తుంచుకో ...\" అని చెప్పి సేవ్ చేయండి."
                } else {
                    "[Offline Mode] You have no saved memory facts yet. Say \"Remember that ...\" to store personal notes locally."
                }
            }
            val matched = findRelevantMemories(trimmed, memories)
            val listToUse = matched.ifEmpty { memories.take(5) }
            val formatted = listToUse.joinToString("\n") { "• ${it.factText}" }
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] మీ సేవ్ చేసిన మెమరీ వివరాలు:\n$formatted"
            } else {
                "[Offline Mode] Here is what I found in your local memory:\n$formatted"
            }
        }

        // Conversation history recall
        if (containsAny(lower, "history", "earlier", "last message", "previous", "ఇంతకు ముందు")) {
            val userMsgs = recentHistory.filter { it.role == "USER" && it.content != trimmed }.takeLast(3)
            if (userMsgs.isEmpty()) {
                return if (isTelugu) {
                    "[ఆఫ్‌లైన్ మోడ్] ఇంతకు ముందు సంభాషణ చరిత్ర ఇంకా నమోదు కాలేదు."
                } else {
                    "[Offline Mode] No earlier conversation messages were found in local history."
                }
            }
            val summary = userMsgs.joinToString("\n") { "• \"${it.content.take(90)}\"" }
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] మీ ఇటీవలి ప్రశ్నలు:\n$summary"
            } else {
                "[Offline Mode] Your recent locally saved prompts:\n$summary"
            }
        }

        // Simple arithmetic evaluation e.g. "calculate 25 * 4" or "12 + 8"
        val mathResult = tryEvaluateSimpleMath(lower)
        if (mathResult != null) {
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] గణన ఫలితం: $mathResult."
            } else {
                "[Offline Mode] Local calculation result: $mathResult."
            }
        }

        // Greetings
        if (containsAny(lower, "hello", "hi", "hey", "namaste", "namaskaram", "నమస్కారం", "హలో", "హాయ్")) {
            val toneLine = when (emotion) {
                EmotionStyle.EXCITED, EmotionStyle.HAPPY ->
                    if (isTelugu) "${userGreetingPrefix}మిమ్మల్ని మళ్ళీ కలవడం చాలా ఆనందంగా ఉంది!" else "Great to hear from you, ${userName.ifBlank { "friend" }}! $assistantName is primed and ready."
                EmotionStyle.SAD, EmotionStyle.SUPPORTIVE, EmotionStyle.EMPATHETIC, EmotionStyle.CARING ->
                    if (isTelugu) "${userGreetingPrefix}నేను మీకు సహాయం చేయడానికి ఇక్కడ ఉన్నాను." else "I'm right here with you${if (userName.isNotBlank()) ", $userName" else ""}. How can I help right now?"
                EmotionStyle.SERIOUS, EmotionStyle.PROFESSIONAL ->
                    if (isTelugu) "${userGreetingPrefix}సిస్టమ్ సిద్ధంగా ఉంది. మీ ఆదేశం చెప్పండి." else "$assistantName offline core ready. State your request."
                else ->
                    if (isTelugu) "నమస్కారం${if (userName.isNotBlank()) " $userName గారు" else ""}! నేను $assistantName. ప్రస్తుతం ఆఫ్‌లైన్ మోడ్‌లో ఉన్నాను." else "Hello${if (userName.isNotBlank()) " $userName" else ""}! I'm $assistantName, currently operating in Offline Mode."
            }
            return "[Offline Mode] $toneLine"
        }

        // Check if saved memories or history contain overlapping keywords
        val keywordMatches = findRelevantMemories(trimmed, memories)
        if (keywordMatches.isNotEmpty()) {
            val top = keywordMatches.take(3).joinToString("; ") { it.factText }
            return if (isTelugu) {
                "[ఆఫ్‌లైన్ మోడ్] మీ స్థానిక మెమరీలో సంబంధించిన వివరాలు ఉన్నాయి: $top. పూర్తి AI విశ్లేషణ కోసం ఆన్‌లైన్ కనెక్షన్ అవసరం."
            } else {
                "[Offline Mode] From your locally saved memory: $top. Note: Deeper reasoning or live web knowledge requires an active internet connection and configured AI provider."
            }
        }

        // Honest disclosure of offline boundaries
        return if (isTelugu) {
            "[ఆఫ్‌లైన్ మోడ్] ప్రస్తుతం ఇంటర్నెట్ లేదా ఆన్‌లైన్ AI ప్రొవైడర్ అందుబాటులో లేదు. నేను స్థానిక మెమరీ, సంభాషణ చరిత్ర, సమయం/తేదీ మరియు ప్రాథమిక గణనలకు సహాయం చేయగలను. పూర్తి AI సమాధానాల కోసం ఇంటర్నెట్‌కు కనెక్ట్ అవ్వండి లేదా ఆఫ్‌లైన్ లోకల్ సర్వర్‌ను కాన్ఫిగర్ చేయండి."
        } else {
            "[Offline Mode] I am currently offline and cannot reach Google Search or cloud AI. Locally, I can recall your saved memories, search conversation history, check time/date, and run basic calculations. Connect to the internet (or point Custom Endpoint to a local llama.cpp/Ollama server) for full LLM answers."
        }
    }

    fun findRelevantMemories(query: String, memories: List<MemoryFactEntity>): List<MemoryFactEntity> {
        val tokens = query.lowercase()
            .split(Regex("[\\s,?.!]+"))
            .filter { it.length >= 3 }
            .toSet()
        if (tokens.isEmpty()) return memories.filter { it.isPinned }.take(4)

        return memories
            .map { mem ->
                val memLower = mem.factText.lowercase()
                val score = tokens.count { memLower.contains(it) } + if (mem.isPinned) 1 else 0
                mem to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun tryEvaluateSimpleMath(input: String): String? {
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*([+\-*/x])\s*(-?\d+(?:\.\d+)?)""").find(input)
            ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "x" -> a * b
            "/" -> if (b == 0.0) return "undefined (division by zero)" else a / b
            else -> return null
        }
        return if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.4f", res).trimEnd('0').trimEnd('.')
    }

    private fun containsAny(text: String, vararg keys: String): Boolean =
        keys.any { text.contains(it) }
}
