package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AishuDarkColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = TextPrimary,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = EmberGlow,
    secondary = NeonScarlet,
    onSecondary = VoidBlack,
    secondaryContainer = PanelElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = ElectricCyan,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = ObsidianBg,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = VoidBlack,
    surfaceContainerLow = ObsidianBg,
    surfaceContainer = TerminalSurface,
    surfaceContainerHigh = PanelElevated,
    outline = CardBorderDark,
    outlineVariant = DeepBloodRed,
    error = NeonScarlet,
    onError = VoidBlack
)

val AishuShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AishuTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AishuDarkColorScheme,
        typography = AishuTypography,
        shapes = AishuShapes,
        content = content
    )
}
