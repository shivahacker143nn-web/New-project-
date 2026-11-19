package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AssistantRuleEntity
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.model.AppLanguage
import com.example.data.model.EmotionStyle
import com.example.data.model.SpeakingStyle
import com.example.data.model.VoiceOption
import com.example.data.network.EmotionAnalyzer
import com.example.data.network.OfflineAssistantEngine
import com.example.data.repository.AishuRepository
import com.example.ui.screens.settings.SettingsSection
import com.example.voice.WakeWordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aishu", appName)
    }

    @Test
    fun `verify 10 selectable voice options and 7 speaking styles exist`() {
        assertEquals(10, VoiceOption.ALL_VOICES.size)
        assertEquals(7, SpeakingStyle.entries.size)
        assertTrue(SpeakingStyle.entries.contains(SpeakingStyle.CUSTOM))
        assertTrue(SpeakingStyle.entries.contains(SpeakingStyle.NATURAL_HUMAN))
        assertTrue(SpeakingStyle.entries.contains(SpeakingStyle.ROBOTIC))
    }

    @Test
    fun `verify required emotion styles exist including Happy Sad Calm Excited Serious Friendly Empathetic`() {
        assertEquals(11, EmotionStyle.entries.size)
        val names = EmotionStyle.entries.map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("HAPPY", "SAD", "CALM", "EXCITED", "SERIOUS", "FRIENDLY", "EMPATHETIC")))
    }

    @Test
    fun `verify 12 ordered settings sections exist`() {
        val expected = listOf(
            "API Settings",
            "Voice Settings",
            "Account",
            "Rules",
            "Custom Settings",
            "Offline Mode",
            "Memory",
            "Emotions and Speaking Style",
            "Photos and Camera",
            "Permissions",
            "Privacy and Security",
            "About AI SHU"
        )
        assertEquals(expected, SettingsSection.entries.map { it.title })
    }

    @Test
    fun `verify max 30 rules limit and conflict detection`() {
        assertEquals(30, AishuRepository.MAX_RULES)
        val conflictingRules = listOf(
            AssistantRuleEntity(id = 1, title = "Concise Rule", instruction = "Always keep answers brief and short", isEnabled = true),
            AssistantRuleEntity(id = 2, title = "Detailed Rule", instruction = "Always provide long detailed essays", isEnabled = true)
        )
        val conflicts = AishuRepository.detectRuleConflicts(conflictingRules)
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts.first().contains("Response length conflict"))
    }

    @Test
    fun `wake word matcher respects custom wake phrase and sensitivity`() {
        assertTrue(WakeWordMatcher.matches("Hey Aishu open assistant", "Hey AISHU", 7))
        assertTrue(WakeWordMatcher.matches("Hey Siri", "Hey Siri", 5))
        assertFalse(WakeWordMatcher.matches("Good morning friend", "Hey AISHU", 7))
    }

    @Test
    fun `emotion analyzer extracts explicit memory detects sad mood and recognizes telugu hints`() {
        val mem = EmotionAnalyzer.extractExplicitMemory("Remember that my meeting is at 4 PM")
        assertEquals("my meeting is at 4 PM", mem)

        val emotion = EmotionAnalyzer.detectAdaptiveEmotion("I am feeling really sad today", EmotionStyle.FRIENDLY)
        assertEquals(EmotionStyle.SAD, emotion)

        assertTrue(EmotionAnalyzer.containsTeluguHints("నమస్కారం ఎలా ఉన్నారు"))
        assertTrue(EmotionAnalyzer.containsTeluguHints("Namaskaram ela unnaru"))
        assertFalse(EmotionAnalyzer.containsTeluguHints("What is the weather today?"))
    }

    @Test
    fun `offline engine recalls saved memory user name and answers math`() {
        val engine = OfflineAssistantEngine()
        val memories = listOf(MemoryFactEntity(id = 1, factText = "My favorite color is crimson red"))
        val recall = engine.generateOfflineResponse(
            userQuery = "What do you remember about my color?",
            memories = memories,
            recentHistory = emptyList<ConversationMessageEntity>(),
            emotion = EmotionStyle.FRIENDLY,
            language = AppLanguage.ENGLISH,
            justSavedMemory = null,
            assistantName = "Jarvis",
            userName = "Kiran"
        )
        assertTrue(recall.contains("crimson red"))

        val whoAmI = engine.generateOfflineResponse(
            userQuery = "What is my name?",
            memories = memories,
            recentHistory = emptyList(),
            emotion = EmotionStyle.FRIENDLY,
            language = AppLanguage.ENGLISH,
            justSavedMemory = null,
            assistantName = "Jarvis",
            userName = "Kiran"
        )
        assertTrue(whoAmI.contains("Kiran"))

        val math = engine.generateOfflineResponse(
            userQuery = "What is 14 * 5?",
            memories = emptyList(),
            recentHistory = emptyList(),
            emotion = EmotionStyle.PROFESSIONAL,
            language = AppLanguage.ENGLISH,
            justSavedMemory = null
        )
        assertTrue(math.contains("70"))
        assertNotNull(math)
    }

    @Test
    fun `voice characteristics analyzer extracts deep vs bright vocal pitch from pcm samples`() {
        val sampleRate = 16000
        val count = sampleRate // 1 second
        val deepMalePcm = ShortArray(count) { i ->
            (kotlin.math.sin(2.0 * kotlin.math.PI * 110.0 * i / sampleRate) * 14000).toInt().toShort()
        }
        val brightFemalePcm = ShortArray(count) { i ->
            (kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * i / sampleRate) * 14000).toInt().toShort()
        }

        val maleProfile = com.example.voice.VoiceCharacteristicsAnalyzer.analyzePcmSamples(deepMalePcm, sampleRate)
        val femaleProfile = com.example.voice.VoiceCharacteristicsAnalyzer.analyzePcmSamples(brightFemalePcm, sampleRate)

        assertTrue("Expected ~110 Hz, got ${maleProfile.estimatedPitchHz}", maleProfile.estimatedPitchHz in 95f..130f)
        assertEquals(com.example.data.model.VoiceGenderHint.MALE, maleProfile.detectedGenderHint)
        assertTrue("Expected pitchMultiplier < 1.0", maleProfile.pitchMultiplier < 0.90f)

        assertTrue("Expected ~220 Hz, got ${femaleProfile.estimatedPitchHz}", femaleProfile.estimatedPitchHz in 195f..245f)
        assertEquals(com.example.data.model.VoiceGenderHint.FEMALE, femaleProfile.detectedGenderHint)
        assertTrue("Expected pitchMultiplier > 1.1", femaleProfile.pitchMultiplier > 1.15f)
    }

    @Test
    fun `tts engine computes calibrated synthesis parameters from custom voice characteristics`() {
        val customProfile = com.example.data.local.CustomVoiceProfileEntity(
            id = 1L,
            name = "Deep Studio",
            filePath = "/tmp/sample.m4a",
            pitchMultiplier = 0.85f,
            rateMultiplier = 1.10f,
            warmth = 0.80f,
            expressiveness = 0.75f,
            similarityBoost = 0.90f,
            baseGenderHint = "MALE",
            estimatedPitchHz = 115f
        )

        val params = com.example.voice.TtsVoiceEngine.computeEffectiveSynthesisParams(
            baseSpeed = 1.0f,
            basePitch = 1.0f,
            baseOption = VoiceOption.findById("natural"),
            speakingStyle = SpeakingStyle.CUSTOM,
            customProfile = customProfile
        )

        assertTrue(params.usesCustomCharacteristics)
        assertEquals(com.example.data.model.VoiceGenderHint.MALE, params.genderHint)
        assertEquals(0.90f, params.similarityBoost, 0.01f)
        assertEquals(1.10f, params.effectiveRate, 0.01f)
        // Warmth 0.80 yields warmthOffset ~0.97 -> effectivePitch < 0.85
        assertTrue(params.effectivePitch < 0.85f)
        assertTrue(params.stability < 0.55f)
    }

    @Test
    fun `voice settings viewmodel manages local filesystem paths recording progress state and sample storage`() {
        kotlinx.coroutines.runBlocking {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val vm = com.example.ui.VoiceSettingsViewModel(app)

            val initialState = vm.recordingProgress.value
            assertEquals(com.example.ui.RecordingPhase.IDLE, initialState.phase)
            assertFalse(initialState.isRecording)
            assertTrue(initialState.voiceStorageDirectory.endsWith("custom_voices"))

            // Verify filename sanitization protects local filesystem paths
            val sanitized = vm.customVoiceRecorder.sanitizeFilePrefix("My ../Bad:Voice! Name")
            assertFalse(sanitized.contains(".."))
            assertFalse(sanitized.contains("/"))
            assertEquals("my_bad_voice_name", sanitized)

            // Store an external temp file into the managed local custom_voices vault
            val tempSource = java.io.File(app.cacheDir, "temp_voice_sample.wav")
            tempSource.writeBytes(ByteArray(2048) { (it % 120).toByte() })

            val savedResult = vm.storeVoiceSampleFile(
                sourceFile = tempSource,
                profileName = "Kiran Custom Voice",
                sourceType = "IMPORTED",
                consentConfirmed = true,
                selectImmediately = true
            )
            assertTrue(savedResult.isSuccess)
            val savedProfile = savedResult.getOrThrow()
            val storedFile = java.io.File(savedProfile.filePath)
            assertTrue("Stored sample should reside inside custom_voices directory", storedFile.parentFile == vm.getLocalStorageDirectory())
            assertTrue(storedFile.exists())
            assertTrue(vm.verifyLocalSampleExists(savedProfile))

            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(com.example.ui.RecordingPhase.SAVED, vm.recordingProgress.value.phase)
            assertTrue(vm.recordingProgress.value.totalLocalStorageBytes >= 2048L)

            // Deleting profile should also remove the local audio file cleanly
            vm.deleteCustomVoice(savedProfile)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(storedFile.exists())
            tempSource.delete()
        }
    }
}
