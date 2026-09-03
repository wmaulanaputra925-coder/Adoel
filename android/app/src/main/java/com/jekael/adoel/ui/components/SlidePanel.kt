package com.jekael.adoel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jekael.adoel.ui.theme.Motion
import kotlinx.coroutines.delay

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
