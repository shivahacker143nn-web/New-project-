package com.example

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.readMaskedFirebaseConfig
import com.example.data.model.AssistantMode
import com.example.ui.AishuViewModel
import com.example.ui.VoiceSettingsViewModel
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PersonalizationScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.settings.ApiSettingsSection
import com.example.ui.screens.settings.SettingsMenuScreen
import com.example.ui.screens.settings.SettingsSection
import com.example.ui.screens.settings.VoiceSettingsSection
import com.example.ui.theme.AishuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdatedFlowsRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `firebase masked config masks sensitive credentials and preserves project identity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cfg = readMaskedFirebaseConfig(app)
        assertTrue(cfg.isConfigured)
        assertFalse(cfg.projectId.isBlank())
        assertTrue(cfg.maskedAppId.contains("••••"))
        assertTrue(cfg.maskedApiKeyStatus.contains("••••") || cfg.maskedApiKeyStatus.contains("Configured") || cfg.maskedApiKeyStatus.contains("Firebase"))
    }

    @Test
    fun `splash screen navigates on get started click`() {
        var clicked = false
        composeRule.setContent {
            AishuTheme {
                SplashScreen(onGetStarted = { clicked = true })
            }
        }
        composeRule.onNodeWithText("AI SHU").assertIsDisplayed()
        composeRule.onNodeWithText("YOUR PERSONAL AI ASSISTANT").assertIsDisplayed()
        composeRule.onNodeWithTag("get_started_button").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `login screen renders three methods and allows skip for now`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AishuViewModel(app)
        var authenticated = false

        composeRule.setContent {
            AishuTheme {
                LoginScreen(viewModel = vm, onAuthenticated = { authenticated = true })
            }
        }

        composeRule.onNodeWithTag("google_signin_button").assertIsDisplayed()
        composeRule.onNodeWithTag("phone_number_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("skip_login_button").assertIsDisplayed().performClick()

        assertTrue(authenticated)
        assertTrue(vm.allowOfflineLocalSession.value)
    }

    @Test
    fun `personalization screen asks assistant name and user name and launches`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AishuViewModel(app)
        var launched = false

        composeRule.setContent {
            AishuTheme {
                PersonalizationScreen(viewModel = vm, onComplete = { launched = true })
            }
        }

        composeRule.onNodeWithText("What should I call your AI assistant?").assertIsDisplayed()
        composeRule.onNodeWithText("What is your name?").assertIsDisplayed()
        composeRule.onNodeWithTag("personalize_assistant_name_input").performTextReplacement("Nova")
        composeRule.onNodeWithTag("personalize_user_name_input").performTextReplacement("Kiran")
        composeRule.onNodeWithTag("personalization_finish_button").performClick()

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(launched)
    }

    @Test
    fun `settings menu shows api settings first and voice settings second`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AishuViewModel(app)
        var chosenSection: SettingsSection? = null

        composeRule.setContent {
            AishuTheme {
                SettingsMenuScreen(
                    viewModel = vm,
                    onBack = {},
                    onSelectSection = { chosenSection = it }
                )
            }
        }

        composeRule.onNodeWithText("1. API Settings").assertIsDisplayed()
        composeRule.onNodeWithText("2. Voice Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_item_api_settings").performClick()
        assertEquals(SettingsSection.API_SETTINGS, chosenSection)
    }

    @Test
    fun `api settings displays gemini search switch and masked firebase configuration`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AishuViewModel(app)

        composeRule.setContent {
            AishuTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ApiSettingsSection(viewModel = vm)
                }
            }
        }

        composeRule.onNodeWithText("1. GOOGLE GEMINI AI & SEARCH").assertIsDisplayed()
        composeRule.onNodeWithTag("gemini_search_switch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("test_api_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2. FIREBASE CONFIGURATION").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3. SPEECH, TTS & CUSTOM VOICE SERVICES").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `assistant screen shows mode selector below speak button and switches to automatic stop control`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val vm = AishuViewModel(app)

        composeRule.setContent {
            AishuTheme {
                AssistantScreen(viewModel = vm, onOpenSettings = {})
            }
        }

        composeRule.onNodeWithTag("speak_to_talk_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_selector_row").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_mode_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("automatic_mode_chip").assertIsDisplayed()

        // Switch to Automatic Mode cleanly via chip tap
        composeRule.onNodeWithTag("automatic_mode_chip").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.settings.value.assistantMode == AssistantMode.AUTOMATIC
        }
        assertEquals(AssistantMode.AUTOMATIC, vm.settings.value.assistantMode)

        // When Automatic Mode conversation starts, Speak button is replaced by STOP CONVERSATION
        vm.onWakeWordActivated()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("STOP CONVERSATION").assertIsDisplayed()
        composeRule.onNodeWithTag("speak_to_talk_button").assertDoesNotExist()

        // Pressing Stop halts active conversation cleanly
        composeRule.onNodeWithTag("stop_button").performClick()
        composeRule.waitForIdle()
        assertFalse(vm.isHandsFreeActive.value)
        composeRule.onNodeWithTag("speak_to_talk_button").assertIsDisplayed()

        // Switching back to Manual Mode via chip tap updates mode cleanly
        composeRule.onNodeWithTag("manual_mode_chip").performClick()
        composeRule.waitUntil(timeoutMillis = 3000L) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.settings.value.assistantMode == AssistantMode.MANUAL
        }
        assertEquals(AssistantMode.MANUAL, vm.settings.value.assistantMode)
    }

    @Test
    fun `voice settings renders voice rules card at bottom and supports replace target state`() {
        kotlinx.coroutines.runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val voiceVm = VoiceSettingsViewModel(app)

            val tempSource = File(app.cacheDir, "sample_for_replace_test.wav")
            tempSource.writeBytes(ByteArray(1536) { (it % 90).toByte() })
            val profile = voiceVm.storeVoiceSampleFile(
                sourceFile = tempSource,
                profileName = "Primary Replaceable Voice",
                sourceType = "IMPORTED",
                consentConfirmed = true,
                selectImmediately = true
            ).getOrThrow()

            voiceVm.beginReplacingProfile(profile)
            assertEquals(profile.id, voiceVm.replacingProfileId.value)

            composeRule.setContent {
                AishuTheme {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        VoiceSettingsSection(voiceViewModel = voiceVm)
                    }
                }
            }

            composeRule.onNodeWithText("CUSTOM VOICE STUDIO (ON-DEVICE)").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("voice_rules_card").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("VOICE RULES & PROTECTIONS").performScrollTo().assertIsDisplayed()

            voiceVm.cancelReplacingProfile()
            assertEquals(null, voiceVm.replacingProfileId.value)
            voiceVm.deleteCustomVoice(profile)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            tempSource.delete()
        }
    }
}
