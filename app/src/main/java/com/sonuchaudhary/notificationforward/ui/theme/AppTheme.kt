package com.sonuchaudhary.notificationforward.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD99B),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFF98F6B5),
    secondary = Color(0xFFB6CCB9),
    secondaryContainer = Color(0xFF344B39),
    tertiary = Color(0xFFA1D0CB),
    tertiaryContainer = Color(0xFF1F4E49),
    background = Color(0xFF0E1510),
    surface = Color(0xFF101914),
    surfaceVariant = Color(0xFF1E2D22),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
