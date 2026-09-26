package com.jekael.adoel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.ui.theme.Fuchsia500
import com.jekael.adoel.ui.theme.Indigo500
import com.jekael.adoel.ui.theme.LocalAppColors
import com.jekael.adoel.ui.theme.Red400
import com.jekael.adoel.ui.theme.Teal500
import com.jekael.adoel.ui.theme.Violet500
import com.jekael.adoel.ui.theme.Zinc500

/** One color per [MesinTipe], reused everywhere a machine's type needs a visual tag (RadarCard,
 * DoffingSection's row dot, Pengaturan > Mesin, Statistik breakdown) — a single source so the
 * "color of a machine type" language can't drift between screens. Deliberately kept off the 4
 * status hues (green/amber/red/blue-cyan, see Theme.kt) so a machine-type badge never reads as
 * a status at a glance. */
fun mesinTipeColor(tipe: MesinTipe?): Color = when (tipe) {
    MesinTipe.TAPPET -> Teal500
    MesinTipe.CAM -> Violet500
    MesinTipe.D405 -> Indigo500
    MesinTipe.D408 -> Fuchsia500
    null -> Zinc500
}

/** One icon per [MesinTipe] — drawn as one family instead of four unrelated glyphs: the same
 * low horizontal machine body (a real WJL's silhouette from the side, not a generic Material
 * icon) with one element on top that's the actual mechanical difference between the four real
 * machines on the mill floor — a single tappet rod, a cam wheel, or a dobby head (405 with 2
 * hooks, 408 with the same head but 4, since they're the same head design at different
 * capacity, not two unrelated machines). Purely a visual identifier, no semantic meaning beyond
 * "this is a different kind of machine at a glance". */
@Composable
fun MesinTipeIcon(tipe: MesinTipe, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val bodyStroke = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(
                color = tint,
                topLeft = Offset(3f, 13f),
                size = Size(18f, 6.5f),
                cornerRadius = CornerRadius(1.5f, 1.5f),
                style = bodyStroke,
            )
            drawLine(tint, Offset(6.5f, 19.5f), Offset(6.5f, 21.3f), strokeWidth = 1.75f, cap = StrokeCap.Round)
            drawLine(tint, Offset(17.5f, 19.5f), Offset(17.5f, 21.3f), strokeWidth = 1.75f, cap = StrokeCap.Round)
            when (tipe) {
                MesinTipe.TAPPET -> {
                    drawLine(tint, Offset(12f, 13f), Offset(12f, 6f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                    drawCircle(tint, radius = 1.3f, center = Offset(12f, 5f), style = Stroke(width = 1.75f))
                }
                MesinTipe.CAM -> {
                    drawCircle(tint, radius = 3.3f, center = Offset(12f, 9.3f), style = Stroke(width = 1.75f))
                }
                MesinTipe.D405 -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(8.5f, 4f),
                        size = Size(7f, 4.5f),
                        cornerRadius = CornerRadius(1f, 1f),
                        style = bodyStroke,
                    )
                    drawLine(tint, Offset(10.2f, 8.5f), Offset(10.2f, 13f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(13.8f, 8.5f), Offset(13.8f, 13f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                }
                MesinTipe.D408 -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(8.5f, 4f),
                        size = Size(7f, 4.5f),
                        cornerRadius = CornerRadius(1f, 1f),
                        style = bodyStroke,
                    )
                    listOf(9.6f, 11.2f, 12.8f, 14.4f).forEach { x ->
                        drawLine(tint, Offset(x, 8.5f), Offset(x, 13f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
fun CheckIcon() {
    Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = "Doff",
        modifier = Modifier.size(20.dp),
    )
}

/** Explicit [Red400] tint, not the ambient [LocalContentColor][androidx.compose.material3.LocalContentColor]
 * default — an untinted Icon here rendered as a faint near-black smudge in dark mode (nothing
 * about a delete action should ever be hard to see), the same red every other destructive
 * action in the app already uses (the dropdown's "Hapus Semua", Statistik's per-row delete). */
@Composable
fun TrashIcon(size: Dp = 18.dp, tint: Color = Red400) {
    Icon(
        imageVector = Icons.Outlined.Delete,
        contentDescription = "Hapus",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
fun GearIcon() {
    Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = "Pengaturan",
        tint = LocalAppColors.current.textPrimary,
        modifier = Modifier.size(20.dp),
    )
}


@Composable
fun CloseIcon() {
    Icon(
        imageVector = Icons.Outlined.Close,
        contentDescription = "Tutup",
        tint = LocalAppColors.current.textMuted,
        modifier = Modifier.size(18.dp),
    )
}
