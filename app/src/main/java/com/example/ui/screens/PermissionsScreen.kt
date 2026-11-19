package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.CyberGridBackground
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val requiresRuntimeNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun queryPermissionState(
        permission: String,
        previousDeniedInSession: Boolean,
        autoGrantedOnLegacyOs: Boolean = false
    ): RuntimePermissionState {
        if (autoGrantedOnLegacyOs) {
            return RuntimePermissionState(
                isGranted = true,
                wasDeniedInSession = false,
                canShowRationale = false
            )
        }
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        val rationale = if (!granted && activity != null) {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        } else {
            false
        }
        return RuntimePermissionState(
            isGranted = granted,
            wasDeniedInSession = if (granted) false else previousDeniedInSession,
            canShowRationale = rationale
        )
    }

    var micState by remember {
        mutableStateOf(
            queryPermissionState(
                permission = Manifest.permission.RECORD_AUDIO,
                previousDeniedInSession = false
            )
        )
    }
    var cameraState by remember {
        mutableStateOf(
            queryPermissionState(
                permission = Manifest.permission.CAMERA,
                previousDeniedInSession = false
            )
        )
    }
    var notifState by remember {
        mutableStateOf(
            queryPermissionState(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                previousDeniedInSession = false,
                autoGrantedOnLegacyOs = !requiresRuntimeNotifPermission
            )
        )
    }

    // Re-sync permission state whenever returning from Android System App Settings
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micState = queryPermissionState(
                    permission = Manifest.permission.RECORD_AUDIO,
                    previousDeniedInSession = micState.wasDeniedInSession
                )
                cameraState = queryPermissionState(
                    permission = Manifest.permission.CAMERA,
                    previousDeniedInSession = cameraState.wasDeniedInSession
                )
                notifState = queryPermissionState(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    previousDeniedInSession = notifState.wasDeniedInSession,
                    autoGrantedOnLegacyOs = !requiresRuntimeNotifPermission
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val speechAvailable = remember(context) {
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    }

    // Individual Android Permission API handlers
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micState = queryPermissionState(
            permission = Manifest.permission.RECORD_AUDIO,
            previousDeniedInSession = !granted
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraState = queryPermissionState(
            permission = Manifest.permission.CAMERA,
            previousDeniedInSession = !granted
        )
    }

    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifState = queryPermissionState(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            previousDeniedInSession = !granted,
            autoGrantedOnLegacyOs = !requiresRuntimeNotifPermission
        )
    }

    val grantedCount = listOf(micState.isGranted, cameraState.isGranted, notifState.isGranted).count { it }
    val animatedProgress by animateFloatAsState(
        targetValue = grantedCount / 3f,
        animationSpec = tween(320),
        label = "permissions_progress"
    )

    CyberGridBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("permissions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to previous screen",
                            tint = TextPrimary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STEP 2 OF 4 • PERMISSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmberGlow
                    )
                    Text(
                        text = "SYSTEM PERMISSIONS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CrimsonPrimary
                    )
                }
                StatusPill(
                    text = "$grantedCount/3 READY",
                    accentColor = if (grantedCount == 3) CyberGreen else AmberAlert
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                TerminalCard(
                    borderColor = if (grantedCount == 3) {
                        CyberGreen.copy(alpha = 0.5f)
                    } else {
                        CrimsonPrimary.copy(alpha = 0.4f)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CrimsonPrimary
                        )
                        Text(
                            text = "Privacy-First Access Control",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AI SHU requests each permission individually and only when you allow it. Broad gallery storage, contacts, SMS, or phone-call permissions are never requested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (grantedCount == 3) CyberGreen else CrimsonPrimary,
                        trackColor = CardBorderDark,
                        strokeCap = StrokeCap.Round
                    )
                }

                // 1. MICROPHONE PERMISSION
                PermissionRequestCard(
                    icon = Icons.Default.Mic,
                    title = "Microphone & Audio Recording",
                    necessityExplanation = "Needed to hear your voice during Speak-to-Talk conversations (English & Telugu), detect your custom wake word in Automatic Mode, and record personal voice profile samples.",
                    deniedConsequence = if (micState.isPermanentlyDenied) {
                        "Denied permanently by system policy. Voice conversations, wake-word standby, and voice sample recording are unavailable until enabled in Android App Settings (typed chat still works)."
                    } else {
                        "If denied: Voice chat, wake-word standby, and voice sample recording stay disabled. Typed text chat remains fully functional."
                    },
                    state = micState,
                    statusLabel = when {
                        micState.isGranted -> "GRANTED"
                        micState.isPermanentlyDenied -> "SETTINGS REQUIRED"
                        micState.wasDeniedInSession -> "DENIED"
                        else -> "VOICE ESSENTIAL"
                    },
                    primaryActionLabel = when {
                        micState.isGranted -> "Microphone Access Granted"
                        micState.isPermanentlyDenied -> "Open App Settings for Microphone"
                        micState.wasDeniedInSession -> "Request Microphone Again"
                        else -> "Allow Microphone Access"
                    },
                    onPrimaryAction = {
                        if (micState.isPermanentlyDenied) {
                            openAppPermissionSettings(context)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onOpenSettingsFallback = if (!micState.isGranted) {
                        { openAppPermissionSettings(context) }
                    } else null,
                    actionTestTag = "grant_mic_button",
                    cardTestTag = "permission_card_microphone"
                )

                // 2. CAMERA PERMISSION
                PermissionRequestCard(
                    icon = Icons.Default.CameraAlt,
                    title = "Camera (Visual AI Capture)",
                    necessityExplanation = "Needed only when you choose to snap a live photo for Gemini Vision object recognition, scene explanations, or document text OCR.",
                    deniedConsequence = if (cameraState.isPermanentlyDenied) {
                        "Denied permanently by system policy. In-app camera capture is disabled until allowed in Android App Settings, while Photo Picker gallery uploads still work."
                    } else {
                        "If denied: Live camera capture is disabled, though picking existing images via Android's Photo Picker still works."
                    },
                    state = cameraState,
                    statusLabel = when {
                        cameraState.isGranted -> "GRANTED"
                        cameraState.isPermanentlyDenied -> "SETTINGS REQUIRED"
                        cameraState.wasDeniedInSession -> "DENIED"
                        else -> "OPTIONAL"
                    },
                    primaryActionLabel = when {
                        cameraState.isGranted -> "Camera Access Granted"
                        cameraState.isPermanentlyDenied -> "Open App Settings for Camera"
                        cameraState.wasDeniedInSession -> "Request Camera Again"
                        else -> "Allow Camera Access"
                    },
                    onPrimaryAction = {
                        if (cameraState.isPermanentlyDenied) {
                            openAppPermissionSettings(context)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOpenSettingsFallback = if (!cameraState.isGranted) {
                        { openAppPermissionSettings(context) }
                    } else null,
                    actionTestTag = "grant_camera_button",
                    cardTestTag = "permission_card_camera"
                )

                // 3. NOTIFICATION PERMISSION
                PermissionRequestCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications & Active Controls",
                    necessityExplanation = if (requiresRuntimeNotifPermission) {
                        "Required on Android 13+ to display the persistent listening status notification with an instant Stop button during Automatic Mode, plus assistant reminders."
                    } else {
                        "Displays the active listening indicator with a one-tap Stop control during Automatic Mode and assistant alerts (enabled automatically on this Android version)."
                    },
                    deniedConsequence = if (notifState.isPermanentlyDenied) {
                        "Denied permanently by system policy. Automatic Mode will run without a persistent notification bar control until enabled in Notification Settings."
                    } else {
                        "If denied: Background listening controls and assistant alert notifications will not appear in your status bar."
                    },
                    state = notifState,
                    statusLabel = when {
                        notifState.isGranted && !requiresRuntimeNotifPermission -> "ACTIVE (OS DEFAULT)"
                        notifState.isGranted -> "GRANTED"
                        notifState.isPermanentlyDenied -> "SETTINGS REQUIRED"
                        notifState.wasDeniedInSession -> "DENIED"
                        else -> "RECOMMENDED"
                    },
                    primaryActionLabel = when {
                        notifState.isGranted && !requiresRuntimeNotifPermission -> "Manage Notification Channels"
                        notifState.isGranted -> "Notifications Enabled"
                        notifState.isPermanentlyDenied -> "Open Notification Settings"
                        notifState.wasDeniedInSession -> "Request Notifications Again"
                        else -> "Allow Notifications"
                    },
                    allowClickWhenGranted = !requiresRuntimeNotifPermission,
                    onPrimaryAction = {
                        if (!requiresRuntimeNotifPermission || notifState.isPermanentlyDenied) {
                            openAppNotificationSettings(context)
                        } else {
                            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenSettingsFallback = if (!notifState.isGranted && requiresRuntimeNotifPermission) {
                        { openAppNotificationSettings(context) }
                    } else null,
                    actionTestTag = "grant_notifications_button",
                    cardTestTag = "permission_card_notifications"
                )

                // Zero-permission Photo Picker & SpeechRecognizer service cards
                InfoCapabilityCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Photos & Media (Photo Picker)",
                    description = "Uses Android's built-in Photo Picker and system file chooser so you grant one-time access only to the specific photo or voice audio file you select — no broad storage permission needed.",
                    statusLabel = "PRIVATE BY DESIGN",
                    statusPositive = true,
                    testTag = "photo_picker_info_card"
                )

                InfoCapabilityCard(
                    icon = Icons.Default.GraphicEq,
                    title = "System Speech Recognition",
                    description = "Connects to Android's native SpeechRecognizer service for bilingual English & Telugu speech-to-text without extra OS permissions.",
                    statusLabel = if (speechAvailable) "SERVICE READY" else "TEXT FALLBACK READY",
                    statusPositive = speechAvailable,
                    testTag = "speech_status_card"
                )

                AnimatedVisibility(visible = !micState.isGranted || !cameraState.isGranted || !notifState.isGranted) {
                    val missingCapabilities = buildList {
                        if (!micState.isGranted) add("Voice chat & recording")
                        if (!cameraState.isGranted) add("In-app camera capture")
                        if (!notifState.isGranted) add("Notification shade controls")
                    }
                    TerminalCard(
                        modifier = Modifier.testTag("permissions_denied_summary"),
                        borderColor = AmberAlert.copy(alpha = 0.55f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = AmberAlert
                            )
                            Text(
                                text = "Limited Mode Preview",
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberAlert
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You can safely continue without crashing. Currently paused: ${missingCapabilities.joinToString(", ")}. Enable them anytime in Settings > Permissions or Android App Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { openAppPermissionSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_app_settings_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Android App Settings", color = TextPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
                    .height(54.dp)
                    .testTag("permissions_continue_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = if (grantedCount == 3) "ALL SET • CONTINUE TO LOGIN" else "CONTINUE TO LOGIN",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
