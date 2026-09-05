package com.jekael.adoel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import com.jekael.adoel.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Slide-in-from-right full-screen panel shell — mount-on-first-frame enter animation, BackHandler,
 * and a slide-out exit that only calls [onClose] once the exit animation actually finishes (so the
 * caller unmounting this composable via its own open/closed flag doesn't cut the exit short). Used
 * identically by MesinDrawer, PengaturanDrawer, and StatistikScreen; both durations come from a
 * single source ([Motion.PANEL_ENTER_MS]/[Motion.PANEL_EXIT_MS]) so they can't drift apart.
 *
 * [content] receives `requestClose` — call it to play the exit animation and then invoke [onClose].
 * A caller that needs its own escape hatch (e.g. MesinDrawer/PengaturanDrawer's swipe-to-dismiss,
 * which provides its own slide-out via drag) can still call the original [onClose] directly to
 * skip this shell's animation, exactly as before this was extracted.
 */
@Composable
fun SlidePanel(
    onClose: () -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    fun requestClose() {
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(Motion.PANEL_EXIT_MS.toLong())
            onClose()
        }
    }

    BackHandler(enabled = visible) { requestClose() }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(Motion.PANEL_ENTER_MS)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(Motion.PANEL_EXIT_MS)),
    ) {
        content(::requestClose)
    }
}

/**
 * Swipe-right-to-dismiss for a [SlidePanel]'s own root — drag the whole page along with the
 * finger, and past 30% of its width let go and it leaves. Shared by Pengaturan, Daftar Mesin and
 * Statistik so closing any of the three full-screen pages is the same motion.
 *
 * Deliberately drives its own offset instead of reusing [rubberBandSwipe]: a card's swipe is a
 * *reveal* that snaps back and fires an action, so it compresses past a commit point; a page's
 * swipe is the page actually leaving, so it must track the finger 1:1 all the way out or the
 * gesture feels like it's fighting back. Calls [onClose] directly (not SlidePanel's requestClose)
 * because the drag has already played the exit — running the shell's slide-out on top of it would
 * animate an already-offscreen panel.
 */
fun Modifier.swipeRightToClose(onClose: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var panelWidthPx by remember { mutableFloatStateOf(0f) }

    this
        .onGloballyPositioned { panelWidthPx = it.size.width.toFloat() }
        .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val width = if (panelWidthPx > 0f) panelWidthPx else 1f
                    if (dragOffset.value > width * 0.3f) {
                        scope.launch {
                            dragOffset.animateTo(width, animationSpec = tween(Motion.PANEL_EXIT_MS))
                            onClose()
                        }
                    } else {
                        scope.launch {
                            dragOffset.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
                onDragCancel = {
                    scope.launch { dragOffset.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
                },
            ) { change, dragAmount ->
                change.consume()
                // Left-edge drags are clamped to 0: this panel can only leave to the right, and
                // letting it drift left would tear a gap open at the screen edge.
                val newVal = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                scope.launch { dragOffset.snapTo(newVal) }
            }
        }
}
