package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Dimens
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Two-option pill toggle with an animated sliding indicator that also follows a
 * drag/tap anywhere across its width, like a physical slider.
 */
@Composable
fun SlidingToggle(
    labelLeft: String,
    labelRight: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    containerColor: Color,
    activeColorLeft: Color,
    activeColorRight: Color,
    activeTextColorLeft: Color,
    activeTextColorRight: Color,
    inactiveTextColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    // Optional label naming what this toggle controls (e.g. "Mode Estimasi/Doffing") — TalkBack
    // announces it alongside the Switch role and the current side's label (read via
    // stateDescription below), so a screen-reader user hears both what it is and what it's set to.
    accessibilityLabel: String? = null,
    iconLeft: ImageVector? = null,
    iconRight: ImageVector? = null,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val currentSelected = rememberUpdatedState(selectedIndex)
    val currentOnSelect = rememberUpdatedState(onSelect)

    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            position.animateTo(selectedIndex.toFloat(), animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (selectedIndex == 0) labelLeft else labelRight
                accessibilityLabel?.let { contentDescription = it }
            }
            .background(containerColor, RoundedCornerShape(50.dp))
            .padding(Dimens.Space4),
    ) {
        val segmentWidth = maxWidth / 2
        val widthPx = with(density) { maxWidth.toPx() }
        val pos = position.value

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(widthPx) {
                    if (widthPx <= 0f) return@pointerInput
                    // A fast flick should carry momentum toward the side it was thrown, like a
                    // real toggle — not just snap to whichever half the finger happened to be
                    // over when it lifted. VelocityTracker measures that throw speed, and the
                    // settle spring is given it as its initial velocity so the indicator keeps
                    // moving on release instead of stopping dead.
                    val flingThresholdPxPerSec = 900.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        val pointerId = down.id
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        scope.launch { position.snapTo((down.position.x / widthPx).coerceIn(0f, 1f)) }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val frac = (change.position.x / widthPx).coerceIn(0f, 1f)
                            scope.launch { position.snapTo(frac) }
                        }
                        val velocityPxPerSec = tracker.calculateVelocity().x
                        val nearest = when {
                            velocityPxPerSec > flingThresholdPxPerSec -> 1
                            velocityPxPerSec < -flingThresholdPxPerSec -> 0
                            else -> position.value.roundToInt().coerceIn(0, 1)
                        }
                        // dragging stays true through the momentum settle below — onSelect (called
                        // right after) changes selectedIndex, which reruns LaunchedEffect(selectedIndex);
                        // if dragging had already flipped false, that effect's plain non-bouncy spring
                        // would immediately cancel and replace this fling animation before it's visible.
                        if (nearest != currentSelected.value) currentOnSelect.value(nearest)
                        scope.launch {
                            position.animateTo(
                                targetValue = nearest.toFloat(),
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                initialVelocity = velocityPxPerSec / widthPx,
                            )
                            dragging = false
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * pos)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(50.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (pos < 0.5f) activeColorLeft else activeColorRight),
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    val leftColor = if (pos < 0.5f) activeTextColorLeft else inactiveTextColor
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (iconLeft != null) {
                            Icon(iconLeft, contentDescription = null, tint = leftColor, modifier = Modifier.size(15.dp))
                        }
                        Text(labelLeft, style = AppType.TabLabel.copy(color = leftColor))
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    val rightColor = if (pos >= 0.5f) activeTextColorRight else inactiveTextColor
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (iconRight != null) {
                            Icon(iconRight, contentDescription = null, tint = rightColor, modifier = Modifier.size(15.dp))
                        }
                        Text(labelRight, style = AppType.TabLabel.copy(color = rightColor))
                    }
                }
            }
        }
    }
}
