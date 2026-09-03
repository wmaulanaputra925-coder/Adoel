package com.jekael.adoel.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.components.EmptyState
import com.jekael.adoel.ui.components.SwipeableCard
import com.jekael.adoel.ui.components.MesinTipeIcon
import com.jekael.adoel.ui.components.mesinTipeColor
import com.jekael.adoel.ui.theme.*

/** RIWAYAT page's list content: empty state or the recorded-doff rows (newest first). Shift-wide
 * actions live in the main header menu so this list stays focused on the recorded entries. */
fun LazyListScope.doffingSection(
    state: DoffState,
    aktualReversed: List<AktualEntry>,
    doffFilter: String,
    onDoffFilterChange: (String) -> Unit,
    onEntryClick: (Int) -> Unit,
    onHapusEntry: (Int) -> Unit,
) {
    if (state.aktual.isEmpty()) {
        item(key = "doff_empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                title = "Belum Ada Riwayat Doffing",
                subtitle = "Geser kartu mesin di layar Radar untuk mencatat doff, atau ketuk ikon gunting di konsol bawah untuk mencatat doff langsung.",
            )
        }
        return
    }

    // Summary Header: Total Doff & Total Yards
    item(key = "doff_summary") {
        val totalYards = remember(state.aktual, state.db) {
            state.aktual.sumOf { entry ->
                val custom = entry.customYard
                if (custom != null && !custom.isNaN()) custom
                else {
                    val mYard = state.db[entry.mcNo]?.targetYard?.toDouble()
                    if (mYard != null && !mYard.isNaN()) mYard else 0.0
                }
            }
        }
        val colors = LocalAppColors.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space4, vertical = Dimens.Space4)
                .animateItem(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.bgElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Total: ${state.aktual.size} Doff",
                    style = AppType.CaptionBold.copy(color = colors.textPrimary),
                )
            }
            if (totalYards > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.bgElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Texture,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Hasil: ${formatYard(totalYards)} yds",
                        style = AppType.CaptionBold.copy(color = colors.textPrimary),
                    )
                }
            }
        }
    }

    // Only worth showing once there's more than a handful to scan through — mirrors the same
    // threshold used for the radar list's filter.
    if (aktualReversed.size > 4) {
        item(key = "doff_filter") {
            ListFilterField(
                value = doffFilter,
                onValueChange = onDoffFilterChange,
                placeholder = "Cari nomor mesin",
                modifier = Modifier.fillMaxWidth().animateItem(),
            )
        }
    }
    // Filtered via withIndex() (not a fresh 1..N over the filtered subset) so the displayed
    // number still reflects each entry's true position in this shift's doff order, not its
    // position among just the search matches.
    val filteredIndexed = if (doffFilter.isBlank()) {
        aktualReversed.withIndex().toList()
    } else {
        aktualReversed.withIndex().filter { (_, entry) ->
            entry.mcNo.contains(doffFilter, ignoreCase = true)
        }
    }
    if (filteredIndexed.isEmpty()) {
        item(key = "doff_filter_empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space24),
                title = "Tidak ditemukan",
                subtitle = "Coba kata kunci lain — cari berdasarkan nomor mesin, corak, atau keterangan",
            )
        }
        return
    }
    items(filteredIndexed, key = { (_, entry) -> entry.id }) { (idx, entry) ->
        DoffingRow(
            entry = entry,
            mesin = state.db[entry.mcNo],
            num = idx + 1,
            onEdit = { onEntryClick(entry.id) },
            onHapus = { onHapusEntry(entry.id) },
            modifier = Modifier.animateItem(),
        )
    }
}


@Composable
private fun DoffingRow(
    entry: AktualEntry,
    mesin: MesinData?,
    num: Int,
    onEdit: () -> Unit,
    onHapus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val corak = entry.corakOverride ?: mesin?.corak ?: "—"
    val sub = when {
        entry.customYard != null -> "$corak · ${formatYard(entry.customYard)}y"
        mesin?.targetYard != null -> "$corak · ${formatYard(mesin.targetYard)}y"
        else -> corak
    }
    val tipeColor = mesin?.tipe?.let(::mesinTipeColor) ?: colors.textFaint
    // Swipe right = edit, swipe left = hapus — matches RadarCard's swipe model.
    // Also tap directly opens edit.
    SwipeableCard(
        modifier = modifier.fillMaxWidth(),
        onSwipeRight = onEdit,
        onSwipeLeft = onHapus,
        rightIcon = Icons.Outlined.Edit,
        leftIcon = Icons.Outlined.Delete,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .elevatedListCard(backgroundColor = colors.bgElevated)
                .clickable(onClick = onEdit)
                .semantics(mergeDescendants = true) {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit riwayat Mc ${entry.mcNo}") { onEdit(); true },
                        CustomAccessibilityAction("Hapus riwayat Mc ${entry.mcNo}") { onHapus(); true },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        ) {
            Text(
                text = "$num",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.textMuted),
                modifier = Modifier.width(22.dp),
            )
            if (mesin != null) {
                MesinTipeIcon(tipe = mesin.tipe, tint = tipeColor, modifier = Modifier.size(14.dp))
            } else {
                Icon(
                    imageVector = Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = tipeColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.mcNo,
                    style = AppType.NumberLarge.copy(color = Cyan500, letterSpacing = (-1).sp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                    // Same kain marker as RadarCard's corak/yard line (see Icons.Outlined.Texture
                    // there) — this history row shows the identical corak/yard pairing.
                    Icon(
                        imageVector = Icons.Outlined.Texture,
                        contentDescription = null,
                        tint = colors.textFaint,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = sub,
                        style = AppType.LabelBold.copy(color = colors.textMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = entry.ket,
                style = AppType.TabLabel.copy(color = colors.textPrimary),
            )
        }
    }
}

internal fun shareHistory(context: Context, state: DoffState) {
    shareIntent(context, buildShareHistoryText(state), "Bagikan riwayat")
}
