package com.jekael.adoel.ui.components

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
    val bg = if (isRight) rightColor else leftColor
    val label = if (isRight) rightLabel else leftLabel
    val description = if (isRight) rightDescription else leftDescription
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(bg.copy(alpha = 0.18f + 0.6f * progress)),
        contentAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = 0.6f + 0.4f * progress
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
