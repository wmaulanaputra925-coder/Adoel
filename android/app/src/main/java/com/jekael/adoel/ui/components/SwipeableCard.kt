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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.Red500
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Shared swipe-to-act gesture + reveal background for list cards outside RadarCard (which has
 * its own richer "doff" completion celebration — slide-out, checkmark pop — since that swipe
 * actually removes the card immediately). Everywhere else (Doffing rows) a swipe just triggers a
 * callback and settles back: hapus goes through a confirm dialog the caller supplies, and
 * edit/bagikan don't remove anything from the list. Uses the exact same drag threshold/
 * max-distance/spring physics as RadarCard so every swipeable card in the app feels identical
 * even though the implementations are separate.
 *
 * [onSwipeRight]/[rightIcon] are optional — Riwayat used to swipe-right into a whole-row edit
 * dialog, but every field there now has its own tap target (DoffEntryRowContent's onEditTipe/
 * onEditCorak/onEditYard/onEditTime/onEditKet), so that swipe direction is dead weight now, not
 * a second way in. Leaving [onSwipeRight] null blocks rightward drag entirely (rubber-banding to
 * 0 instead of revealing anything) rather than keeping a swipe that visually arms but does
 * nothing on release.
 *
 * Statistik's archived shift cards deliberately do *not* use this: they carry visible Bagikan/
 * Hapus buttons, and the horizontal drag over them closes the page instead (swipeRightToClose). */
@Composable
fun SwipeableCard(
    modifier: Modifier = Modifier,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: () -> Unit,
    rightIcon: ImageVector? = null,
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
    val haptic = LocalHapticFeedback.current
    // Raw finger travel for this drag — see RadarCard for why the compressed offset can't be fed
    // back into rubberBandSwipe.
    var rawDragX by remember { mutableFloatStateOf(0f) }
    val armed = abs(offsetX.value) >= thresholdPx
    LaunchedEffect(armed) {
        if (armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun settle() {
        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeActionBackground(
            offsetX = offsetX.value,
            thresholdPx = thresholdPx,
            // Never actually drawn when onSwipeRight is null — rightward drag is clamped to 0
            // below, so offsetX never goes positive and SwipeActionBackground's own isRight
            // branch never fires. leftIcon is just a harmless placeholder to satisfy the type.
            rightIcon = rightIcon ?: leftIcon,
            leftIcon = leftIcon,
            rightColor = rightColor,
            leftColor = leftColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetX.value }
                // Keyed on nullness, not the lambda itself — a fresh onSwipeRight lambda from a
                // recomposing caller would otherwise restart this pointerInput (and drop any
                // in-progress drag) every single recomposition, since lambda instances are never
                // struct-equal across recompositions the way a Boolean is.
                .pointerInput(onSwipeRight != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offsetX.value
                            when {
                                onSwipeRight != null && value >= thresholdPx -> { onSwipeRight(); settle() }
                                value <= -thresholdPx -> { onSwipeLeft(); settle() }
                                else -> settle()
                            }
                        },
                        onDragStart = { rawDragX = offsetX.value },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rawDragX += dragAmount
                            if (onSwipeRight == null) rawDragX = rawDragX.coerceAtMost(0f)
                            scope.launch {
                                offsetX.snapTo(rubberBandSwipe(rawDragX, thresholdPx, maxPx))
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
