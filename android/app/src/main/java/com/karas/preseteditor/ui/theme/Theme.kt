package com.karas.preseteditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same accent as the desktop app (#6EA8FF on dark), darker on light for 4.5:1 contrast.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FB),
    onPrimaryContainer = Color(0xFF0B2A57),
    secondary = Color(0xFF51607A),
    secondaryContainer = Color(0xFFE3E8F1),
    onSecondaryContainer = Color(0xFF1B2433),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF16181C),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF16181C),
    surfaceVariant = Color(0xFFE6E8EC),
    onSurfaceVariant = Color(0xFF4A505A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF1F4),
    surfaceContainerHigh = Color(0xFFE9EBEF),
    surfaceContainerHighest = Color(0xFFE2E5EA),
    outline = Color(0xFF7A808A),
    outlineVariant = Color(0xFFD5D8DE),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF00285C),
    primaryContainer = Color(0xFF1C3A66),
    onPrimaryContainer = Color(0xFFD7E5FF),
    secondary = Color(0xFFB4C1D8),
    secondaryContainer = Color(0xFF2B3240),
    onSecondaryContainer = Color(0xFFDCE3F0),
    background = Color(0xFF101113),
    onBackground = Color(0xFFE6E7EA),
    surface = Color(0xFF101113),
    onSurface = Color(0xFFE6E7EA),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFB2B6BE),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF17191C),
    surfaceContainer = Color(0xFF1B1D21),
    surfaceContainerHigh = Color(0xFF22252A),
    surfaceContainerHighest = Color(0xFF2B2E34),
    outline = Color(0xFF8A8F98),
    outlineVariant = Color(0xFF34373D),
    error = Color(0xFFF2B8B5)
)

// Scale kept to a few sizes: 12 / 14 / 16 / 20 / 24. Body and list text are 16sp.
private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    )
}

/** Monospace style for prompt content, like the desktop editor. */
val ContentTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 23.sp)

@Composable
fun PresetEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
