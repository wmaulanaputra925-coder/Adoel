package com.jekael.adoel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Path
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
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.Emerald400
import com.jekael.adoel.ui.theme.Emerald500
import com.jekael.adoel.ui.theme.LocalAppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Which illustration sits inside the empty state's dashed ring. Every variant shares that ring
 * and the dark disc behind it so the empty states still read as one family — only the field
 * inside differs, and each one animates whatever that particular screen is waiting for. */
enum class EmptyStateArt {
    /** Wound-thread spool, breathing. The neutral default for states that aren't waiting on the
     * mill floor at all (search found nothing, no shift archived yet). */
    SPOOL,

    /** Sweeping beam with blips that light as it passes — Radar's "siap memantau": the screen is
     * watching an empty floor, not idle. */
    RADAR,

    /** Loom shuttle flying across the warp, paying out weft over cloth already woven — Riwayat's
     * "belum ada potongan": the machines are still weaving, nothing has been cut to log yet. */
    WEAVING,
}

/** Empty-state block: an animated illustration inside a dashed woven ring, plus title/subtitle
 * copy that matches the guided-only workflow (Master Blueprint v9.2 §11) — no more "proses baris"
 * wording left over from the old free-text console. [art] picks the illustration; see
 * [EmptyStateArt] for what each one says. */
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
    art: EmptyStateArt = EmptyStateArt.SPOOL,
) {
    val colors = LocalAppColors.current

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

            when (art) {
                EmptyStateArt.SPOOL -> SpoolArt()
                EmptyStateArt.RADAR -> RadarSweepArt()
                EmptyStateArt.WEAVING -> WeavingArt()
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

/** [EmptyStateArt.SPOOL] — the wound-thread spool: flanges either side of a dense band of thread,
 * breathing with a slow pulse. */
@Composable
private fun SpoolArt() {
    val colors = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "spoolPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(colors.bgElevated2.copy(alpha = 0.35f)),
    )
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

/** Where the [EmptyStateArt.RADAR] blips sit, as (bearing in degrees, distance as a fraction of
 * the field's radius) — fixed positions rather than random ones so the illustration animates the
 * same way every time it appears instead of reshuffling on each recomposition. */
private val RadarBlips = listOf(38f to 0.62f, 155f to 0.42f, 262f to 0.74f)

/** [EmptyStateArt.RADAR] — range rings the beam sweeps over, with each blip flaring as the beam
 * crosses it and fading out over the rest of the revolution, the way a real scope holds a
 * contact between passes. */
@Composable
private fun RadarSweepArt() {
    val colors = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "radarSweep")
    val sweepDeg by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepDeg",
    )

    Canvas(modifier = Modifier.size(76.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val hairline = 1.dp.toPx()

        // Static grid the beam reads against.
        drawCircle(colors.border.copy(alpha = 0.55f), radius = r * 0.62f, center = center, style = Stroke(hairline))
        drawCircle(colors.border.copy(alpha = 0.40f), radius = r * 0.30f, center = center, style = Stroke(hairline))
        drawLine(colors.border.copy(alpha = 0.30f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), hairline)
        drawLine(colors.border.copy(alpha = 0.30f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), hairline)

        // The beam, as a short trail of wedges fading out behind the leading edge — cheaper and
        // more controllable than a sweep-gradient brush, and it reads identically at this size.
        val trailSegments = 14
        val segmentDeg = 5f
        for (i in 0 until trailSegments) {
            drawArc(
                color = Cyan500.copy(alpha = 0.26f * (1f - i / trailSegments.toFloat())),
                startAngle = sweepDeg - (i + 1) * segmentDeg,
                // Overlap each wedge slightly so the trail has no hairline seams between steps.
                sweepAngle = segmentDeg + 0.6f,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
            )
        }
        val leadRad = (sweepDeg * PI / 180f).toFloat()
        drawLine(
            color = Cyan400.copy(alpha = 0.8f),
            start = center,
            end = Offset(center.x + cos(leadRad) * r, center.y + sin(leadRad) * r),
            strokeWidth = 1.5.dp.toPx(),
        )

        RadarBlips.forEach { (bearing, distance) ->
            // How far behind the beam this blip is right now; it flares at 0 and decays from there.
            val behind = (((sweepDeg - bearing) % 360f) + 360f) % 360f
            val fade = (1f - behind / 300f).coerceAtLeast(0f)
            val alpha = fade * fade
            if (alpha > 0.01f) {
                val rad = (bearing * PI / 180f).toFloat()
                val at = Offset(center.x + cos(rad) * r * distance, center.y + sin(rad) * r * distance)
                drawCircle(Cyan400.copy(alpha = alpha * 0.22f), radius = 6.dp.toPx(), center = at)
                drawCircle(Cyan400.copy(alpha = alpha), radius = 2.2.dp.toPx(), center = at)
            }
        }

        drawCircle(Cyan400.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = center)
    }
}

/** [EmptyStateArt.WEAVING] — warp under tension, cloth already beaten in below, and a shuttle
 * flying pick after pick across the shed. Emerald rather than the spool's cyan, matching the
 * Doffing console action this screen's copy points at. */
@Composable
private fun WeavingArt() {
    val colors = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "weaving")
    // A 0→2 sawtooth, not a 0→1 Reverse: reversing hides which way the shuttle is travelling, and
    // the weft it pays out has to trail back to the edge it actually left from.
    val pass by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pass",
    )

    Canvas(modifier = Modifier.size(76.dp).clip(CircleShape)) {
        val w = size.width
        val h = size.height
        val inset = 4.dp.toPx()
        val left = inset
        val right = w - inset

        val warpCount = 9
        for (i in 0 until warpCount) {
            val x = left + (right - left) * i / (warpCount - 1).toFloat()
            drawLine(colors.border, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
        }

        // Cloth already woven — denser toward the bottom, where it has been beaten in longest.
        for (i in 0 until 4) {
            drawLine(
                color = Emerald500.copy(alpha = 0.24f + 0.09f * i),
                start = Offset(left, h * 0.58f + h * 0.11f * i),
                end = Offset(right, h * 0.58f + h * 0.11f * i),
                strokeWidth = 2.5.dp.toPx(),
            )
        }

        val movingRight = pass < 1f
        val travelled = if (movingRight) pass else 2f - pass
        val x = left + (right - left) * travelled
        val y = h * 0.40f
        drawLine(
            color = Emerald400,
            start = Offset(if (movingRight) left else right, y),
            end = Offset(x, y),
            strokeWidth = 2.dp.toPx(),
        )

        val tip = 9.dp.toPx()
        val belly = 4.dp.toPx()
        val shuttle = Path().apply {
            moveTo(x - tip, y)
            lineTo(x, y - belly)
            lineTo(x + tip, y)
            lineTo(x, y + belly)
            close()
        }
        drawPath(shuttle, Emerald400)
        // The pirn showing through the shuttle's window.
        drawCircle(colors.bg, radius = 1.4.dp.toPx(), center = Offset(x, y))
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
