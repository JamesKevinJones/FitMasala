package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tonal palettes, the layer beneath Material 3's colour roles.
 *
 * M3 does not assign colours to roles directly - it derives every role from five
 * tonal palettes (primary, secondary, tertiary, neutral, neutral-variant), each
 * a ramp from tone 0 (black) to tone 100 (white). Roles are then tone
 * *positions*: light-mode primary is P40, dark-mode primary is P80. Because a
 * light role and its dark counterpart come from the same ramp, the two themes
 * stay recognisably the same brand instead of being two separate palettes that
 * happen to share a name.
 *
 * The hues are FitMasala's own: saffron, a fresh green, terracotta, and a
 * yellow-shifted warm neutral. The tone positions are Material's.
 *
 * Naming is `HueNN` where NN is the tone. Never reference a tone directly from
 * UI code - go through a colour role.
 */

// --- Primary: saffron, ~35 degrees ---
val P0 = Color(0xFF000000)
val P10 = Color(0xFF2A1800)
val P20 = Color(0xFF472A00)
val P30 = Color(0xFF663E00)
val P40 = Color(0xFF875300)
val P50 = Color(0xFFA96900)
val P60 = Color(0xFFCC8100)
val P70 = Color(0xFFEF9A0F)
val P80 = Color(0xFFFFB95C)
val P90 = Color(0xFFFFDDB5)
val P95 = Color(0xFFFFEEDC)
val P100 = Color(0xFFFFFFFF)

// --- Secondary: the achievement green, ~155 degrees ---
val S10 = Color(0xFF002114)
val S20 = Color(0xFF003826)
val S30 = Color(0xFF005138)
val S40 = Color(0xFF006B4B)
val S50 = Color(0xFF00875F)
val S60 = Color(0xFF1FA274)
val S70 = Color(0xFF45BE8D)
val S80 = Color(0xFF64DAA7)
val S90 = Color(0xFFA6F2CB)
val S100 = Color(0xFFFFFFFF)

// --- Tertiary: terracotta, ~15 degrees ---
val T10 = Color(0xFF3B0A00)
val T20 = Color(0xFF601500)
val T30 = Color(0xFF85210A)
val T40 = Color(0xFFA63A20)
val T50 = Color(0xFFC75236)
val T60 = Color(0xFFE76C4D)
val T80 = Color(0xFFFFB4A0)
val T90 = Color(0xFFFFDAD1)
val T100 = Color(0xFFFFFFFF)

// --- Neutral: warm, yellow-shifted. Not grey. ---
val N0 = Color(0xFF000000)
val N4 = Color(0xFF0B0A09)
val N6 = Color(0xFF100F0D)
val N10 = Color(0xFF1B1A17)
val N12 = Color(0xFF1F1E1B)
val N17 = Color(0xFF2A2825)
val N20 = Color(0xFF302E2B)
val N22 = Color(0xFF35322F)
val N24 = Color(0xFF3A3733)
val N87 = Color(0xFFDEDAD3)
val N90 = Color(0xFFE7E2DB)
val N92 = Color(0xFFEDE8E1)
val N94 = Color(0xFFF3EEE7)
val N95 = Color(0xFFF5F1EA)
val N96 = Color(0xFFF8F4ED)
val N98 = Color(0xFFFDF9F2)
val N100 = Color(0xFFFFFFFF)

// --- Neutral variant: for outlines and surface variants ---
val NV20 = Color(0xFF352F26)
val NV30 = Color(0xFF4C463D)
val NV50 = Color(0xFF7D766A)
val NV60 = Color(0xFF978F82)
val NV80 = Color(0xFFCFC6B8)
val NV90 = Color(0xFFEBE2D3)

// --- Error ---
val E10 = Color(0xFF410002)
val E20 = Color(0xFF690005)
val E30 = Color(0xFF93000A)
val E40 = Color(0xFFBA1A1A)
val E80 = Color(0xFFFFB4AB)
val E90 = Color(0xFFFFDAD6)
val E100 = Color(0xFFFFFFFF)
