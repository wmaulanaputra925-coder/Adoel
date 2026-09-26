package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    onEditTipe: (() -> Unit)? = null,
    onEditCorak: (() -> Unit)? = null,
    onEditYard: (() -> Unit)? = null,
    onEditTime: (() -> Unit)? = null,
    onEditKet: (() -> Unit)? = null,
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
        McBadgeBox(
            mcNo = entry.mcNo,
            modifier = onEditTipe?.let {
                Modifier.clickable(onClickLabel = "Ubah tipe mesin Mc ${entry.mcNo}", onClick = it)
            } ?: Modifier,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = onEditCorak?.let {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "Ubah corak Mc ${entry.mcNo}", onClick = it)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } ?: Modifier,
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
                    onClick = onEditYard,
                    onClickLabel = "Ubah panjang Mc ${entry.mcNo}",
                )
                MetaTagPill(
                    icon = Icons.Outlined.Schedule,
                    text = entry.jam,
                    tint = null,
                    onClick = onEditTime,
                    onClickLabel = "Ubah jam Mc ${entry.mcNo}",
                )
                if (ketCode.isNotEmpty()) {
                    MetaTagPill(
                        icon = null,
                        text = ketCode,
                        tint = ketColor,
                        onClick = onEditKet,
                        onClickLabel = "Ubah keterangan Mc ${entry.mcNo}",
                        modifier = Modifier.widthIn(max = 110.dp),
                    )
                } else if (onEditKet != null) {
                    AddKeteranganPill(onClick = onEditKet, mcNo = entry.mcNo)
                }
            }
        }
    }
}

/** One meta pill (Panjang/Jam/Keterangan) below the corak line — untinted (neutral) when [tint]
 * is null, otherwise washed in [tint] for the yard chip and for a flagged (MATCHING/HB)
 * keterangan. Tappable (with the same border, just no fill-in feedback beyond the ripple) when
 * [onClick] is supplied — Riwayat/Statistik wire one per field, RadarCard's own pills stay
 * display-only. */
@Composable
private fun MetaTagPill(
    icon: ImageVector?,
    text: String,
    tint: Color?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
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
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
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

/** "+ Keterangan" pill shown in the keterangan slot in place of [MetaTagPill] when the entry
 * carries none yet — a dashed cyan-tinted invitation to add one, Android equivalent of web's
 * `.meta-tag.ket.add-ket-btn` (DoffEntryRow.tsx/index.css). */
@Composable
private fun AddKeteranganPill(onClick: () -> Unit, mcNo: String) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(shape)
            .background(Cyan400.copy(alpha = 0.08f))
            .border(1.dp, Cyan400.copy(alpha = 0.45f), shape)
            .clickable(onClickLabel = "Tambah keterangan Mc $mcNo", onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Cyan400, modifier = Modifier.size(11.dp))
        Text(
            "+ Keterangan",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Cyan400),
            maxLines = 1,
            softWrap = false,
        )
    }
}
