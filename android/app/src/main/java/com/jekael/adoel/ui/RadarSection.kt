package com.jekael.adoel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.components.*
import com.jekael.adoel.ui.theme.*

/** ESTIMASI mode's list content: empty state, or the Segera/Menunggu urgency bands (each band's
 * own count lives in its [UrgencyBandHeader] — no separate "Estimasi N" header above both, since
 * the mode toggle already says which list this is and the header's shift-progress bar already
 * covers the overall total). */
internal fun LazyListScope.estimasiSection(
    radarList: List<Estimasi>,
    dijedaList: List<Estimasi>,
    segeraList: List<Estimasi>,
    menungguList: List<Estimasi>,
    menungguRows: List<MenungguRow>,
    menungguAccent: Color,
    db: Map<String, MesinData>,
    nowAbs: Long,
    radarFilter: String,
    onRadarFilterChange: (String) -> Unit,
    onDoff: (String) -> Unit,
    onDoffMatching: (String) -> Unit,
    // See RadarCard's guardDoffMatching doc — runs before the swipe-left slide-out animation
    // starts, not after, so a "potongan awal 70y" confirm dialog can still cancel it cleanly.
    guardDoffMatching: (String, () -> Unit) -> Unit = { _, proceed -> proceed() },
    onHapus: (String) -> Unit,
    onJeda: (String) -> Unit,
    onLanjutkan: (String) -> Unit,
    onQuickEdit: (String) -> Unit,
    onEditWaktu: (String) -> Unit,
) {
    if (radarList.isEmpty()) {
        item(key = "est_empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                title = "Belum Ada Pantauan Estimasi",
                subtitle = "Ketik nomor mesin di konsol bawah, lalu ketuk ikon jam untuk menjadwalkan estimasi doffing.",
            )
        }
        return
    }

    // Live Monitoring Status Header (Radar Aktif & Mesin Terdekat)
    item(key = "radar_status_bar") {
        val activeEstimasi = remember(radarList) { radarList.filter { it.pausedAtAbsMin == null } }
        val nearestActive = remember(activeEstimasi) { activeEstimasi.minByOrNull { it.estAbsMin } }
        RadarStatusBar(
            totalActive = radarList.size,
            nearestActive = nearestActive,
            nowAbs = nowAbs,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space4, vertical = Dimens.Space4).animateItem(),
        )
    }

    // Only worth showing once there's more than a handful to scan through — for a couple of
    // machines a filter field is just clutter above the very thing it's meant to help find.
    if (radarList.size > 4) {
        item(key = "est_filter") {
            ListFilterField(
                value = radarFilter,
                onValueChange = onRadarFilterChange,
                placeholder = "Cari nomor mesin",
                modifier = Modifier.fillMaxWidth().animateItem(),
            )
        }
    }
    if (segeraList.isEmpty() && menungguList.isEmpty() && dijedaList.isEmpty()) {
        item(key = "est_filter_empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space24),
                title = "Tidak ditemukan",
                subtitle = "Coba kata kunci lain — cari berdasarkan nomor mesin",
            )
        }
        return
    }
    val shiftBoundary = currentShiftStartAbsMin(nowAbs) + 480
    if (dijedaList.isNotEmpty()) {
        item(key = "dijeda_head") {
            UrgencyBandHeader(label = "Dijeda", count = dijedaList.size, color = Amber400, modifier = Modifier.animateItem())
        }
        itemsIndexed(dijedaList, key = { _, est -> "dijeda_${est.mcNo}" }) { index, est ->
            RadarCard(
                est = est,
                mesin = db[est.mcNo],
                nowAbs = nowAbs,
                clashingMcNos = emptyList(),
                shiftHandover = est.estAbsMin > shiftBoundary,
                onDoff = { onDoff(est.mcNo) },
                onDoffMatching = { onDoffMatching(est.mcNo) },
                guardDoffMatching = { proceed -> guardDoffMatching(est.mcNo, proceed) },
                onHapus = { onHapus(est.mcNo) },
                onJeda = { onJeda(est.mcNo) },
                onLanjutkan = { onLanjutkan(est.mcNo) },
                onQuickEdit = { onQuickEdit(est.mcNo) },
                onEditWaktu = { onEditWaktu(est.mcNo) },
                modifier = Modifier.animateItem(),
                entranceDelayMs = (index * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS),
            )
        }
    }
    if (segeraList.isNotEmpty()) {
        item(key = "segera_head") {
            UrgencyBandHeader(label = "Segera", count = segeraList.size, color = Red400, modifier = Modifier.animateItem())
        }
        itemsIndexed(segeraList, key = { _, est -> est.mcNo }) { index, est ->
            RadarCard(
                est = est,
                mesin = db[est.mcNo],
                nowAbs = nowAbs,
                clashingMcNos = findClashingMachines(est.mcNo, radarList),
                shiftHandover = est.estAbsMin > shiftBoundary,
                onDoff = { onDoff(est.mcNo) },
                onDoffMatching = { onDoffMatching(est.mcNo) },
                guardDoffMatching = { proceed -> guardDoffMatching(est.mcNo, proceed) },
                onHapus = { onHapus(est.mcNo) },
                onJeda = { onJeda(est.mcNo) },
                onLanjutkan = { onLanjutkan(est.mcNo) },
                onQuickEdit = { onQuickEdit(est.mcNo) },
                onEditWaktu = { onEditWaktu(est.mcNo) },
                modifier = Modifier.animateItem(),
                entranceDelayMs = (index * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS),
            )
        }
    }
    if (menungguList.isNotEmpty()) {
        item(key = "menunggu_head") {
            UrgencyBandHeader(label = "Menunggu", count = menungguList.size, color = menungguAccent, modifier = Modifier.animateItem())
        }
        // Every row full-width, in order — the 2-column grid pairing this band used to do for
        // calm/distant cards was an experiment; real floor use showed operators prefer scanning
        // one wide column over parsing a denser 2-up grid (Master Blueprint v9.2 §6).
        itemsIndexed(menungguRows, key = { _, row -> rowKey(row) }) { index, row ->
            val entranceDelayMs = (index * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS)
            when (row) {
                is MenungguRow.CardRow -> {
                    val previousCard = menungguRows.getOrNull(index - 1) as? MenungguRow.CardRow
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.est.estAbsMin > shiftBoundary && (previousCard == null || previousCard.est.estAbsMin <= shiftBoundary)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Amber400.copy(alpha = 0.45f))
                            Text("⏭️ OPERAN SHIFT", style = AppType.Caption.copy(color = Amber400, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Amber400.copy(alpha = 0.45f))
                        }
                    }
                    RadarCard(
                        est = row.est,
                        mesin = db[row.est.mcNo],
                        nowAbs = nowAbs,
                        clashingMcNos = findClashingMachines(row.est.mcNo, radarList),
                        shiftHandover = row.est.estAbsMin > shiftBoundary,
                        onDoff = { onDoff(row.est.mcNo) },
                        onDoffMatching = { onDoffMatching(row.est.mcNo) },
                        guardDoffMatching = { proceed -> guardDoffMatching(row.est.mcNo, proceed) },
                        onHapus = { onHapus(row.est.mcNo) },
                        onJeda = { onJeda(row.est.mcNo) },
                        onLanjutkan = { onLanjutkan(row.est.mcNo) },
                        onQuickEdit = { onQuickEdit(row.est.mcNo) },
                        onEditWaktu = { onEditWaktu(row.est.mcNo) },
                        modifier = Modifier.animateItem(),
                        entranceDelayMs = entranceDelayMs,
                    )
                }
                }
                is MenungguRow.GapRow -> BreakGapCard(
                    gapMin = row.gapMin,
                    nextMcNo = row.nextMcNo,
                    nextAbsMin = row.nextAbsMin,
                    nowAbs = nowAbs,
                    // Only the very first row in the whole list is the gap actually happening
                    // right now — any GapRow further down previews a break that hasn't started,
                    // so its bar should read as not-yet-active rather than fill in.
                    isActive = index == 0,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun rowKey(row: MenungguRow): String = when (row) {
    is MenungguRow.CardRow -> row.est.mcNo
    is MenungguRow.GapRow -> "gap_after_${row.afterMcNo}"
}

// Carries its own count instead of a separate "Estimasi N"/"Doffing N" header above it — the
// mode toggle already says which list this is, and the header's own shift-progress bar already
// covers the overall total, so a second standalone count row was just repeating the same number.
@Composable
private fun UrgencyBandHeader(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    val animatedColor by animateColorAsState(color, animationSpec = tween(250), label = "urgencyBandColor")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(animatedColor),
            )
            Text(
                text = "$label · $count",
                style = AppType.LabelBold.copy(color = animatedColor),
            )
        }
    }
}

/** Sits between two RadarCards in the Menunggu band when the gap to the next doff is long enough
 * to actually step away. The headline number (same big/bold treatment as a RadarCard's countdown)
 * answers "how long is this break" at a glance instead of making an operator do the subtraction
 * between the two neighboring cards' times themselves — the whole point of this card existing.
 * The caption below carries the live end time so "when does it end" stays answerable too. Emerald
 * is used nowhere in the urgency scale (Cyan/Amber/Orange/Red), so this reads as "good news"
 * rather than competing with any urgency color.
 *
 * [isActive] gates both the headline number and the progress bar: only the topmost jeda card (the
 * one whose window has actually started) counts the headline down live and fills its bar — a jeda
 * further down the list is a preview of a gap that hasn't begun yet, so [nowAbs]..[nextAbsMin]
 * there spans however many hours away the whole thing is, not the break's own length. That preview
 * case shows the fixed [gapMin] instead — the actual break length, unchanging until it starts. */
@Composable
private fun BreakGapCard(
    gapMin: Long,
    nextMcNo: String,
    nextAbsMin: Long,
    nowAbs: Long,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val remainingMin = (nextAbsMin - nowAbs).coerceAtLeast(0)
    val elapsedFraction = if (!isActive) 0f else if (gapMin > 0) (1f - remainingMin.toFloat() / gapMin).coerceIn(0f, 1f) else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .elevatedListCard(backgroundColor = lerp(colors.bgElevated, Emerald500, 0.08f))
            .padding(horizontal = Dimens.Space16, vertical = 14.dp),
        // Centers this card's content when stretched taller than it needs to match a grid-paired
        // RadarCard sibling (see MenungguGridSlot) — a no-op when its own height already fits.
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = Emerald500,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "⏳ Selang Waktu ${if (isActive) remainingMin else gapMin} Menit",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Emerald500),
            )
        }
        Spacer(Modifier.height(Dimens.Space4))
        Text(
            text = formatDeltaMin(if (isActive) remainingMin else gapMin),
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, color = colors.textPrimary),
        )
        Text(
            text = "Sampai ${absMinToTimeStr(nextAbsMin)} — sebelum Mc $nextMcNo",
            style = AppType.Caption.copy(color = colors.textFaint),
        )
        Spacer(Modifier.height(Dimens.Space8))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.bgElevated2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(elapsedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Emerald500),
            )
        }
    }
}

/** Shared search box for both the radar (ESTIMASI) and doffing (AKTUAL) lists — lets an operator
 * jump straight to a machine instead of scanning past everything else when a lot are on screen. */
@Composable
internal fun ListFilterField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, style = AppType.Caption.copy(color = colors.textFaint)) },
        colors = outlinedFieldColors(),
        shape = RoundedCornerShape(50.dp),
        textStyle = AppType.FieldText.copy(color = colors.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
        singleLine = true,
    )
}

@Composable
private fun RadarStatusBar(
    totalActive: Int,
    nearestActive: Estimasi?,
    nowAbs: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgElevated)
            .padding(horizontal = Dimens.Space12, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Emerald500),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Radar Aktif:",
                    style = AppType.Caption.copy(color = colors.textSecondary),
                )
                Text(
                    text = "$totalActive Mesin",
                    style = AppType.CaptionBold.copy(color = colors.textPrimary),
                )
            }
        }

        if (nearestActive != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.bgElevated2)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "Terdekat: Mc ${nearestActive.mcNo} (${formatDeltaMin(nearestActive.estAbsMin - nowAbs)})",
                    style = AppType.CaptionBold.copy(color = Cyan400, fontSize = 11.5.sp),
                )
            }
        }
    }
}

