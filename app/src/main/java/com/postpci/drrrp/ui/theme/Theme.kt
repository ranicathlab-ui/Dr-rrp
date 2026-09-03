package com.postpci.drrrp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MedicalTealBlueDark,
    onPrimary = Color.White,
    secondary = MedicalTealBlueDark,
    onSecondary = Color.White,
    tertiary = MedicalTealBlueDark,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    error = AlertRoseRed,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = MedicalTealBlue,
    onPrimary = Color.White,
    secondary = MedicalTealBlue,
    onSecondary = Color.White,
    tertiary = MedicalTealBlue,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD5E1),
    error = AlertRoseRed,
    onError = Color.White,
)

/** Subtle background gradient adapting to light or dark mode. */
val AppBackgroundBrush: Brush
    @Composable get() {
        val dark = isSystemInDarkTheme()
        return if (dark) {
            Brush.radialGradient(
                colors = listOf(Color(0xFF1E293B).copy(alpha = 0.25f), DarkBackground),
                center = Offset(0.5f, 0f),
                radius = 1400f,
            )
        } else {
            Brush.radialGradient(
                colors = listOf(Color(0xFFE2E8F0).copy(alpha = 0.5f), LightBackground),
                center = Offset(0.5f, 0f),
                radius = 1400f,
            )
        }
    }

/** Fills the screen with the standard DR RRP theme background. */
fun Modifier.appBackground(): Modifier = this.fillMaxSize().composed { background(AppBackgroundBrush) }

@Composable
fun DrRrpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DrrrpTypography,
        content = content,
    )
}
