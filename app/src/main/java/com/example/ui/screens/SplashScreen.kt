package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.VoiceSessionState
import com.example.ui.components.AishuCoreVisualizer
import com.example.ui.components.CyberGridBackground
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DeepBloodRed
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SplashScreen(
    onGetStarted: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "splash_glow")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    CyberGridBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top tactical tag
                Surface(
                    shape = RoundedCornerShape(50),
                    color = TerminalSurface,
                    border = BorderStroke(1.dp, DeepBloodRed)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CrimsonPrimary
                        )
                        Text(
                            text = "NEURAL VOICE CORE • READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmberGlow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Centerpiece animated AISHU emblem & wordmark
                AishuCoreVisualizer(
                    sessionState = VoiceSessionState.PROCESSING,
                    rmsLevel = 0.65f,
                    size = 180.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "AI SHU",
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(glowAlpha)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.36f)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    DeepBloodRed.copy(alpha = 0.1f),
                                    CrimsonPrimary,
                                    DeepBloodRed.copy(alpha = 0.1f)
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "YOUR PERSONAL AI ASSISTANT",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonScarlet,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Powered by Google Gemini with bilingual English & తెలుగు voice conversation, automatic online/offline intelligence, custom voice profiles, and permanent assistant rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Get Started CTA pinned cleanly in viewport
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("get_started_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPrimary,
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "GET STARTED",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Get Started"
                    )
                }
            }
        }
    }
}
