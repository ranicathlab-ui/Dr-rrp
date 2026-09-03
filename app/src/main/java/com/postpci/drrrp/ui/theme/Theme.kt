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
    primary = AccentYellowGold,
    onPrimary = Color(0xFF241A00),
    secondary = HeaderBrightBlue,
    onSecondary = Color.White,
    tertiary = HeaderBrightBlue,
    background = BackgroundNearBlack,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    error = AlertRed,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF996B00),
    onPrimary = Color.White,
    secondary = Color(0xFF2B52D4),
    onSecondary = Color.White,
    tertiary = Color(0xFF2B52D4),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

/** Subtle background gradient adapting to light or dark mode. */
val AppBackgroundBrush: Brush
    @Composable get() {
        val dark = isSystemInDarkTheme()
        return if (dark) {
            Brush.radialGradient(
                colors = listOf(BackgroundGradientTop.copy(alpha = 0.35f), BackgroundNearBlack),
                center = Offset(0.5f, 0f),
                radius = 1400f,
            )
        } else {
            Brush.radialGradient(
                colors = listOf(Color(0xFFE2E8F0), Color(0xFFF8FAFC)),
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
