package com.example

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.PermissionsScreen
import com.example.ui.theme.AishuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionsScreenRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `displays microphone camera and notification explanations and individual request buttons when denied`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS
        )

        var continueCount = 0
        var backCount = 0

        composeRule.setContent {
            AishuTheme {
                PermissionsScreen(
                    onContinue = { continueCount++ },
                    onBack = { backCount++ }
                )
            }
        }

        composeRule.onNodeWithText("SYSTEM PERMISSIONS").assertIsDisplayed()
        composeRule.onNodeWithText("0/3 READY").assertIsDisplayed()

        // Microphone section & button
        composeRule.onNodeWithTag("permission_card_microphone").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Microphone & Audio Recording").assertIsDisplayed()
        composeRule.onNodeWithTag("grant_mic_button").performScrollTo().assertIsEnabled()

        // Camera section & button
        composeRule.onNodeWithTag("permission_card_camera").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Camera (Visual AI Capture)").assertIsDisplayed()
        composeRule.onNodeWithTag("grant_camera_button").performScrollTo().assertIsEnabled()

        // Notifications section & button
        composeRule.onNodeWithTag("permission_card_notifications").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Notifications & Active Controls").assertIsDisplayed()
        composeRule.onNodeWithTag("grant_notifications_button").performScrollTo().assertIsEnabled()

        // Denied summary banner + App Settings fallback
        composeRule.onNodeWithTag("permissions_denied_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("open_app_settings_button").performScrollTo().assertIsEnabled()

        // Clicking individual request buttons invokes OS permission launchers without crashing
        composeRule.onNodeWithTag("grant_mic_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("grant_camera_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("grant_notifications_button").performScrollTo().performClick()

        // Continue remains functional even with all optional permissions denied
        composeRule.onNodeWithTag("permissions_continue_button").performClick()
        assertEquals(1, continueCount)

        // Back callback works
        composeRule.onNodeWithTag("permissions_back_button").performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun `updates readiness summary and disables grant buttons when all three permissions are granted`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS
        )

        var continued = false

        composeRule.setContent {
            AishuTheme {
                PermissionsScreen(
                    onContinue = { continued = true }
                )
            }
        }

        composeRule.onNodeWithText("3/3 READY").assertIsDisplayed()
        composeRule.onNodeWithTag("grant_mic_button").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("grant_camera_button").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("grant_notifications_button").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("permissions_denied_summary").assertDoesNotExist()
        composeRule.onNodeWithText("ALL SET • CONTINUE TO LOGIN").assertIsDisplayed()

        composeRule.onNodeWithTag("permissions_continue_button").performClick()
        assertTrue(continued)
    }
}
