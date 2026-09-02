package com.postpci.drrrp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

// DR RRP is dark-only by design. There is deliberately no light color scheme and no
// dynamic-color branch: never switch theme or accent colors based on system settings.
private val DrrrpColorScheme = darkColorScheme(
    primary = AccentYellowGold,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF241A00),
    secondary = HeaderBrightBlue,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = HeaderBrightBlue,
    background = BackgroundNearBlack,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    error = AlertRed,
    onError = androidx.compose.ui.graphics.Color.White,
)

/** Subtle blue radial gradient over the near-black background, applied at the top of the screen. */
val AppBackgroundBrush: Brush
    @Composable get() = Brush.radialGradient(
        colors = listOf(BackgroundGradientTop.copy(alpha = 0.35f), BackgroundNearBlack),
        center = Offset(0.5f, 0f),
        radius = 1400f,
    )

/** Fills the screen with the standard DR RRP background: near-black with the subtle top glow. */
fun Modifier.appBackground(): Modifier = this.fillMaxSize().composed { background(AppBackgroundBrush) }

@Composable
fun DrRrpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DrrrpColorScheme,
        typography = DrrrpTypography,
        content = content,
    )
}
