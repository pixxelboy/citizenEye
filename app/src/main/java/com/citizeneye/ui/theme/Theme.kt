package com.citizeneye.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CitizenEyeColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EBFF),
    onPrimaryContainer = Color(0xFF102A43),
    secondary = Color(0xFFD6A84F),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF102A43),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF102A43),
    onSurfaceVariant = Color(0xFF52606D),
    outline = Color(0xFFBCCCDC)
)

@Composable
fun CitizenEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CitizenEyeColors, typography = CitizenEyeTypography, content = content)
}
