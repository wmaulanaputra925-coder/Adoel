package com.jekael.adoel.ui.theme

import androidx.compose.ui.unit.dp

/** Shared dimension tokens for values that repeat across files. Corner radii that only appear
 * in one component stay inline there — these are the ones that define a cross-screen visual
 * identity (every floating bar, every list card) or physics that must feel identical everywhere
 * (swipe gestures on Estimasi/Doffing/Statistik cards). */
object Dimens {
    /** Corner radius of the floating bars: header, console, panel headers. Matches web's shared
     * .floating-card (border-radius: 18px) — was 28dp, which read as a much more rounded "pill"
     * than web's more moderate corner on the same header/console-bar/panel-header elements. */
    val RadiusFloating = 18.dp

    /** Corner radius of list cards: radar, doffing rows, shift cards, settings machine rows. */
    val RadiusCard = 14.dp

    /** Corner radius of controls: buttons, form fields, chips, dialog action rows. Was a bare
     * `12.dp` literal repeated ~50 times across every dialog/sheet before this token existed —
     * distinct from RadiusCard on purpose (controls read tighter than list cards at the same
     * radius because they're smaller), not a drift that needs correcting to 14.dp. */
    val RadiusControl = 12.dp

    /** Horizontal drag distance at which a card swipe commits its action. Shared between
     * RadarCard's bespoke swipe and the generic SwipeableCard so all cards feel identical. */
    val SwipeThreshold = 88.dp

    /** Maximum distance a card can be dragged past its resting position. */
    val SwipeMax = 132.dp

    // Spacing scale (Material's 4dp grid) — introduced for the Woven Interface redesign pass.
    // Apply to components as they're touched during redesign; not retrofitted across the whole
    // app in one go (a follow-up consistency pass does that once the redesign is stable).
    val Space4 = 4.dp
    val Space6 = 6.dp
    val Space8 = 8.dp
    val Space10 = 10.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
}
