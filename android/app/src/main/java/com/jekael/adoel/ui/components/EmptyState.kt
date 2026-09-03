package com.jekael.adoel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.LocalAppColors

/** Empty-state illustration: a woven-thread bobbin/spool that breathes with a slow pulse, plus
 * title/subtitle copy that matches the guided-only workflow (Master Blueprint v9.2 §11) — no more
 * "proses baris" wording left over from the old free-text console. */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = "Belum Ada Pantauan",
    subtitle: String = "Gunakan konsol bawah untuk memulai tindakan.",
) {
    val colors = LocalAppColors.current

    // Slow breathing pulse on the wound-thread spool at the center.
    val infiniteTransition = rememberInfiniteTransition(label = "spoolPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
            val warpColor = colors.border
            val weftColor = lerp(colors.border, Cyan500, 0.40f)

            Canvas(modifier = Modifier.size(112.dp)) {
                val strokeW = 1.5.dp.toPx()
                val radius = size.minDimension / 2f - strokeW
                val dash = 10.dp.toPx()
                val gap = 6.dp.toPx()

                drawCircle(
                    color = warpColor,
                    radius = radius,
                    style = Stroke(width = strokeW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase = 0f)),
                )
                drawCircle(
                    color = weftColor,
                    radius = radius,
                    style = Stroke(width = strokeW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase = dash)),
                )
            }

            Box(Modifier.size(80.dp).clip(CircleShape).background(colors.bg))
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(colors.bgElevated2.copy(alpha = 0.35f)),
            )

            // The wound-thread spool itself — flanges either side of a dense band of thread.
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
            ) {
                val w = size.width
                val h = size.height
                val spoolH = h * 0.45f
                val spoolW = w * 0.70f
                val flangeW = w * 0.08f

                drawRoundRect(
                    color = colors.border,
                    topLeft = Offset((w - spoolW) / 2f - flangeW, (h - spoolH) / 2f - 4.dp.toPx()),
                    size = Size(flangeW, spoolH + 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
                drawRoundRect(
                    color = colors.border,
                    topLeft = Offset((w + spoolW) / 2f, (h - spoolH) / 2f - 4.dp.toPx()),
                    size = Size(flangeW, spoolH + 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )

                drawRoundRect(
                    color = Cyan500,
                    topLeft = Offset((w - spoolW) / 2f, (h - spoolH) / 2f),
                    size = Size(spoolW, spoolH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )

                val threadGap = 4.dp.toPx()
                var x = (w - spoolW) / 2f + threadGap
                while (x < (w + spoolW) / 2f) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(x, (h - spoolH) / 2f),
                        end = Offset(x, (h + spoolH) / 2f),
                        strokeWidth = 1.dp.toPx(),
                    )
                    x += threadGap
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = Dimens.Space24),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.2).sp,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = AppType.Caption.copy(
                    color = colors.textMuted,
                    lineHeight = 18.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}
