package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.AktualEntry
import com.jekael.adoel.data.ShiftRecord
import com.jekael.adoel.data.getRepresentativeEpochMin
import com.jekael.adoel.data.parseJam
import com.jekael.adoel.data.shiftNumberForEpochMin
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Amber500
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.Emerald400
import com.jekael.adoel.ui.theme.LocalAppColors
import com.jekael.adoel.ui.theme.tactilePill
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class HourlyPoint(
    val hourIndex: Int,
    val hour: Int,
    val label: String,
    val fullLabel: String,
    val count: Int,
    val kumulatif: Int,
    val machines: List<String>,
)

private enum class TrendViewMode { BOTH, HOURLY, CUMULATIVE }

/** Per-jam produksi dalam satu shift — area cyan untuk output jam-per-jam, garis putus-putus
 * emerald untuk akumulasi total. Digambar manual lewat [Canvas] (bukan library chart) supaya
 * konsisten dengan grafik lain di aplikasi ini yang semuanya hand-rolled — Android-only, tidak
 * ada padanan di web. Kurva dan sumbunya sengaja dibuat semirip mungkin dengan chart library
 * seperti recharts (garis melengkung, bukan lurus antar titik, plus label sumbu X/Y) meski
 * di-tangan sendiri. */
@Composable
fun ShiftHourlyTrendChart(shift: ShiftRecord, modifier: Modifier = Modifier) {
    if (shift.aktual.isEmpty()) return
    val colors = LocalAppColors.current
    var viewMode by remember { mutableStateOf(TrendViewMode.BOTH) }
    var selectedIndex by remember(shift.id) { mutableStateOf<Int?>(null) }

    val hourlyData = remember(shift) {
        val repEpoch = getRepresentativeEpochMin(shift)
        val shiftNo = shiftNumberForEpochMin(repEpoch)
        // Jadwal shift tetap 8 jam — dipakai sebagai urutan kanonis jam-jam pada shift ini
        // (termasuk yang melewati tengah malam untuk Shift 3), bukan sekadar diurutkan numerik.
        val baseHours = when (shiftNo) {
            1 -> listOf(6, 7, 8, 9, 10, 11, 12, 13)
            2 -> listOf(14, 15, 16, 17, 18, 19, 20, 21)
            else -> listOf(22, 23, 0, 1, 2, 3, 4, 5)
        }
        val hourMap = LinkedHashMap<Int, MutableList<AktualEntry>>()
        baseHours.forEach { hourMap[it] = mutableListOf() }
        for (entry in shift.aktual) {
            val min = parseJam(entry.jam)
            val ts = entry.tsEpochMin
            val h = when {
                min != null -> min / 60
                ts != null -> Calendar.getInstance().apply { timeInMillis = ts * 60000L }.get(Calendar.HOUR_OF_DAY)
                else -> continue
            }
            hourMap.getOrPut(h) { mutableListOf() }.add(entry)
        }
        val orderedHours = hourMap.keys.sortedWith(
            compareBy(
                { val idx = baseHours.indexOf(it); if (idx == -1) Int.MAX_VALUE else idx },
                { it },
            ),
        )
        var running = 0
        orderedHours.mapIndexed { i, h ->
            val entries = hourMap[h].orEmpty()
            running += entries.size
            val nextH = (h + 1) % 24
            HourlyPoint(
                hourIndex = i + 1,
                hour = h,
                label = "%02d.00".format(h),
                fullLabel = "Jam %02d.00–%02d.00 (Jam ke-${i + 1})".format(h, nextH),
                count = entries.size,
                kumulatif = running,
                machines = entries.map { it.mcNo },
            )
        }
    }

    val peak = remember(hourlyData) { hourlyData.maxByOrNull { it.count } }
    val avgPerHour = remember(hourlyData, shift.aktual.size) {
        shift.aktual.size.toFloat() / max(1, hourlyData.size)
    }

    // Fades + rises in fresh every time this composes — which, since it only exists inside the
    // shift row's AnimatedVisibility, is exactly every time that row is expanded (matches web's
    // recharts, which auto-animates the Area/Line in on mount with no extra wiring needed there).
    val entranceAlpha = remember { Animatable(0f) }
    val entranceOffsetY = remember { Animatable(10f) }
    LaunchedEffect(Unit) {
        launch { entranceAlpha.animateTo(1f, tween(320)) }
        entranceOffsetY.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceAlpha.value
                translationY = entranceOffsetY.value
            }
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(colors.bgElevated2)
            .padding(Dimens.Space12),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space10),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = Cyan400, modifier = Modifier.size(15.dp))
                Text("Tren Doffing Per Jam", style = AppType.CaptionBold.copy(color = colors.textPrimary))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TrendModeChip(
                    label = "Semua",
                    selected = viewMode == TrendViewMode.BOTH,
                    modifier = Modifier.weight(1f),
                ) { viewMode = TrendViewMode.BOTH }
                TrendModeChip(
                    label = "Per Jam",
                    selected = viewMode == TrendViewMode.HOURLY,
                    modifier = Modifier.weight(1f),
                ) { viewMode = TrendViewMode.HOURLY }
                TrendModeChip(
                    label = "Kumulatif",
                    selected = viewMode == TrendViewMode.CUMULATIVE,
                    modifier = Modifier.weight(1f),
                ) { viewMode = TrendViewMode.CUMULATIVE }
            }
        }

        // Metrik ringkas
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            TrendMetric(modifier = Modifier.weight(1f), label = "Total Shift", value = "${shift.aktual.size} doff", color = Cyan400)
            TrendMetric(
                modifier = Modifier.weight(1f),
                label = "Puncak Output",
                value = if (peak != null && peak.count > 0) "${peak.label} (${peak.count})" else "—",
                color = Amber500,
            )
            TrendMetric(
                modifier = Modifier.weight(1f),
                label = "Rata-rata",
                value = "${"%.1f".format(avgPerHour)}/jam",
                color = Emerald400,
            )
        }

        TrendChartCanvas(
            data = hourlyData,
            viewMode = viewMode,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it },
        )

        val selected = selectedIndex?.let { hourlyData.getOrNull(it) }
        if (selected != null) {
            TrendTooltipCard(point = selected)
        }

        // Legenda — hanya seri yang sedang ditampilkan
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
            if (viewMode != TrendViewMode.CUMULATIVE) {
                LegendItem(color = Cyan400, label = "Output Jam Ini")
            }
            if (viewMode != TrendViewMode.HOURLY) {
                LegendItem(color = Emerald400, label = "Akumulasi Total", dashed = true)
            }
        }
    }
}

@Composable
private fun TrendModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .tactilePill(if (selected) Cyan600 else colors.bgElevated2)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else colors.textFaint,
            ),
        )
    }
}

@Composable
private fun TrendMetric(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, style = TextStyle(fontSize = 9.5.sp, color = colors.textFaint))
        Text(value, style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = color), maxLines = 1)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(if (dashed) RoundedCornerShape(2.dp) else CircleShape)
                .background(color),
        )
        Text(label, style = TextStyle(fontSize = 10.5.sp, color = colors.textFaint))
    }
}

@Composable
private fun TrendTooltipCard(point: HourlyPoint) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgElevated)
            .padding(Dimens.Space10),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(point.fullLabel, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
            Text("Output jam ini: ${point.count} doff", style = TextStyle(fontSize = 11.sp, color = Cyan400, fontWeight = FontWeight.SemiBold))
            Text("Akumulasi: ${point.kumulatif} doff", style = TextStyle(fontSize = 11.sp, color = Emerald400, fontWeight = FontWeight.SemiBold))
        }
        if (point.machines.isNotEmpty()) {
            Text(
                "Mesin: ${point.machines.joinToString(", ") { "Mc $it" }}",
                style = TextStyle(fontSize = 10.5.sp, color = colors.textFaint),
            )
        }
    }
}

// Reserved margins for the Y-axis value labels (left) and X-axis hour labels (bottom) — shared
// between the draw scope and the tap-detection scope below, which can't share a local val since
// they're two separate lambdas (DrawScope vs PointerInputScope).
private const val CHART_LEFT_PAD_DP = 26f
private const val CHART_BOTTOM_PAD_DP = 22f
private const val CHART_TOP_PAD_DP = 12f

/** Appends a smooth curve through [points] onto [path], which must already be positioned at
 * `points.first()` (via a preceding moveTo/lineTo) — a Catmull-Rom spline, converted to cubic
 * Bézier segments (the standard 1/6-tangent form). Each segment's own start/end *is* one of
 * [points] exactly, unlike a quadratic-through-midpoints shortcut (an earlier version of this
 * function used exactly that, and it visibly cut inside every data point instead of passing
 * through it — reported as the plotted dots not lining up with the curve drawn through them) —
 * Catmull-Rom is the standard spline family for precisely this "smooth, but still interpolates
 * every real data point" requirement. */
private fun appendSmoothCurve(path: Path, points: List<Offset>) {
    if (points.size < 2) return
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

/** Picks up to [targetCount] indices out of `0 until total`, spread as evenly as possible and
 * always including both 0 and `total - 1` — used to thin a row of axis labels too narrow to all
 * show at once. Spacing by *position* (not a fixed "every Nth" stride) matters here: a stride
 * leaves an uneven, oddly-bunched gap right at the end whenever `total - 1` isn't a clean multiple
 * of it — this instead lands each pick as close to its ideal evenly-spaced slot as rounding
 * allows. */
private fun evenlySpacedIndices(total: Int, targetCount: Int): Set<Int> {
    if (total <= targetCount) return (0 until total).toSet()
    return (0 until targetCount)
        .map { i -> (i * (total - 1).toFloat() / (targetCount - 1)).roundToInt() }
        .toSet()
}

@Composable
private fun TrendChartCanvas(
    data: List<HourlyPoint>,
    viewMode: TrendViewMode,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val colors = LocalAppColors.current
    val showHourly = viewMode != TrendViewMode.CUMULATIVE
    val showCumulative = viewMode != TrendViewMode.HOURLY
    val maxVal = remember(data, viewMode) {
        max(
            1,
            max(
                if (showHourly) data.maxOfOrNull { it.count } ?: 0 else 0,
                if (showCumulative) data.maxOfOrNull { it.kumulatif } ?: 0 else 0,
            ),
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = TextStyle(fontSize = 9.sp, color = colors.textFaint)

    // Progressive left-to-right reveal, matching recharts' default Area/Line mount animation on
    // web — re-fires on every fresh composition of this Canvas (see the entrance animation above
    // in the parent composable for why that lines up with "card just opened").
    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80)
        revealProgress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(data) {
                detectTapGestures { offset ->
                    if (data.isEmpty()) return@detectTapGestures
                    val leftPad = CHART_LEFT_PAD_DP.dp.toPx()
                    val step = (size.width - leftPad) / data.size
                    val idx = ((offset.x - leftPad) / step).toInt().coerceIn(0, data.size - 1)
                    onSelect(if (selectedIndex == idx) null else idx)
                }
            },
    ) {
        if (data.isEmpty()) return@Canvas
        val topPad = CHART_TOP_PAD_DP.dp.toPx()
        val bottomPad = CHART_BOTTOM_PAD_DP.dp.toPx()
        val leftPad = CHART_LEFT_PAD_DP.dp.toPx()
        val chartHeight = size.height - topPad - bottomPad
        val step = (size.width - leftPad) / data.size
        fun xFor(i: Int) = leftPad + step * i + step / 2f
        fun yFor(v: Int) = topPad + chartHeight * (1f - v.toFloat() / maxVal)
        val baselineY = size.height - bottomPad

        // Y-axis: a faint gridline + value at 0, half, and max — drawn before the reveal clip so
        // the chart's chrome reads immediately instead of wiping in with the data.
        listOf(0, maxVal / 2, maxVal).distinct().forEach { tickVal ->
            val y = yFor(tickVal)
            drawLine(
                color = colors.border.copy(alpha = 0.35f),
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val measured = textMeasurer.measure(tickVal.toString(), axisLabelStyle)
            drawText(textLayoutResult = measured, topLeft = Offset(leftPad - 6.dp.toPx() - measured.size.width, y - measured.size.height / 2f))
        }

        // X-axis: the hour label under every point would crowd 8 columns into illegible overlap
        // (the same clipping problem the Statistik shift-history chart had), so only up to 5
        // spread evenly across the row get a label — always keeping the first and the last. Picks
        // by *position*, not a fixed "every Nth" stride — a stride leaves an uneven, oddly-bunched
        // gap right at the end whenever data.size - 1 isn't a clean multiple of it.
        val shownHourIndices = evenlySpacedIndices(data.size, targetCount = 5)
        data.forEachIndexed { i, p ->
            if (i in shownHourIndices) {
                val measured = textMeasurer.measure(p.label, axisLabelStyle)
                drawText(textLayoutResult = measured, topLeft = Offset(xFor(i) - measured.size.width / 2f, baselineY + 6.dp.toPx()))
            }
        }

        clipRect(right = size.width * revealProgress.value) {
            if (showHourly) {
                val points = data.mapIndexed { i, p -> Offset(xFor(i), yFor(p.count)) }
                val fillPath = Path().apply {
                    moveTo(points[0].x, baselineY)
                    lineTo(points[0].x, points[0].y)
                    appendSmoothCurve(this, points)
                    lineTo(points.last().x, baselineY)
                    close()
                }
                drawPath(fillPath, color = Cyan500.copy(alpha = 0.18f))

                val linePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    appendSmoothCurve(this, points)
                }
                drawPath(linePath, color = Cyan400, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                points.forEachIndexed { i, pt ->
                    drawCircle(Cyan400, radius = if (i == selectedIndex) 5.dp.toPx() else 3.dp.toPx(), center = pt)
                }
            }

            if (showCumulative) {
                val points = data.mapIndexed { i, p -> Offset(xFor(i), yFor(p.kumulatif)) }
                val linePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    appendSmoothCurve(this, points)
                }
                drawPath(
                    linePath,
                    color = Emerald400,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    ),
                )
                points.forEachIndexed { i, pt ->
                    drawCircle(Emerald400, radius = if (i == selectedIndex) 4.5.dp.toPx() else 2.5.dp.toPx(), center = pt)
                }
            }
        }

        // Garis penanda titik terpilih
        selectedIndex?.let { i ->
            val x = xFor(i)
            drawLine(
                color = colors.textFaint.copy(alpha = 0.4f),
                start = Offset(x, topPad),
                end = Offset(x, baselineY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
    }
}
