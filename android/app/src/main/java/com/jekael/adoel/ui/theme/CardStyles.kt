package com.jekael.adoel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Soft shadow in both modes — now that dark mode's background (see DarkBg in Theme.kt) is an
// actual dark gray rather than near-black, a shadow reads against it same as light mode. Kept
// small/subtle either way: this is a secondary depth cue layered on top of the tonal system
// below, not a replacement for it.
private val CardShadowElevation = 3.dp

@Composable
private fun Modifier.softCardShadow(shape: RoundedCornerShape): Modifier =
    this.shadow(elevation = CardShadowElevation, shape = shape, clip = false)

/**
 * Tonal elevation as the primary depth cue: depth/hierarchy is read from how much a surface's
 * background tone has lifted off [LocalAppColors.bg]. A thin border stands in for the extra
 * separation a stronger shadow would give; [softCardShadow] layers a subtle shadow on top in
 * both modes.
 *
 * Floating header/console-bar card. Shared by MainScreenHeader, ConsoleBar, SettingsDrawer's
 * header, StatistikScreen's header, and [com.jekael.adoel.ui.components.FloatingEditDialog] —
 * kept in one place so the look can't drift between them. Border/background always come from
 * [LocalAppColors] at every current call site, so they're read here directly rather than threaded
 * through as parameters.
 */
@Composable
fun Modifier.floatingHeaderCard(): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Dimens.RadiusFloating)
    return this
        .softCardShadow(shape)
        .clip(shape)
        .border(1.dp, colors.border, shape)
        .background(colors.bgElevated)
}

/**
 * Tonal list-row card: rounded + background + a thin border, plus a subtle shadow in light mode
 * only (see [floatingHeaderCard] doc). [backgroundColor] carries the main hierarchy signal — a
 * plain row passes [LocalAppColors.bgElevated]/`bgElevated2`, while RadarCard tints it toward its
 * urgency color (see `urgency()` in RadarCard.kt) — but the border is what keeps a card readable
 * as "raised" even where the tonal jump off [LocalAppColors.bg] is subtle (e.g. light theme).
 */
@Composable
fun Modifier.elevatedListCard(backgroundColor: Color): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Dimens.RadiusCard)
    return this
        .softCardShadow(shape)
        .clip(shape)
        .border(1.dp, colors.border, shape)
        .background(backgroundColor)
}

/**
 * Soft top/bottom fade so list content doesn't cut off with a hard edge as it scrolls behind the
 * floating header/console — items ease into the screen background instead of disappearing at a
 * sharp line (Master Blueprint v9.2 §10). Not a true backdrop blur of the card itself (that would
 * need capturing the scrolled content into a render layer, only feasible on API 31+) — a plain
 * gradient scrim reads as the same soft transition and works identically on every device. Drawn as
 * a sibling positioned over the list but under the header/console card, so declare it after the
 * scrollable content and before the floating card in a Box's children.
 */
@Composable
fun BoxScope.EdgeFadeScrim(atTop: Boolean, height: Dp) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = if (atTop) {
                        listOf(colors.bg, colors.bg.copy(alpha = 0f))
                    } else {
                        listOf(colors.bg.copy(alpha = 0f), colors.bg)
                    },
                ),
            ),
    )
}
