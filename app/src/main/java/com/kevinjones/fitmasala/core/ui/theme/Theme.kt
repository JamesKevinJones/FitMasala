package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App colours with no Material role. Everything else comes from the ColorScheme.
 *
 * `progress` is not a duplicate of `secondary` - it is the SEMANTIC name. A
 * screen asks for "the achievement colour", never "the secondary colour", so the
 * meaning survives a later decision to re-map which role carries it.
 */
@Immutable
data class FmColors(
    val progress: Color,
    val progressSoft: Color,
    val macroProtein: Color,
    val macroCarbs: Color,
    val macroFat: Color,
    val streakCold: Color,
    val streakWarm: Color,
    val streakHot: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val track: Color,
    val accentPressed: Color,
)

private val DarkFmColors = FmColors(
    progress = S80,
    progressSoft = Color(0x2964DAA7),
    macroProtein = MacroProteinDark,
    macroCarbs = MacroCarbsDark,
    macroFat = MacroFatDark,
    streakCold = StreakColdDark,
    streakWarm = StreakWarmDark,
    streakHot = StreakHotDark,
    borderSubtle = Color(0x14FFFFFF),
    borderStrong = Color(0x24FFFFFF),
    textSecondary = Color(0x9EFFFFFF),
    textMuted = Color(0x6BFFFFFF),
    track = N24,
    accentPressed = P60,
)

/**
 * Light is a designed peer, not a derivation.
 *
 * Its hairlines are darker AND more opaque than the dark theme's. An 8%-white
 * line reads clearly on near-black; 8% black on near-white is invisible. Simply
 * flipping the alpha is the standard way a light theme ends up looking
 * unfinished.
 */
private val LightFmColors = FmColors(
    progress = S40,
    progressSoft = Color(0x1F006B4B),
    macroProtein = MacroProteinLight,
    macroCarbs = MacroCarbsLight,
    macroFat = MacroFatLight,
    streakCold = StreakColdLight,
    streakWarm = StreakWarmLight,
    streakHot = StreakHotLight,
    borderSubtle = Color(0x14000000),
    borderStrong = Color(0x2E000000),
    textSecondary = Color(0xA6000000),
    textMuted = Color(0x73000000),
    track = NV90,
    accentPressed = P30,
)

private val LocalFmColors = staticCompositionLocalOf { DarkFmColors }

/** `MaterialTheme.fm.progress` - the accessor every component uses. */
val MaterialTheme.fm: FmColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFmColors.current

/**
 * Every Material 3 role, at its standard tone position.
 *
 * Light takes primary from tone 40 and its container from 90; dark takes
 * primary from 80 and its container from 30. Because both draw from the SAME
 * ramp, the two themes read as one brand rather than two palettes that happen
 * to share a name.
 *
 * Filling every role matters even for the ones this app never names. Material's
 * own components reach for `inverseSurface` (snackbars), `surfaceBright`,
 * `surfaceDim` and all five container levels - leaving them at their defaults is
 * how a carefully themed app suddenly shows a stock purple snackbar.
 */
private val FitMasalaDarkScheme: ColorScheme = darkColorScheme(
    primary = P80,
    onPrimary = P20,
    primaryContainer = P30,
    onPrimaryContainer = P90,
    inversePrimary = P40,

    secondary = S80,
    onSecondary = S20,
    secondaryContainer = S30,
    onSecondaryContainer = S90,

    tertiary = T80,
    onTertiary = T20,
    tertiaryContainer = T30,
    onTertiaryContainer = T90,

    background = N6,
    onBackground = N90,
    surface = N6,
    onSurface = N90,
    surfaceVariant = NV30,
    onSurfaceVariant = NV80,
    surfaceTint = P80,

    surfaceDim = N6,
    surfaceBright = N24,
    surfaceContainerLowest = N4,
    surfaceContainerLow = N10,
    surfaceContainer = N12,
    surfaceContainerHigh = N17,
    surfaceContainerHighest = N22,

    inverseSurface = N90,
    inverseOnSurface = N20,

    outline = NV60,
    outlineVariant = NV30,

    error = E80,
    onError = E20,
    errorContainer = E30,
    onErrorContainer = E90,

    scrim = N0,
)

private val FitMasalaLightScheme: ColorScheme = lightColorScheme(
    primary = P40,
    onPrimary = P100,
    primaryContainer = P90,
    onPrimaryContainer = P10,
    inversePrimary = P80,

    secondary = S40,
    onSecondary = S100,
    secondaryContainer = S90,
    onSecondaryContainer = S10,

    tertiary = T40,
    onTertiary = T100,
    tertiaryContainer = T90,
    onTertiaryContainer = T10,

    background = N98,
    onBackground = N10,
    surface = N98,
    onSurface = N10,
    surfaceVariant = NV90,
    onSurfaceVariant = NV30,
    surfaceTint = P40,

    surfaceDim = N87,
    surfaceBright = N98,
    surfaceContainerLowest = N100,
    surfaceContainerLow = N96,
    surfaceContainer = N94,
    surfaceContainerHigh = N92,
    surfaceContainerHighest = N90,

    inverseSurface = N20,
    inverseOnSurface = N95,

    outline = NV50,
    outlineVariant = NV80,

    error = E40,
    onError = E100,
    errorContainer = E90,
    onErrorContainer = E10,

    scrim = N0,
)

/**
 * Light and dark are peers. The app follows the system preference.
 *
 * This reverses an earlier dark-only decision. The product argument for
 * dark-only was real - a gym at 6am, a kitchen at 9pm - but Material's
 * accessibility argument outranks it: forcing a theme "can go against a user's
 * accessibility and personalization needs", and dark text on light is genuinely
 * easier to read for people whose astigmatism makes light-on-dark halate.
 *
 * Dynamic colour is still declined, and this is the one place the app departs
 * from Material's recommendation. Saffron, green and the macro triad carry
 * INFORMATION here rather than decoration - "warm means food, green means
 * progress" is the entire legend, and the macro ring is glanceable only because
 * protein is always the same colour. Wallpaper-derived colour would repaint a
 * system the user has already learned.
 */
@Composable
fun FitMasalaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFmColors provides if (darkTheme) DarkFmColors else LightFmColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) FitMasalaDarkScheme else FitMasalaLightScheme,
            typography = FitMasalaTypography,
            shapes = FitMasalaShapes,
            content = content,
        )
    }
}
