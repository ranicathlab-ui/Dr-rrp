package com.postpci.drrrp.ui.theme

import androidx.compose.ui.graphics.Color

// DR RRP dark-only palette. Do not introduce a light theme or alternate accents.

/** Near-black background; combine with [BackgroundGradientTop] for the subtle blue radial glow. */
val BackgroundNearBlack = Color(0xFF0A0E16)

/** Top-of-screen blue used in the subtle radial gradient over [BackgroundNearBlack]. */
val BackgroundGradientTop = Color(0xFF1B2E63)

/** Header / navigation deep blue. */
val HeaderDeepBlue = Color(0xFF1B2E63)

/** Header / navigation bright blue accent. */
val HeaderBrightBlue = Color(0xFF3E6EF0)

/** Primary buttons and the "Day N post-PCI" badge. Warm yellow-gold. */
val AccentYellowGold = Color(0xFFFFC53D)

/** Reserved ONLY for alerts and out-of-range readings. Never used decoratively. */
val AlertRed = Color(0xFFFF5A5F)

/** Card surface color. */
val SurfaceCard = Color(0xFF131A26)

/** 1px hairline border on cards. */
val BorderHairline = Color(0xFF232E3F)

// Derived text colors against the dark surfaces above.
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFFA7B0C0)
val TextDisabled = Color(0xFF5C6577)

// Semantic status colors (kept out of the alert-red family so red stays reserved for alerts).
val StatusGood = Color(0xFF3FCF8E)
val StatusInfo = Color(0xFF3E6EF0)
