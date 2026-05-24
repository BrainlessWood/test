package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightSepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = Color.White,
    secondary = SepiaSecondary,
    onSecondary = Color.White,
    tertiary = SepiaTertiary,
    onTertiary = Color.White,
    background = SepiaBackground,
    onBackground = Color(0xFF2C2219),
    surface = SepiaSurface,
    onSurface = Color(0xFF2C2219),
    surfaceVariant = Color(0xFFEBE0D0),
    onSurfaceVariant = Color(0xFF4E453A)
)

private val DarkEspressoColorScheme = darkColorScheme(
    primary = EspressoPrimary,
    onPrimary = Color(0xFF38230D),
    secondary = EspressoSecondary,
    onSecondary = Color(0xFF231405),
    tertiary = EspressoTertiary,
    onTertiary = Color(0xFF192A06),
    background = EspressoBackground,
    onBackground = EspressoOnSurface,
    surface = EspressoSurface,
    onSurface = EspressoOnSurface,
    surfaceVariant = Color(0xFF332F2B),
    onSurfaceVariant = EspressoSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkEspressoColorScheme
    } else {
        LightSepiaColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
