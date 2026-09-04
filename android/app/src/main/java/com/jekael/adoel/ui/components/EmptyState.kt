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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
    // Web's equivalent (empty-state-title) always pairs the title with a small icon (RadarIcon,
    // HistoryIcon, ...) — optional here since a few call sites (search/filter "not found") don't.
    titleIcon: ImageVector? = null,
    // Overrides the plain [subtitle] Text when set — lets a call site build its own richer copy,
    // e.g. web's inline icon+word pill mid-sentence (see InlineActionPill) that a plain String
    // can't express.
    subtitleContent: (@Composable () -> Unit)? = null,
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (titleIcon != null) {
                    Icon(imageVector = titleIcon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                }
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
            }
            if (subtitleContent != null) {
                subtitleContent()
            } else {
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
}

/** Web's `.inline-icon-pill` — a small icon+word chip embedded mid-sentence in an empty-state
 * subtitle (see RadarSection/DoffingSection's "Estimasi"/"Doffing" pills), naming the exact
 * console action the copy just described so it reads as pointing at that button, not just a
 * plain word. Built with [InlineTextContent] since Compose Text has no direct equivalent of a
 * CSS inline-block span mixed into wrapped text. */
@Composable
fun InlineActionPillSubtitle(
    before: String,
    pillIcon: ImageVector,
    pillLabel: String,
    pillAccent: Color,
    after: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val pillId = "action_pill"
    val annotated = buildAnnotatedString {
        append(before)
        appendInlineContent(pillId, "[$pillLabel]")
        append(after)
    }
    // Sized generously (icon + label at their natural size rarely exceed ~70dp) rather than
    // measured exactly — a little extra breathing room in the reserved box reads better than a
    // clipped pill if the estimate runs short.
    val pillWidth = with(density) { (pillLabel.length * 6 + 34).dp.toSp() }
    val pillHeight = with(density) { 19.dp.toSp() }
    val inlineContent = mapOf(
        pillId to InlineTextContent(
            placeholder = Placeholder(width = pillWidth, height = pillHeight, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(pillAccent.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = pillIcon, contentDescription = null, tint = pillAccent, modifier = Modifier.size(11.dp))
                Text(pillLabel, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = pillAccent))
            }
        },
    )
    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = AppType.Caption.copy(color = colors.textMuted, lineHeight = 18.sp),
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
