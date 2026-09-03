package com.jekael.adoel.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val Zinc950 = Color(0xFF09090B)
val Zinc900 = Color(0xFF18181B)
val Zinc800 = Color(0xFF27272A)
val Zinc700 = Color(0xFF3F3F46)
val Zinc600 = Color(0xFF52525B)
val Zinc500 = Color(0xFF71717A)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc50 = Color(0xFFFAFAFA)

// Single blue family for both roles the brief assigns to blue — informational status (CALM)
// and the app's primary action color. Two separate blue-ish families (this used to also carry
// a parallel Blue400/500/700 trio for CALM only) read as two different colors with no visual
// cue for why, since they're both just "blue" to the eye.
val Cyan400 = Color(0xFF22D3EE)
val Cyan500 = Color(0xFF06B6D4)
val Cyan600 = Color(0xFF0891B2)
val Cyan700 = Color(0xFF0E7490)

val Teal500 = Color(0xFF14B8A6)

val Amber400 = Color(0xFFFBBF24)
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFD97706)
val Amber700 = Color(0xFFB45309)

val Red400 = Color(0xFFF87171)
val Red500 = Color(0xFFEF4444)
val Red700 = Color(0xFFB91C1C)

val Violet500 = Color(0xFF8B5CF6)
val Indigo500 = Color(0xFF6366F1)
val Fuchsia500 = Color(0xFFD946EF)
// Reserved for the "Matching" doff-completion role only (RadarCard swipe/celebration,
// GuidedDoffingSheet's Matching accent+button) — do not also use this for machine-type identity
// (see mesinTipeColor in Icons.kt), or a D408 badge next to a Matching action reads as related
// when they're unconnected.
val Sky500 = Color(0xFF0EA5E9)
val Sky400 = Color(0xFF38BDF8)
val Emerald500 = Color(0xFF10B981)
val Emerald400 = Color(0xFF34D399)
// Used only by OnboardingDialog's "Ketuk Nomor/Corak" gesture badge — no Purple500 counterpart
// exists because nothing else in the app currently needs one.
val Purple400 = Color(0xFFC084FC)

// True dark-gray neutrals for dark mode surfaces — the brief calls for "abu-abu gelap" (dark
// gray), not near-black. Zinc950/900/800/700 above sit much closer to black (luminance ~0.4%)
// than a dark gray; DarkBg and friends target the same jump-in-tone ladder one step lighter and
// warmer so shadows (see CardStyles.kt) also read against it instead of vanishing.
val DarkBg = Color(0xFF1C1C1E)
val DarkBgElevated = Color(0xFF242427)
val DarkBgElevated2 = Color(0xFF2C2C30)
val DarkBorder = Color(0xFF3D3D42)

/**
 * Semantic, theme-aware neutral tokens. Every non-brand (Zinc-scale) color used across the
 * app should come from here rather than a literal Zinc constant, so that switching between
 * dark/light actually changes the UI instead of just the MaterialTheme.colorScheme (which
 * most of this app's composables never read).
 */
class AppColors(
    val bg: Color,
    val bgElevated: Color,
    val bgElevated2: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val bannerWarnBg: Color,
    val bannerWarnFg: Color,
    val criticalPulseTarget: Color,
    val isDark: Boolean,
) {
    /** Alias for [bgElevated] — ShortcutPicker/OnboardingDialog reach for this name; kept as a
     * synonym rather than a distinct third tier so it doesn't need its own light/dark values. */
    val bgElevated1: Color get() = bgElevated
}

private val DarkAppColors = AppColors(
    isDark = true,
    bg = DarkBg,
    bgElevated = DarkBgElevated,
    bgElevated2 = DarkBgElevated2,
    border = DarkBorder,
    textPrimary = Zinc100,
    // Contrast targets carried over from the old Zinc950 base (13.46:1 / 7.76:1 / 4.12:1) — the
    // new DarkBg is lighter but still dark enough that this ladder clears WCAG AA the same way.
    textSecondary = Zinc300,
    textMuted = Zinc400,
    textFaint = Zinc500,
    bannerWarnBg = Color(0xFF292007),
    bannerWarnFg = Amber400,
    criticalPulseTarget = Color(0xFF3A1414),
)

// Warm-neutral light palette — a soft paper/cream base instead of stark white, kept low-glare
// on purpose (a plain white bg made list cards/header/console read as almost the same tone as
// the page once shadows were replaced with tonal elevation — see elevatedListCard/floatingHeaderCard).
// Text tokens are left on the cool Zinc scale: luminance (what drives WCAG contrast) barely
// shifts moving Zinc100→WarmBg300 below, so the ratios noted on textFaint below still hold
// (textPrimary/Secondary/Muted comfortably >7:1, textFaint ~4:1, same as before this change).
val WarmBg300 = Color(0xFFEDEAE4)
val WarmBg100 = Color(0xFFF7F5F1)
// The lightest tier — was pure white before, which broke the warm-paper feel every other light
// surface uses (this shows up as the top-most nested surface, e.g. a card-within-a-card).
val WarmBg50 = Color(0xFFFDFCFA)
val WarmBorder = Color(0xFFD9D3C7)

private val LightAppColors = AppColors(
    isDark = false,
    bg = WarmBg300,
    bgElevated = WarmBg100,
    bgElevated2 = WarmBg50,
    border = WarmBorder,
    textPrimary = Zinc900,
    textSecondary = Zinc700,
    textMuted = Zinc600,
    // textFaint alone moved 400→500 (2.33:1 → 4.40:1) — textSecondary/textMuted above were
    // already well above WCAG AA (9.50:1 / 7.03:1) and don't need to shift.
    textFaint = Zinc500,
    bannerWarnBg = Color(0xFFFEF3C7),
    bannerWarnFg = Amber700,
    criticalPulseTarget = Color(0xFFFECACA),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

private val DarkScheme = darkColorScheme(
    primary = Cyan500,
    onPrimary = Zinc950,
    background = DarkBg,
    surface = DarkBgElevated,
    onBackground = Zinc100,
    onSurface = Zinc100,
)

private val LightScheme = lightColorScheme(
    primary = Cyan600,
    onPrimary = Zinc50,
    background = Zinc100,
    surface = Zinc50,
    onBackground = Zinc900,
    onSurface = Zinc900,
)

// Material3's own default Typography() (Roboto) with every role's fontFamily swapped to Inter,
// so stock M3 components (Button labels, AlertDialog title/text, OutlinedTextField label) pick
// up Inter too, not just the ad hoc AppType tokens most of this app's composables read instead.
private val DefaultTypography = Typography()
private val AdoelTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = InterFontFamily),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = InterFontFamily),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = InterFontFamily),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = InterFontFamily),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = InterFontFamily),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = InterFontFamily),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = InterFontFamily),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = InterFontFamily),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = InterFontFamily),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = InterFontFamily),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = InterFontFamily),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = InterFontFamily),
)

@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun AdoelTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = resolveDarkTheme(themeMode)
    CompositionLocalProvider(
        LocalAppColors provides if (dark) DarkAppColors else LightAppColors,
        LocalIndication provides FabricWaveIndication,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AdoelTypography,
            content = content,
        )
    }
}
