package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AishuDao {
    // Conversation Messages
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    fun observeAllMessages(): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationMessageEntity>

    @Query("SELECT * FROM conversation_messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ConversationMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity): Long

    @Query("DELETE FROM conversation_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM conversation_messages")
    suspend fun clearAllMessages()

    // Memory Facts
    @Query("SELECT * FROM memory_facts ORDER BY isPinned DESC, createdAt DESC")
    fun observeAllMemories(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryFactEntity): Long

    @Query("UPDATE memory_facts SET isPinned = :pinned WHERE id = :id")
    suspend fun setMemoryPinned(id: Long, pinned: Boolean)

    @Query("UPDATE memory_facts SET factText = :newText WHERE id = :id")
    suspend fun updateMemoryText(id: Long, newText: String)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_facts WHERE id IN (:ids)")
    suspend fun deleteMemoriesByIds(ids: List<Long>)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllMemories()

    // Custom Voice Profiles
    @Query("SELECT * FROM custom_voice_profiles ORDER BY createdAt DESC")
    fun observeCustomVoices(): Flow<List<CustomVoiceProfileEntity>>

    @Query("SELECT * FROM custom_voice_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedCustomVoice(): CustomVoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomVoice(profile: CustomVoiceProfileEntity): Long

    @Query("UPDATE custom_voice_profiles SET name = :newName WHERE id = :id")
    suspend fun renameCustomVoice(id: Long, newName: String)

    @Query("UPDATE custom_voice_profiles SET remoteVoiceId = :remoteId, replicationStatus = :status, consentConfirmed = :consent WHERE id = :id")
    suspend fun updateVoiceReplication(id: Long, remoteId: String, status: String, consent: Boolean)

    @Query(
        """
        UPDATE custom_voice_profiles
        SET pitchMultiplier = :pitchMultiplier,
            rateMultiplier = :rateMultiplier,
            warmth = :warmth,
            expressiveness = :expressiveness,
            similarityBoost = :similarityBoost,
            baseGenderHint = :baseGenderHint
        WHERE id = :id
        """
    )
    suspend fun updateCustomVoiceCharacteristics(
        id: Long,
        pitchMultiplier: Float,
        rateMultiplier: Float,
        warmth: Float,
        expressiveness: Float,
        similarityBoost: Float,
        baseGenderHint: String
    )

    @Query(
        """
        UPDATE custom_voice_profiles
        SET filePath = :filePath,
            durationMs = :durationMs,
            sourceType = :sourceType,
            fileSizeBytes = :fileSizeBytes,
            formatLabel = :formatLabel,
            pitchMultiplier = :pitchMultiplier,
            rateMultiplier = :rateMultiplier,
            warmth = :warmth,
            expressiveness = :expressiveness,
            baseGenderHint = :baseGenderHint,
            estimatedPitchHz = :estimatedPitchHz,
            sampleRateHz = :sampleRateHz,
            clarityScore = :clarityScore
        WHERE id = :id
        """
    )
    suspend fun replaceCustomVoiceSample(
        id: Long,
        filePath: String,
        durationMs: Long,
        sourceType: String,
        fileSizeBytes: Long,
        formatLabel: String,
        pitchMultiplier: Float,
        rateMultiplier: Float,
        warmth: Float,
        expressiveness: Float,
        baseGenderHint: String,
        estimatedPitchHz: Float,
        sampleRateHz: Int,
        clarityScore: Float
    )

    @Query("UPDATE custom_voice_profiles SET isSelected = 0")
    suspend fun clearCustomVoiceSelection()

    @Query("UPDATE custom_voice_profiles SET isSelected = 1 WHERE id = :id")
    suspend fun selectCustomVoice(id: Long)

    @Query("DELETE FROM custom_voice_profiles WHERE id = :id")
    suspend fun deleteCustomVoiceById(id: Long)

    // Permanent Assistant Rules
    @Query("SELECT * FROM assistant_rules ORDER BY isProtected DESC, createdAt ASC")
    fun observeRules(): Flow<List<AssistantRuleEntity>>

    @Query("SELECT * FROM assistant_rules ORDER BY isProtected DESC, createdAt ASC")
    suspend fun getRulesOnce(): List<AssistantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AssistantRuleEntity): Long

    @Update
    suspend fun updateRule(rule: AssistantRuleEntity)

    @Query("DELETE FROM assistant_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("SELECT COUNT(*) FROM assistant_rules")
    suspend fun getRuleCount(): Int

    // Saved Photos & Vision Analysis
    @Query("SELECT * FROM saved_photos ORDER BY createdAt DESC")
    fun observeSavedPhotos(): Flow<List<SavedPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: SavedPhotoEntity): Long

    @Query("UPDATE saved_photos SET lastAnalysis = :analysis WHERE id = :id")
    suspend fun updatePhotoAnalysis(id: Long, analysis: String)

    @Query("DELETE FROM saved_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)
}
