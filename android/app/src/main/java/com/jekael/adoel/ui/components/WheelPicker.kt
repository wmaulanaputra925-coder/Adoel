package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
// Wildcard, not individually-named imports — a single-name `import
// androidx.compose.foundation.layout.weight` resolves to the wrong symbol (an internal
// `RowColumnParentData.weight` property that happens to share the name) instead of the actual
// public `RowScope.weight` modifier, and fails to compile with "it is internal in file". Every
// other file in this codebase already imports this package as a wildcard for the same reason.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One vertical scroll wheel over [range] — drag up/down to cycle values (with real momentum: a
 * quick flick keeps spinning and decelerates into place, the iOS clock/timer feel), or tap any
 * visible row to jump straight to it.
 *
 * Built on [androidx.compose.foundation.gestures.scrollable] rather than a hand-rolled
 * `pointerInput`+`detectVerticalDragGestures` (an earlier version of this file did exactly that,
 * and it's why it doesn't compile that way anymore) — every field-edit sheet's content
 * ([FloatingEditDialog]) sits inside its own `Modifier.verticalScroll`, a safety net for content
 * taller than the screen. A raw `pointerInput` child doesn't participate in Compose's nested
 * scroll negotiation at all, so that ancestor scroll intermittently stole this wheel's drags out
 * from under it (reported: scrolling down didn't register at all, scrolling up barely did).
 * `scrollable()` is the sanctioned building block for exactly this "custom scrollable that must
 * coexist with an ancestor scrollable" situation, and its own [ScrollableDefaults.flingBehavior]
 * gives real platform-matching momentum for free — no manual `VelocityTracker`/`animateDecay`
 * needed. (This still deliberately avoids the separate *list-snapping* APIs,
 * `rememberSnapFlingBehavior` and friends — a different, less-common Foundation API,
 * `stickyHeader`, already failed to resolve against this project's exact Compose BOM once this
 * session and cost two CI cycles chasing it; `scrollable()` itself is a much longer-established,
 * non-list-specific primitive, so it doesn't carry that same risk.)
 *
 * The wheel's own [anchorIndex] only changes once scrolling has fully settled (drag released,
 * fling decayed to a stop) — but [onValueChange] fires live as each row is crossed mid-gesture,
 * the same "selection follows the wheel as it turns" behavior a real picker has. Rendering
 * derives which rows are visible from the *raw, unbounded* live offset every frame (see
 * `liveIndexNow`), so a long drag or a fast flick keeps resolving to further rows for as long as
 * it's moving, instead of running out past a small fixed window around a stale center.
 */
@Composable
fun WheelColumn(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleCount: Int = 5,
    format: (Int) -> String = { it.toString().padStart(2, '0') },
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val scope = rememberCoroutineScope()

    // The committed center when idle. `liveOffsetPx` is how far an active scroll/fling has moved
    // away from it — deliberately unbounded (not clamped to one item's height), so a fast flick
    // can keep resolving to further rows for as long as it's still decaying — and is updated
    // synchronously from scrollable()'s own (non-suspend) consume callback below. `settleOffset`
    // takes over, as an Animatable, only for the final spring onto the exact row once scrolling
    // has fully stopped — the two are never both "live" at once (see `isSettling`).
    var anchorIndex by remember(range) { mutableIntStateOf(value.coerceIn(range)) }
    var liveOffsetPx by remember(range) { mutableFloatStateOf(0f) }
    val settleOffset = remember { Animatable(0f) }
    var isSettling by remember { mutableStateOf(false) }

    fun currentOffsetPx(): Float = if (isSettling) settleOffset.value else liveOffsetPx

    // Defined as a function, not a `val` — it needs to read *current* state at the moment it's
    // called, including from inside a coroutine that started earlier (e.g. the settle effect
    // below), which Compose only guarantees fresh for state reads (property access), not for a
    // plain local captured by value when that coroutine started.
    fun liveIndexNow(): Int =
        (anchorIndex + (-currentOffsetPx() / itemHeightPx).roundToInt()).coerceIn(range)

    val scrollableState = rememberScrollableState { delta ->
        if (isSettling) return@rememberScrollableState 0f
        liveOffsetPx += delta
        delta
    }

    // The caller can move `value` out from under this wheel while it's idle — e.g. the paired
    // minute wheel getting pinned to :00 once the hour wheel hits its cap — so re-center to
    // match. Guarded so it never fights an active scroll/settle, which drives `value` itself via
    // the live-update effect below (this wheel's own change echoing back in).
    LaunchedEffect(value, range) {
        if (!scrollableState.isScrollInProgress && !isSettling && value.coerceIn(range) != anchorIndex) {
            anchorIndex = value.coerceIn(range)
            liveOffsetPx = 0f
        }
    }

    val liveIndex = liveIndexNow()
    // Bounded to roughly ±half an item's height by construction of the round() in liveIndexNow —
    // the remainder after "how many whole rows has this offset moved past" is extracted out.
    val fractionalOffsetPx = currentOffsetPx() + (liveIndex - anchorIndex) * itemHeightPx

    LaunchedEffect(liveIndex) {
        if (liveIndex != value) onValueChange(liveIndex)
    }
    // One light tick per row crossed while a scroll/fling is actually moving the wheel — the
    // same per-row feedback a real picker gives, not just a silent slide.
    var lastTickedIndex by remember { mutableIntStateOf(anchorIndex) }
    LaunchedEffect(liveIndex, scrollableState.isScrollInProgress) {
        if (scrollableState.isScrollInProgress && liveIndex != lastTickedIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastTickedIndex = liveIndex
    }

    // Re-anchors to `finalIndex` without a visual jump: the fractional remainder at the moment
    // this is called becomes the new starting point for the settle spring, since that's exactly
    // how far off-center the wheel currently sits *relative to the row it's about to commit to*.
    suspend fun settleTo(finalIndex: Int) {
        val settleFromPx = currentOffsetPx() + (finalIndex - anchorIndex) * itemHeightPx
        anchorIndex = finalIndex
        liveOffsetPx = 0f
        isSettling = true
        settleOffset.snapTo(settleFromPx)
        settleOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        isSettling = false
    }

    // Once scrollable()'s own drag+fling has fully settled (finger lifted and any momentum has
    // decayed to a stop), snap the resting position onto the exact row instead of wherever the
    // fling happened to run out — the one piece scrollable() doesn't do for us on its own.
    LaunchedEffect(scrollableState.isScrollInProgress) {
        if (!scrollableState.isScrollInProgress) {
            settleTo(liveIndexNow())
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .scrollable(
                state = scrollableState,
                orientation = Orientation.Vertical,
                // Dragging down should bring the *previous* (smaller) value to the center — the
                // same physical-dial feel every other swipe in this app uses — which is the
                // opposite of scrollable()'s own default sense for a downward drag.
                reverseDirection = true,
                flingBehavior = ScrollableDefaults.flingBehavior(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Center highlight — the row the scroll/fling settles onto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.bgElevated2),
        )
        val halfWindowPx = itemHeightPx * (visibleCount / 2f)
        Column(modifier = Modifier.graphicsLayer { translationY = fractionalOffsetPx }) {
            for (i in -(visibleCount / 2)..(visibleCount / 2)) {
                val idx = liveIndex + i
                // Continuous distance of *this row* from dead-center, accounting for the live
                // scroll/fling offset — not just its fixed slot index — so the tilt reads as one
                // smoothly turning cylinder while dragging, not five rows that suddenly reflow
                // once a row-crossing snaps. t is -1 at the top edge row, 0 dead-center, +1 at
                // the bottom edge row.
                val continuousDistancePx = i * itemHeightPx + fractionalOffsetPx
                val t = (continuousDistancePx / halfWindowPx).coerceIn(-1f, 1f)
                val centered = i == 0
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Wraps each row's flat text plane onto the surface of an implied
                            // drum: rotationX tilts it away from the viewer the further it sits
                            // from center, cameraDistance keeps that tilt reading as perspective
                            // instead of a squash (see RadarCard's own graphicsLayer for why it's
                            // `this.density`, GraphicsLayerScope's own property, and not the
                            // outer LocalDensity-derived `density` val — that one silently
                            // resolves to the wrong receiver and fails to typecheck).
                            rotationX = t * WHEEL_MAX_TILT_DEG
                            cameraDistance = 12 * this.density
                            alpha = 1f - abs(t) * 0.65f
                            scaleX = 1f - abs(t) * 0.12f
                        }
                        // Tap any visible row (besides the one already centered) to jump straight
                        // to it — the same shortcut a real picker offers alongside dragging.
                        // Coexists fine with the container's own scrollable(): a tap and a drag
                        // are mutually exclusive within a single gesture, the same as any
                        // clickable row inside a scrollable list.
                        .then(
                            if (!centered && idx in range) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = format(idx),
                                    onClick = { scope.launch { settleTo(idx) } },
                                )
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (idx in range) {
                        Text(
                            text = format(idx),
                            style = TextStyle(
                                fontSize = if (centered) 20.sp else 15.sp,
                                fontWeight = if (centered) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                color = if (centered) colors.textPrimary else colors.textFaint,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** How far a row at the very top/bottom edge of the visible window tilts away from the viewer —
 * an iOS clock/timer wheel's own drum-like curvature, not the flat stacked-list look a plain
 * translated Column reads as on its own. */
private const val WHEEL_MAX_TILT_DEG = 55f

/**
 * Hour:Minute wheel pair for Terpandu ESTIMASI's TAPPET/CAM ("sisa waktu", capped at [maxHour])
 * and D408 ("bacaan jam counter", the plain 0–23 clock) fields — a "set a timer/clock" gesture
 * instead of typing an H.MM string neither field's plain-number keyboard made self-explanatory
 * (D408 in particular reads as a counter value until you already know it's H.MM, not a raw
 * number). D405's field (yard sudah berjalan) isn't a time at all, so it keeps its own plain
 * numeric field instead of using this.
 *
 * Once the hour wheel reaches [maxHour], the minute wheel is pinned to :00 so the combined value
 * can never read past it (e.g. exactly 8 jam, never 8 jam 59) — for D408's plain clock, pass 23
 * so the cap never actually binds.
 */
@Composable
fun HourMinuteWheelPicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    maxHour: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val minuteRange = if (hour >= maxHour) 0..0 else 0..59
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WheelColumn(
            range = 0..maxHour,
            value = hour,
            onValueChange = { h ->
                onHourChange(h)
                if (h >= maxHour && minute != 0) onMinuteChange(0)
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            ":",
            modifier = Modifier.width(12.dp),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.textFaint),
        )
        WheelColumn(
            range = minuteRange,
            value = minute.coerceIn(minuteRange),
            onValueChange = onMinuteChange,
            modifier = Modifier.weight(1f),
        )
    }
}
