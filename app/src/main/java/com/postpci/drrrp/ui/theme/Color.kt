package com.postpci.drrrp.ui.theme

import androidx.compose.ui.graphics.Color

// --- Modern Medical-Grade Color Palette ---

// Dark Palette
val DarkBackground = Color(0xFF0B1120) // Deep charcoal navy
val DarkSurface = Color(0xFF131D31) // Elevated dark card surface
val DarkBorder = Color(0xFF1E293B) // Subtle dark border
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)

// Light Palette
val LightBackground = Color(0xFFF8FAFC) // Soft off-white/slate
val LightSurface = Color(0xFFFFFFFF) // Elevated pure white card
val LightBorder = Color(0xFFE2E8F0) // Subtle light border
val TextPrimaryLight = Color(0xFF0F172A) // Crisp navy/slate text
val TextSecondaryLight = Color(0xFF64748B) // Muted slate secondary text

// Professional Medical Accents
val MedicalTealBlue = Color(0xFF0284C7) // Professional medical teal/blue primary
val MedicalTealBlueDark = Color(0xFF38BDF8)
val AccentAmber = Color(0xFFD97706) // Distinct amber for warnings / routine flags
val AlertRoseRed = Color(0xFFE11D48) // Soft rose/red for emergencies
val StatusGoodGreen = Color(0xFF059669) // Success / stable
val StatusInfo = Color(0xFF2563EB)

// Legacy aliases for backward compatibility across modules
val BackgroundNearBlack = DarkBackground
val BackgroundGradientTop = Color(0xFF1E293B)
val HeaderDeepBlue = Color(0xFF0F172A)
val HeaderBrightBlue = MedicalTealBlue
val AccentYellowGold = AccentAmber
val AlertRed = AlertRoseRed
val SurfaceCard = DarkSurface
val BorderHairline = DarkBorder
val TextPrimary = TextPrimaryDark
val TextSecondary = TextSecondaryDark
val TextDisabled = Color(0xFF475569)
val StatusGood = StatusGoodGreen
