package com.jekael.adoel.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Soft shadow in both modes — kept small/subtle: this is a secondary depth cue layered on top
// of the tonal system below, not a replacement for it.
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
 * Floating header/console-bar card. Shared by MainScreenHeader, ConsoleBar, MesinDrawer/
 * PengaturanDrawer's header, StatistikScreen's header, and
 * [com.jekael.adoel.ui.components.FloatingEditDialog] —
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
 *
 * [borderColor] overrides the default [LocalAppColors.border]; [dashedBorder] swaps the solid
 * ring for a dashed one — together these mark a row as needing attention (e.g. a stopped machine
 * row in MesinTab) without a second, differently-styled card variant to keep in sync.
 */
@Composable
fun Modifier.elevatedListCard(
    backgroundColor: Color,
    borderColor: Color? = null,
    dashedBorder: Boolean = false,
): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Dimens.RadiusCard)
    val resolvedBorderColor = borderColor ?: colors.border
    return this
        .softCardShadow(shape)
        .clip(shape)
        .let {
            if (dashedBorder) it.dashedRoundedBorder(resolvedBorderColor, Dimens.RadiusCard)
            else it.border(1.dp, resolvedBorderColor, shape)
        }
        .background(backgroundColor)
}

/**
 * Glossy variant of [elevatedListCard]: a subtle top-lit vertical gradient (from [baseColor] to a
 * touch darker at the bottom) plus a thin top-edge sheen, instead of [elevatedListCard]'s flat
 * tone — AI Studio's `.machine-list-item`/`.radar-card-front` card treatment, ported where asked
 * (RadarCard's front face, Riwayat's row) rather than swapped in everywhere: most of the app still
 * reads its depth off [elevatedListCard]'s flat tonal system, and this is a second variant living
 * alongside it, not a replacement for it.
 */
@Composable
fun Modifier.glossyListCard(
    baseColor: Color,
    borderColor: Color? = null,
): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Dimens.RadiusCard)
    val gradient = Brush.verticalGradient(listOf(baseColor, lerp(baseColor, Color.Black, 0.04f)))
    return this
        .softCardShadow(shape)
        .clip(shape)
        .background(gradient)
        .drawWithContent {
            drawContent()
            val y = 0.5.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        .border(1.dp, borderColor ?: colors.border, shape)
}

/**
 * Tactile "pressable button" treatment for small actionable chips/pills — shortcut chips, filter
 * chips, radar card action chips. These used to read as flat labels: a washed-out, low-alpha tint
 * of [baseColor] with only a thin border for definition, easy to mistake for a plain informational
 * tag (a tipe badge, say, which really is one and stays that way). This gives them the bold,
 * mostly-opaque [baseColor] fill a pressable control needs, plus the same lifted-surface language
 * as [glossyListCard] scaled down to chip size (a top-lit gradient, a thin top sheen line, a soft
 * shadow) so they read as buttons, not tags.
 */
@Composable
fun Modifier.tactilePill(baseColor: Color, shape: Shape = RoundedCornerShape(6.dp)): Modifier {
    val gradient = Brush.verticalGradient(listOf(lerp(baseColor, Color.White, 0.14f), baseColor))
    return this
        .shadow(elevation = 2.dp, shape = shape, clip = false)
        .clip(shape)
        .background(gradient)
        .drawWithContent {
            drawContent()
            val y = 0.5.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        .border(1.dp, lerp(baseColor, Color.Black, 0.3f), shape)
}

/**
 * Squish-on-press scale feedback — the give a real rubber button has, not just a ripple wash:
 * dips to [pressedScale] the instant [interactionSource] reports a press, springs back the
 * instant it's released or cancelled. Chain this *before* [tactilePill] (and before whatever
 * modifier owns [interactionSource] — clickable/selectable/a Surface's own onClick) so the
 * outer graphicsLayer scales the whole tactilePill visual — shadow, gradient, border — as one
 * piece, not just its content.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.94f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** [tactilePill] plus [pressScale] plus the [clickable] that drives both, bundled into the one
 * chain every actionable tactilePill site wants — own [MutableInteractionSource] created and
 * shared between the two, ripple traded for the scale feedback alone (a rubber button doesn't
 * also wash with a ripple) so the two feedback languages don't compete on the same tap. */
@Composable
fun Modifier.tactilePillClickable(
    baseColor: Color,
    shape: Shape = RoundedCornerShape(6.dp),
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource)
        .tactilePill(baseColor, shape)
        .clickable(interactionSource = interactionSource, indication = null, onClickLabel = onClickLabel, onClick = onClick)
}

private fun Modifier.dashedRoundedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier =
    this.drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
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
