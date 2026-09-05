package com.jekael.adoel.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * Where the card should sit for a finger that has travelled [raw] px. Up to the commit point the
 * card tracks the finger exactly; past it, further travel compresses toward [maxPx] and never
 * reaches it, so the card keeps answering the drag instead of hitting the dead stop a plain
 * coerceIn gave it. Shared by RadarCard and [SwipeableCard] so every swipe in the app pulls the
 * same way — pass the raw accumulated drag, not the already-compressed offset, or the curve
 * compounds on itself and dragging back feels sticky.
 */
fun rubberBandSwipe(raw: Float, thresholdPx: Float, maxPx: Float): Float {
    val magnitude = abs(raw)
    if (magnitude <= thresholdPx) return raw
    val room = (maxPx - thresholdPx).coerceAtLeast(1f)
    val past = magnitude - thresholdPx
    return sign(raw) * (thresholdPx + room * (1f - exp(-past / room)))
}

/** Reveal panel drawn behind a card as it's dragged past the swipe threshold — an icon (plus an
 * optional label and description under it) over a color wash that intensifies with drag progress.
 * Shared by [SwipeableCard] and RadarCard's own swipe handling (which additionally animates a
 * doff-completion slide-out on release, so it can't just delegate the whole gesture to
 * [SwipeableCard]), so the reveal itself can't drift apart between the two. [rightDescription]/
 * [leftDescription] are only used by RadarCard's Normal/Matching swipe (SwipeableCard's plain
 * share/delete reveal has no second line worth adding).
 */
@Composable
fun BoxScope.SwipeActionBackground(
    offsetX: Float,
    thresholdPx: Float,
    rightIcon: ImageVector,
    leftIcon: ImageVector,
    rightColor: Color,
    leftColor: Color,
    rightLabel: String? = null,
    leftLabel: String? = null,
    rightDescription: String? = null,
    leftDescription: String? = null,
) {
    if (abs(offsetX) < 1f) return
    val isRight = offsetX > 0
    val progress = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
    // Past the commit point the drag itself stops saying anything new — progress is already capped
    // and the card barely moves — so the panel takes over: the wash deepens and the icon pops, and
    // it holds that state until the finger is pulled back below the line. Without it there was no
    // way to tell a release would actually fire the action until after letting go.
    val armed = abs(offsetX) >= thresholdPx
    val armedProgress by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = tween(140),
        label = "swipeArmed",
    )
    val bg = if (isRight) rightColor else leftColor
    val label = if (isRight) rightLabel else leftLabel
    val description = if (isRight) rightDescription else leftDescription
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(bg.copy(alpha = 0.18f + 0.55f * progress + 0.22f * armedProgress)),
        contentAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    val scale = 0.6f + 0.4f * progress + 0.12f * armedProgress
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Icon(
                imageVector = if (isRight) rightIcon else leftIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            if (label != null) {
                Text(
                    text = label,
                    style = AppType.LabelSmallBold.copy(color = Color.White),
                    modifier = Modifier.padding(top = Dimens.Space4),
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = AppType.Caption.copy(color = Color.White.copy(alpha = 0.88f)),
                )
            }
        }
    }
}
