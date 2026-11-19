package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.VoiceSessionState
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DeepBloodRed
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.VoidBlack
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun CyberGridBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle crimson ambient glow at top-center
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrimsonPrimary.copy(alpha = 0.14f),
                        DeepBloodRed.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.24f),
                    radius = size.maxDimension * 0.6f
                )
            )
            // Subtle tactical HUD grid
            val step = 48.dp.toPx()
            val gridColor = Color(0xFF1A0A0E).copy(alpha = 0.45f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun AishuCoreVisualizer(
    sessionState: VoiceSessionState,
    rmsLevel: Float,
    size: Dp = 176.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "core_anim")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (sessionState) {
                    VoiceSessionState.PROCESSING -> 2600
                    VoiceSessionState.LISTENING, VoiceSessionState.SPEAKING -> 5000
                    else -> 14000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_rot"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing)
        ),
        label = "inner_rot"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (sessionState == VoiceSessionState.IDLE) 2200 else 750,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val animatedRms by animateFloatAsState(
        targetValue = when (sessionState) {
            VoiceSessionState.LISTENING -> rmsLevel.coerceIn(0.15f, 1f)
            VoiceSessionState.SPEAKING -> 0.72f
            VoiceSessionState.PROCESSING -> 0.5f
            VoiceSessionState.WAKE_WORD_STANDBY -> 0.22f
            VoiceSessionState.IDLE -> 0.08f
        },
        animationSpec = tween(120),
        label = "rms"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f

            // Outer glowing aura responding to voice activity
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrimsonPrimary.copy(alpha = 0.22f + animatedRms * 0.30f),
                        DeepBloodRed.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * (0.95f + animatedRms * 0.15f)
                ),
                radius = baseRadius
            )

            // Rotating dashed cyber ring
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = CrimsonPrimary.copy(alpha = 0.75f),
                    radius = baseRadius * 0.88f * pulse.coerceAtMost(1.03f),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), 0f)
                    )
                )
            }

            // Counter-rotating inner scanner arc
            rotate(innerRotation, pivot = center) {
                drawCircle(
                    color = NeonScarlet.copy(alpha = 0.5f),
                    radius = baseRadius * 0.76f,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 18f), 0f)
                    )
                )
            }
        }

        val coreDiameter = size * 0.66f
        Box(
            modifier = Modifier
                .size(coreDiameter)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(CrimsonPrimary, NeonScarlet, DeepBloodRed)
                    ),
                    shape = CircleShape
                )
                .background(VoidBlack),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_aishu_core),
                contentDescription = "AISHU AI Neural Core Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VoiceActivityWaveform(
    sessionState: VoiceSessionState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing)
        ),
        label = "phase"
    )

    val activityScale = when (sessionState) {
        VoiceSessionState.LISTENING -> 0.35f + rmsLevel * 0.65f
        VoiceSessionState.SPEAKING -> 0.85f
        VoiceSessionState.PROCESSING -> 0.45f
        VoiceSessionState.WAKE_WORD_STANDBY -> 0.18f
        VoiceSessionState.IDLE -> 0.08f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val barCount = 28
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.48f).coerceAtLeast(3.dp.toPx())
        val midY = size.height / 2f

        for (i in 0 until barCount) {
            val norm = i / barCount.toFloat()
            val envelope = sin(norm * PI).toFloat()
            val wave = if (sessionState == VoiceSessionState.IDLE) {
                0.15f
            } else {
                0.25f + 0.75f * kotlin.math.abs(sin(phase + i * 0.45f))
            }
            val barHeight = (size.height * 0.88f * envelope * wave * activityScale)
                .coerceAtLeast(3.dp.toPx())
            val x = i * spacing + (spacing - barWidth) / 2f

            drawLine(
                color = if (sessionState == VoiceSessionState.IDLE) {
                    DeepBloodRed.copy(alpha = 0.5f)
                } else {
                    CrimsonPrimary.copy(alpha = 0.65f + 0.35f * envelope)
                },
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = barWidth
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        }
    }
}

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorderDark,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = TerminalSurface,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
