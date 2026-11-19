package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

internal data class RuntimePermissionState(
    val isGranted: Boolean = false,
    val wasDeniedInSession: Boolean = false,
    val canShowRationale: Boolean = false
) {
    val isPermanentlyDenied: Boolean
        get() = !isGranted && wasDeniedInSession && !canShowRationale
}

@Composable
internal fun PermissionRequestCard(
    icon: ImageVector,
    title: String,
    necessityExplanation: String,
    deniedConsequence: String,
    state: RuntimePermissionState,
    statusLabel: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onOpenSettingsFallback: (() -> Unit)?,
    actionTestTag: String,
    cardTestTag: String,
    allowClickWhenGranted: Boolean = false
) {
    val accentColor = when {
        state.isGranted -> CyberGreen
        state.wasDeniedInSession -> NeonScarlet
        else -> AmberAlert
    }

    TerminalCard(
        modifier = Modifier.testTag(cardTestTag),
        borderColor = when {
            state.isGranted -> CyberGreen.copy(alpha = 0.45f)
            state.wasDeniedInSession -> NeonScarlet.copy(alpha = 0.55f)
            else -> CardBorderDark
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isGranted) CyberGreen.copy(alpha = 0.14f) else CrimsonContainer
                        )
                        .border(
                            width = 1.dp,
                            color = (if (state.isGranted) CyberGreen else CrimsonPrimary).copy(alpha = 0.45f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (state.isGranted) CyberGreen else NeonScarlet,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                text = statusLabel,
                accentColor = accentColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = necessityExplanation,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (!state.isGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelElevated)
                    .border(
                        width = 1.dp,
                        color = (if (state.wasDeniedInSession) NeonScarlet else AmberAlert).copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = deniedConsequence,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.wasDeniedInSession) EmberGlow else AmberAlert
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        val buttonEnabled = !state.isGranted || allowClickWhenGranted
        if (!state.isGranted && !state.isPermanentlyDenied) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(actionTestTag),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary.copy(alpha = 0.9f),
                    contentColor = TextPrimary
                )
            ) {
                Text(text = primaryActionLabel, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            OutlinedButton(
                onClick = onPrimaryAction,
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(actionTestTag),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = CyberGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = primaryActionLabel,
                    color = if (state.isGranted) CyberGreen else EmberGlow
                )
            }
        }

        if (onOpenSettingsFallback != null && !state.isPermanentlyDenied && state.wasDeniedInSession) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onOpenSettingsFallback,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize()
            ) {
                Text(
                    text = "Enable later in App Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
internal fun InfoCapabilityCard(
    icon: ImageVector,
    title: String,
    description: String,
    statusLabel: String,
    statusPositive: Boolean,
    testTag: String
) {
    TerminalCard(
        modifier = Modifier.testTag(testTag),
        borderColor = if (statusPositive) CyberGreen.copy(alpha = 0.35f) else CardBorderDark
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (statusPositive) CyberGreen else AmberAlert
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                text = statusLabel,
                accentColor = if (statusPositive) CyberGreen else AmberAlert
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun openAppPermissionSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { context.startActivity(intent) }
}

internal fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppPermissionSettings(context) }
}
