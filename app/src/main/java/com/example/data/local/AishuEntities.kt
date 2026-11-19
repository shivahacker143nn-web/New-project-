package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_messages")
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "USER", "ASSISTANT", "SYSTEM"
    val content: String,
    val emotionTag: String = "FRIENDLY",
    val timestamp: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false,
    val providerLabel: String = "LOCAL",
    val attachedImagePath: String? = null
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val factText: String,
    val category: String = "GENERAL",
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "custom_voice_profiles")
data class CustomVoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val isSelected: Boolean = false,
    val sourceType: String = "RECORDED", // "RECORDED" or "IMPORTED"
    val remoteVoiceId: String = "",
    val replicationStatus: String = "LOCAL_SAMPLE_ONLY", // "REPLICATED", "LOCAL_SAMPLE_ONLY", "ERROR"
    val consentConfirmed: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val formatLabel: String = "M4A",
    val pitchMultiplier: Float = 1.0f,
    val rateMultiplier: Float = 1.0f,
    val warmth: Float = 0.5f,
    val expressiveness: Float = 0.65f,
    val similarityBoost: Float = 0.82f,
    val baseGenderHint: String = "NEUTRAL", // "MALE", "FEMALE", "NEUTRAL"
    val estimatedPitchHz: Float = 0f,
    val sampleRateHz: Int = 44100,
    val clarityScore: Float = 0.78f
)

@Entity(tableName = "assistant_rules")
data class AssistantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val instruction: String,
    val isEnabled: Boolean = true,
    val isProtected: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_photos")
data class SavedPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val label: String = "Photo",
    val lastAnalysis: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
