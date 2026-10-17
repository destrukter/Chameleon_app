package com.destrukter.chameleon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5E1A5),
    onPrimaryContainer = Color(0xFF0B2E0D),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFE65100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF3E1600),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F6EC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDE7D3),
    outlineVariant = Color(0xFFC3CDB9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCC65),
    onPrimary = Color(0xFF0B2E0D),
    primaryContainer = Color(0xFF2E5D1F),
    onPrimaryContainer = Color(0xFFDCEDC8),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3E1600),
    tertiaryContainer = Color(0xFF7A3000),
    onTertiaryContainer = Color(0xFFFFE0B2),
    error = Color(0xFFEF9A9A),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F140F),
    surface = Color(0xFF1A221A),
    surfaceVariant = Color(0xFF2F3A2C),
    outlineVariant = Color(0xFF3F4A3B),
)

@Composable
fun ChameleonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
