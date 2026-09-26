package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
 * One vertical scroll wheel over [range] — drag up/down to cycle values, with real momentum: a
 * quick flick keeps spinning past the finger's last position and decelerates into place, the
 * same feel as iOS's clock/timer wheel, rather than only moving exactly as far as the finger
 * dragged. Built on plain drag-detection + [androidx.compose.animation.core.exponentialDecay] +
 * a spring settle — not Compose's snap-fling *list* APIs (`rememberSnapFlingBehavior` and
 * friends) — since a different, less-common Foundation API (`stickyHeader`) already failed to
 * resolve against this project's exact Compose BOM once this session and cost two CI cycles
 * chasing it; `animateDecay`/`exponentialDecay` are much longer-established animation-core
 * primitives, not list-snapping ones, so they don't carry that same risk.
 *
 * The wheel's own [anchorIndex] only ever changes value once a gesture (drag, then its fling, if
 * any) has fully settled — but [onValueChange] fires live as each row is crossed mid-gesture, the
 * same "selection follows the wheel as it turns" behavior a real picker has, rather than only
 * reporting a value once the finger lifts. Rendering derives which rows are visible from the
 * *raw, unbounded* drag/fling offset every frame (see `liveIndexNow`) instead of a small fixed
 * window of items around a value that only updates on release — the earlier version rendered
 * exactly 5 fixed rows around a stale center and ran out of rows (showing blank space) past two
 * rows of drag, which is what actually made it feel stiff, not the lack of momentum alone.
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

    // The committed center when idle; `offset` is how far a live drag/fling has moved away from
    // it, in raw px — deliberately unbounded (not clamped to one item's height) so a fast flick
    // can keep resolving to further rows for as long as it's still decaying.
    var anchorIndex by remember(range) { mutableIntStateOf(value.coerceIn(range)) }
    val offset = remember(range) { Animatable(0f) }
    var isGestureActive by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    // Defined as a function, not a `val` — it needs to read the *current* anchorIndex/offset at
    // the moment it's called, including from inside onDragEnd's closure, which Compose can
    // otherwise only guarantee for state reads (property access), not for a plain local captured
    // by value when the gesture's coroutine started.
    fun liveIndexNow(): Int =
        (anchorIndex + (-offset.value / itemHeightPx).roundToInt()).coerceIn(range)

    // The caller can move `value` out from under this wheel while it's idle — e.g. the paired
    // minute wheel getting pinned to :00 once the hour wheel hits its cap — so re-center to
    // match. Guarded by isGestureActive so it never fights an active drag/fling, which drives
    // `value` itself via the live-update effect below (this wheel's own change echoing back in).
    LaunchedEffect(value, range) {
        if (!isGestureActive && value.coerceIn(range) != anchorIndex) {
            anchorIndex = value.coerceIn(range)
            offset.snapTo(0f)
        }
    }

    val liveIndex = liveIndexNow()
    // Bounded to roughly ±half an item's height by construction of the round() in liveIndexNow —
    // the remainder after "how many whole rows has this offset moved past" is extracted out.
    val fractionalOffsetPx = offset.value + (liveIndex - anchorIndex) * itemHeightPx

    LaunchedEffect(liveIndex) {
        if (liveIndex != value) onValueChange(liveIndex)
    }
    // One light tick per row crossed while a gesture is actually moving the wheel — the same
    // per-row feedback a real picker gives, not just a silent slide.
    var lastTickedIndex by remember { mutableIntStateOf(anchorIndex) }
    LaunchedEffect(liveIndex, isGestureActive) {
        if (isGestureActive && liveIndex != lastTickedIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastTickedIndex = liveIndex
    }

    // Re-anchors to `finalIndex` without a visual jump: the fractional remainder at the moment
    // this is called becomes the new starting point for the settle spring, since that's exactly
    // how far off-center the wheel currently sits *relative to the row it's about to commit to*.
    suspend fun settleTo(finalIndex: Int) {
        val settleFromPx = offset.value + (finalIndex - anchorIndex) * itemHeightPx
        anchorIndex = finalIndex
        offset.snapTo(settleFromPx)
        offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .pointerInput(range) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isGestureActive = true
                        scope.launch { offset.stop() }
                        velocityTracker.resetTracking()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        // Dragging down brings the *previous* (smaller) value to the center — the
                        // index moves opposite the drag direction, same as a real dial.
                        scope.launch { offset.snapTo(offset.value - dragAmount) }
                    },
                    onDragEnd = {
                        val flingVelocity = -velocityTracker.calculateVelocity().y
                        scope.launch {
                            val atLowEdge = liveIndexNow() == range.first && flingVelocity < 0
                            val atHighEdge = liveIndexNow() == range.last && flingVelocity > 0
                            if (abs(flingVelocity) > 60f && !atLowEdge && !atHighEdge) {
                                offset.animateDecay(flingVelocity, exponentialDecay(frictionMultiplier = 3.5f))
                            }
                            settleTo(liveIndexNow())
                            isGestureActive = false
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            settleTo(liveIndexNow())
                            isGestureActive = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Center highlight — the row the drag/fling settles onto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.bgElevated2),
        )
        Column(modifier = Modifier.graphicsLayer { translationY = fractionalOffsetPx }) {
            for (i in -(visibleCount / 2)..(visibleCount / 2)) {
                val idx = liveIndex + i
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (idx in range) {
                        val centered = i == 0
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
