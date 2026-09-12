package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jekael.adoel.ui.theme.Purple400

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes (Riwayat stacked a big mc number over a corak line; Statistik ran everything
 * inline). Each caller still supplies its own container: Riwayat wraps this in a swipeable list
 * card, Statistik in a flat tappable strip inside the shift card.
 *
 * Fixed two-row layout instead of a wrapping FlowRow — the wrap point used to depend on content
 * length (a long keterangan or corak could push time onto its own line unpredictably, so card
 * height and vertical alignment varied row to row while scrolling). Now every row is exactly two
 * lines, always: identity (No/tipe/Mc/corak) on top, result (yard/jam/keterangan) below. Corak and
 * yard are also no longer folded into one pill — corak alone can already run long, and appending
 * "(303y)" to it just made the single pill wider still; each gets its own chip so neither one's
 * length affects the other's legibility.
 */
@Composable
fun DoffEntryRowContent(
    num: Int,
    entry: AktualEntry,
    mesin: MesinData?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val corak = entry.corakOverride ?: mesin?.corak ?: "—"
    val yard = entry.customYard ?: mesin?.targetYard
    // entry.ket is "$jam($extra)" when the doff carried a keterangan, or bare "$jam" when it
    // didn't (see DoffViewModel.prosesBarisUmum). The time has its own chip below, so strip it
    // back off here and keep only the code — otherwise the row prints the clock twice.
    val ketCode = entry.ket.removePrefix(entry.jam).removeSurrounding("(", ")")
    // MATCHING and HB are common/meaningful enough entries to pick out from ordinary free-typed
    // keterangan (P.LP, GANTI BEAM, etc, which stay the default amber) at a glance while scanning
    // Riwayat/Statistik — Emerald matches every other Matching indicator in the app (the corner
    // ribbon, the doff celebration); Purple is otherwise unused by any status/urgency color here,
    // so HB doesn't borrow meaning from something else (Teal/Violet/Indigo/Fuchsia are all already
    // machine-type identity colors — see mesinTipeColor in Icons.kt).
    val ketColor = when (ketCode) {
        "MATCHING" -> Emerald400
        "HB" -> Purple400
        else -> Amber400
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Baris 1: identitas (No, tipe, Mc) rapat di kiri, corak didorong ke ujung kanan lewat
        // SpaceBetween — bukan cuma menumpuk semua di kiri lalu membiarkan sisa lebar kartu kosong
        // begitu saja, kartu jadi terasa penuh dan seimbang dari tepi ke tepi.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.bgElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$num",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.textFaint),
                    )
                }

                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (mesin != null) {
                        MesinTipeIcon(tipe = mesin.tipe, tint = mesinTipeColor(mesin.tipe), modifier = Modifier.size(13.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = colors.textFaint,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }

                Text(
                    entry.mcNo,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Cyan400),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Capped (bukan weight) — sekarang berdiri sendiri di ujung kanan, bukan lagi
            // berbagi baris dengan elemen lain, jadi lebar sisa baris tidak relevan lagi; batas
            // ini semata mencegah corak yang diketik bebas terlalu panjang mendorong baris melebar.
            Row(
                modifier = Modifier
                    .widthIn(max = 160.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colors.bgElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Texture,
                    contentDescription = null,
                    tint = colors.textFaint,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    corak,
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Baris 2: yard + jam rapat di kiri, keterangan (kalau ada) didorong ke ujung kanan —
        // simetris dengan baris 1. Tanpa keterangan, SpaceBetween dengan satu grup saja otomatis
        // rapat kiri (tidak ada elemen kedua untuk didorong ke kanan).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (yard != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.bgElevated2)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${formatYard(yard)}y",
                            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textMuted),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // No more edit-pencil here — the row itself is the tap target (Statistik even
                // prints "Ketuk baris untuk edit" once above the list), so a second per-row hint
                // was redundant, not the reason anyone found the affordance.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.bgElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.textFaint,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        entry.jam,
                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            if (ketCode.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 130.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(ketColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        ketCode,
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = ketColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
