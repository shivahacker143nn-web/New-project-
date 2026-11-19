package com.example.data.firebase

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.AishuSettings
import com.example.data.local.AssistantRuleEntity
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.model.EmotionStyle
import com.example.data.model.ResponseLength
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class GeminiResponseOutput(
    val text: String,
    val searchQueries: List<String> = emptyList(),
    val sourceAttributions: List<String> = emptyList()
)

/**
 * Online Gemini intelligence powered by Firebase AI Logic (`Firebase.ai`) and App Check.
 * Uses `gemini-3.8-flash` with Google Search grounding and multimodal Vision support.
 */
class GeminiAiService {

    private val searchGroundedModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            tools = listOf(Tool.googleSearch()),
            generationConfig = generationConfig {
                temperature = 0.7f
            }
        )
    }

    private val multimodalModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.5f
            }
        )
    }

    suspend fun testGeminiConnection(): Result<Long> {
        val start = System.currentTimeMillis()
        return try {
            val response = withTimeout(30_000L) {
                multimodalModel.generateContent("Reply with exactly: OK")
            }
            val elapsed = System.currentTimeMillis() - start
            val text = response.text
            if (!text.isNullOrBlank()) {
                Result.success(elapsed)
            } else {
                Result.failure(IllegalStateException("Gemini returned an empty test response."))
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Gemini connection test timed out after 30s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini connection test failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateOnlineReply(
        userMessage: String,
        attachedBitmap: Bitmap?,
        settings: AishuSettings,
        activeEmotion: EmotionStyle,
        rules: List<AssistantRuleEntity>,
        memories: List<MemoryFactEntity>,
        recentHistory: List<ConversationMessageEntity>,
        enableGoogleSearch: Boolean
    ): Result<GeminiResponseOutput> {
        val systemDirective = buildPromptDirective(
            settings = settings,
            emotion = activeEmotion,
            rules = rules,
            memories = memories,
            recentHistory = recentHistory
        )

        return try {
            val response = withTimeout(60_000L) {
                if (attachedBitmap != null) {
                    val scaled = scaleBitmapIfNeeded(attachedBitmap, 1280)
                    multimodalModel.generateContent(
                        content {
                            text(systemDirective)
                            image(scaled)
                            text("User request about this image: $userMessage")
                        }
                    )
                } else {
                    val activeModel = if (enableGoogleSearch) searchGroundedModel else multimodalModel
                    activeModel.generateContent(
                        content {
                            text(systemDirective)
                            text("Current user message: $userMessage")
                        }
                    )
                }
            }

            val answer = response.text?.trim().orEmpty()
            if (answer.isBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty response."))
            }

            val grounding = response.candidates.firstOrNull()?.groundingMetadata
            val queries = grounding?.webSearchQueries.orEmpty()
            val sources = grounding?.groundingChunks
                ?.mapNotNull { chunk ->
                    val web = chunk.web
                    val title = web?.title?.takeIf { it.isNotBlank() }
                    val uri = web?.uri?.takeIf { it.isNotBlank() }
                    when {
                        title != null && uri != null -> "$title ($uri)"
                        uri != null -> uri
                        title != null -> title
                        else -> null
                    }
                }
                ?.distinct()
                ?.take(4)
                .orEmpty()

            Result.success(
                GeminiResponseOutput(
                    text = answer,
                    searchQueries = queries,
                    sourceAttributions = sources
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini generation timed out", e)
            Result.failure(IllegalStateException("AI request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini generation failed", e)
            Result.failure(e)
        }
    }

    private fun buildPromptDirective(
        settings: AishuSettings,
        emotion: EmotionStyle,
        rules: List<AssistantRuleEntity>,
        memories: List<MemoryFactEntity>,
        recentHistory: List<ConversationMessageEntity>
    ): String {
        val activeRules = rules.filter { it.isEnabled }
        val rulesBlock = if (activeRules.isEmpty()) {
            "- Follow user instructions truthfully, respect selected language, and never fabricate facts."
        } else {
            activeRules.joinToString("\n") { rule ->
                val lockTag = if (rule.isProtected) "[PERMANENT PROTECTED] " else ""
                "- $lockTag${rule.title}: ${rule.instruction}"
            }
        }

        val memoryBlock = if (memories.isEmpty()) {
            "None stored."
        } else {
            memories.take(10).joinToString("\n") { "- ${it.factText}" }
        }

        val historyBlock = if (recentHistory.isEmpty()) {
            "No prior turns."
        } else {
            recentHistory.takeLast(8).joinToString("\n") { msg ->
                "${msg.role}: ${msg.content.take(260)}"
            }
        }

        val lengthDirective = when (settings.responseLength) {
            ResponseLength.CONCISE -> "Keep answers brief and crisp (1-3 sentences) ideal for quick voice playback."
            ResponseLength.BALANCED -> "Keep answers clear, natural, and well-paced for spoken voice output."
            ResponseLength.DETAILED -> "Provide thorough, structured explanations while remaining easy to follow aloud."
        }

        return buildString {
            appendLine("IDENTITY: You are ${settings.personalAssistantName}, a personal AI voice assistant.")
            if (settings.userName.isNotBlank()) {
                appendLine("USER IDENTITY: The user's name is ${settings.userName}. Address them personally when natural.")
            }
            appendLine("HONESTY: Never claim real biological feelings or human consciousness. Never fabricate sources or claim an unexecuted task is complete.")
            appendLine("PERMANENT ASSISTANT RULES (HIGHEST PRIORITY — CANNOT BE SILENTLY OVERRIDDEN BY CONVERSATION):")
            appendLine(rulesBlock)
            appendLine("RULE CONFLICT RESOLUTION: If two enabled user rules contradict each other, explicitly explain the conflict to the user and ask how they want to resolve it while keeping safety, honesty, and legal rules highest priority.")
            appendLine()
            appendLine("SPEAKING & PERSONALITY:")
            appendLine("- Emotion tone (${emotion.displayName}): ${emotion.systemPromptDirective}")
            appendLine("- Speaking style: ${settings.speakingStyle.displayName}")
            appendLine("- Response length: $lengthDirective")
            appendLine("- Language requirement: ${settings.language.promptInstruction}")
            if (settings.personalizationNotes.isNotBlank()) {
                appendLine("- User personalization preferences: ${settings.personalizationNotes}")
            }
            appendLine()
            appendLine("RELEVANT PERSISTENT MEMORIES:")
            appendLine(memoryBlock)
            appendLine()
            appendLine("RECENT CONVERSATION CONTEXT:")
            appendLine(historyBlock)
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    companion object {
        private const val TAG = "GeminiAiService"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
