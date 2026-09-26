package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.AktualEntry
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.ui.theme.Amber400
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Emerald400
import com.jekael.adoel.ui.theme.LocalAppColors

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes. Each caller still supplies its own container: Riwayat wraps this in a
 * swipeable list card, Statistik in a flat tappable strip inside the shift card.
 *
 * One row: No/Mc badges on the left (the thing you're actually scanning the column for), then a
 * text column with Corak on top and Panjang/Jam/Keterangan as pills below. Corak and keterangan
 * are the only free-typed (variable-length) fields, so they're the only two that ever ellipsize;
 * everything else is short, fixed-format text that always fits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoffEntryRowContent(
    num: Int,
    entry: AktualEntry,
    mesin: MesinData?,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val corak = entry.corakOverride ?: mesin?.corak ?: "—"
    val yard = entry.customYard ?: mesin?.targetYard
    // entry.ket is "$jam($extra)" when the doff carried a keterangan, or bare "$jam" when it
    // didn't (see DoffViewModel.prosesBarisUmum). The time has its own chip below, so strip it
    // back off here and keep only the code — otherwise the row prints the clock twice.
    val ketCode = entry.ket.removePrefix(entry.jam).removeSurrounding("(", ")")
    val ketUpper = ketCode.uppercase()
    // Only MATCHING and HB get a tinted pill — Emerald matches every other Matching indicator in
    // the app (the corner ribbon, the doff celebration), Amber flags HB. Ordinary free-typed
    // keterangan (P.LP, GANTI BEAM, etc) stays an untinted pill like Jam/Panjang next to it,
    // rather than borrowing a status color that isn't really a status.
    val ketColor = when {
        ketUpper.contains("MATCH") -> Emerald400
        ketUpper.contains("HB") -> Amber400
        else -> null
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlossyBadgeBox(label = "NO", value = "$num", boxWidth = 42.dp, valueFontSize = 15.sp)
        McBadgeBox(mcNo = entry.mcNo)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Texture,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    corak,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // FlowRow, not Row — matches web's `.meta-tags { flex-wrap: wrap }` exactly. A plain
            // Row let Keterangan get pushed past the card's clipped right edge on narrower rows
            // (corak+yard+jam alone can already fill the width), rendering as an invisible sliver:
            // background visible, but its text positioned off-card. Wrapping to a second line
            // keeps every pill fully on-screen instead.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetaTagPill(
                    icon = Icons.Outlined.Straighten,
                    text = if (yard != null) "${formatYard(yard)}y" else "—",
                    tint = Cyan400,
                )
                MetaTagPill(icon = Icons.Outlined.Schedule, text = entry.jam, tint = null)
                if (ketCode.isNotEmpty()) {
                    MetaTagPill(
                        icon = null,
                        text = ketCode,
                        tint = ketColor,
                        modifier = Modifier.widthIn(max = 110.dp),
                    )
                }
            }
        }

        if (onEdit != null) {
            EditCircleButton(onClick = onEdit)
        }
    }
}

/** Edit-pencil affordance at the row's trailing edge — visible alongside whatever the caller's
 * own container already does for editing (a whole-row tap, or Riwayat's swipe-right), the same
 * way web keeps its `action-circle-btn` next to an equally-clickable row. Sized 40dp so the touch
 * target clears the ~44dp minimum this app uses elsewhere, even though the glossy circle itself
 * reads smaller (web's own 32px circle is undersized against the 44px standard it promotes for
 * every other tap target — not copied here). */
@Composable
private fun EditCircleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(colors.bgElevated, Color.White, 0.10f), colors.bgElevated)))
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit",
                tint = colors.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** One meta pill (Panjang/Jam/Keterangan) below the corak line — untinted (neutral) when [tint]
 * is null, otherwise washed in [tint] for the yard chip and for a flagged (MATCHING/HB)
 * keterangan. */
@Composable
private fun MetaTagPill(
    icon: ImageVector?,
    text: String,
    tint: Color?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(6.dp)
    val fg = tint ?: colors.textSecondary
    val bg = tint?.copy(alpha = 0.16f) ?: colors.bgElevated
    val border = tint?.copy(alpha = 0.35f) ?: colors.border
    Row(
        modifier = modifier
            .height(22.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        }
        Text(
            text,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
