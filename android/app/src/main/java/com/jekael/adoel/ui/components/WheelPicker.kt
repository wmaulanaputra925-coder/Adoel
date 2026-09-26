package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One vertical scroll wheel over [range] — drag up/down to cycle values, snapping to the
 * nearest whole step on release (no fling/momentum: this is a "drag to the value you want and
 * let go" gesture, not a long flick-scroll list). Built on plain drag-detection + a spring
 * settle — the same primitives RadarCard's own swipe already uses — rather than Compose's
 * snap-fling APIs, since a less-common Foundation API (`stickyHeader`) already failed to resolve
 * against this project's exact Compose BOM once this session and cost two CI cycles chasing it;
 * this sticks to primitives already proven to compile here.
 *
 * [value] is a controlled prop — the caller owns state and re-renders with the committed value
 * once [onValueChange] fires (on release, not on every drag frame), the same "commit on
 * settle" shape [HourMinuteWheelPicker] relies on to derive one combined string only once the
 * operator has actually picked a number.
 */
@Composable
fun WheelColumn(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 36.dp,
    visibleCount: Int = 5,
    format: (Int) -> String = { it.toString().padStart(2, '0') },
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val values = remember(range) { range.toList() }
    val clamped = value.coerceIn(range)

    val dragOffset = remember { Animatable(0f) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // The value can change out from under an in-progress (or just-settled) gesture — e.g. the
    // paired minute wheel snapping back to :00 once the hour wheel hits its cap — so re-center
    // whenever it moves, instead of only ever doing it from inside this wheel's own onDragEnd.
    LaunchedEffect(value, range) { dragOffset.snapTo(0f) }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .pointerInput(range) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumPx = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumPx += dragAmount
                        scope.launch { dragOffset.snapTo(dragAccumPx) }
                    },
                    onDragEnd = {
                        // Dragging down moves the wheel's visible content down, which brings the
                        // *previous* (smaller) value to the center — the index moves opposite the
                        // drag direction, same as a real dial.
                        val steps = -(dragAccumPx / itemHeightPx).roundToInt()
                        if (steps != 0) {
                            val currentIndex = values.indexOf(clamped).coerceAtLeast(0)
                            val nextIndex = (currentIndex + steps).coerceIn(values.indices)
                            val next = values[nextIndex]
                            if (next != value) onValueChange(next)
                        }
                        scope.launch { dragOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy)) }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy)) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Center highlight — the row the drag settles onto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.bgElevated2),
        )
        val currentIndex = values.indexOf(clamped).coerceAtLeast(0)
        Column(modifier = Modifier.graphicsLayer { translationY = dragOffset.value }) {
            for (offset in -(visibleCount / 2)..(visibleCount / 2)) {
                val v = values.getOrNull(currentIndex + offset)
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (v != null) {
                        val centered = offset == 0
                        Text(
                            text = format(v),
                            style = TextStyle(
                                fontSize = if (centered) 19.sp else 15.sp,
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
