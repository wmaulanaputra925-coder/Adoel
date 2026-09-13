package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.Red500
import kotlinx.coroutines.launch

/** Shared swipe-to-act gesture + reveal background for list cards outside RadarCard (which has
 * its own richer "doff" completion celebration — slide-out, checkmark pop — since that swipe
 * actually removes the card immediately). Everywhere else (Doffing rows, archived Statistik
 * shifts) a swipe just triggers a callback and settles back: hapus goes through a confirm dialog
 * the caller supplies, and edit/bagikan don't remove anything from the list. Uses the exact same
 * drag threshold/max-distance/spring physics as RadarCard so every swipeable card in the app
 * feels identical even though the implementations are separate. */
@Composable
fun SwipeableCard(
    modifier: Modifier = Modifier,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    rightIcon: ImageVector,
    leftIcon: ImageVector = Icons.Outlined.Delete,
    rightColor: Color = Cyan600,
    leftColor: Color = Red500,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { Dimens.SwipeThreshold.toPx() }
    val maxPx = with(density) { Dimens.SwipeMax.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun settle() {
        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeActionBackground(
            offsetX = offsetX.value,
            thresholdPx = thresholdPx,
            rightIcon = rightIcon,
            leftIcon = leftIcon,
            rightColor = rightColor,
            leftColor = leftColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offsetX.value
                            when {
                                value >= thresholdPx -> { onSwipeRight(); settle() }
                                value <= -thresholdPx -> { onSwipeLeft(); settle() }
                                else -> settle()
                            }
                        },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-maxPx, maxPx))
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
