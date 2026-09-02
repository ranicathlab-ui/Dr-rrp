package com.postpci.drrrp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.postpci.drrrp.R

// Space Grotesk (headings), Inter (body) and Noto Sans Tamil (Tamil fallback) ship as variable
// fonts, so distinct weights are pulled from the same file via FontVariation.Settings.
// IBM Plex Mono (numeric readings) ships as separate static weight files.

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight): Font =
    Font(
        resId = resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

private val spaceGroteskFonts = listOf(
    variableFont(R.font.space_grotesk, FontWeight.Normal),
    variableFont(R.font.space_grotesk, FontWeight.Medium),
    variableFont(R.font.space_grotesk, FontWeight.SemiBold),
    variableFont(R.font.space_grotesk, FontWeight.Bold),
)

private val interFonts = listOf(
    variableFont(R.font.inter, FontWeight.Normal),
    variableFont(R.font.inter, FontWeight.Medium),
    variableFont(R.font.inter, FontWeight.SemiBold),
    variableFont(R.font.inter, FontWeight.Bold),
)

private val ibmPlexMonoFonts = listOf(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Tamil script fallback — Space Grotesk/Inter/IBM Plex Mono don't cover Tamil glyphs. */
private val notoSansTamilFonts = listOf(
    variableFont(R.font.noto_sans_tamil, FontWeight.Normal),
    variableFont(R.font.noto_sans_tamil, FontWeight.Medium),
    variableFont(R.font.noto_sans_tamil, FontWeight.SemiBold),
    variableFont(R.font.noto_sans_tamil, FontWeight.Bold),
)

val SpaceGrotesk = FontFamily(spaceGroteskFonts)
val Inter = FontFamily(interFonts)
val IBMPlexMono = FontFamily(ibmPlexMonoFonts)
val NotoSansTamil = FontFamily(notoSansTamilFonts)

// Headings/body/numeric families fall back to Noto Sans Tamil, then the system default, for
// any Tamil glyphs the primary Latin faces can't render.
private val HeadingFamily = FontFamily(spaceGroteskFonts + notoSansTamilFonts)
private val BodyFamily = FontFamily(interFonts + notoSansTamilFonts)
private val NumericFamily = FontFamily(ibmPlexMonoFonts + notoSansTamilFonts)

// Standard (non-accessibility-large) text sizes throughout, per the design system.
val DrrrpTypography = Typography(
    displayLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

/** Style for numeric vital readings (BP, HR, SpO2, weight, etc). Use with [IBMPlexMono] directly. */
val NumericReadingLarge = TextStyle(fontFamily = NumericFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp)
val NumericReadingMedium = TextStyle(fontFamily = NumericFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)
