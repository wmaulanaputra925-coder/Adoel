package com.jekael.adoel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.components.*
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.delay

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
    onHapus: (String) -> Unit,
    onJeda: (String) -> Unit,
    onLanjutkan: (String) -> Unit,
    onQuickEdit: (String) -> Unit,
    onEditTipe: (String) -> Unit,
    onEditCorak: (String) -> Unit,
    onEditWaktu: (String) -> Unit,
) {
    if (radarList.isEmpty()) {
        item(key = "est_empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).animateItem(),
                title = "Radar Siap Memantau",
                titleIcon = Icons.Outlined.Radar,
                art = EmptyStateArt.RADAR,
                subtitleContent = {
                    InlineActionPillSubtitle(
                        before = "Belum ada estimasi aktif. Ketik nomor mesin di konsol bawah, lalu ketuk ikon jam ",
                        pillIcon = Icons.Outlined.Schedule,
                        pillLabel = "Estimasi",
                        pillAccent = Cyan400,
                        after = " untuk mulai memantau waktu doffing.",
                    )
                },
            )
        }
        return
    }

    // Computed up here (not down with the rest of the Menunggu band below) since the status bar
    // right below needs it too — a machine that's actually next shift's business isn't part of
    // "how many estimates are running right now" either, the same reasoning that keeps it out of
    // Menunggu's own OPERAN SHIFT-marked group in the first place.
    val shiftBoundary = currentShiftStartAbsMin(nowAbs) + 480
    val shiftHandoverCount = menungguList.count { it.estAbsMin > shiftBoundary }

    // Live Monitoring Status Header (Radar Aktif & Mesin Terdekat)
    item(key = "radar_status_bar") {
        val activeEstimasi = remember(radarList) { radarList.filter { it.pausedAtAbsMin == null } }
        val nearestActive = remember(activeEstimasi) { activeEstimasi.minByOrNull { it.estAbsMin } }
        RadarStatusBar(
            totalActive = radarList.size - shiftHandoverCount,
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
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space24).animateItem(),
                title = "Mesin Tidak Ditemukan",
                titleIcon = Icons.Outlined.Search,
                subtitle = "Coba kata kunci lain — masukkan nomor mesin yang terdaftar",
            )
        }
        return
    }
    if (dijedaList.isNotEmpty()) {
        item(key = "dijeda_head") {
            UrgencyBandHeader(label = "Dijeda", count = dijedaList.size, color = Amber400, icon = Icons.Outlined.Pause, modifier = Modifier.animateItem())
        }
        itemsIndexed(dijedaList, key = { _, est -> "dijeda_${est.mcNo}" }) { index, est ->
            RadarCard(
                est = est,
                mesin = db[est.mcNo],
                nowAbs = nowAbs,
                onDoff = { onDoff(est.mcNo) },
                onDoffMatching = { onDoffMatching(est.mcNo) },
                onHapus = { onHapus(est.mcNo) },
                onJeda = { onJeda(est.mcNo) },
                onLanjutkan = { onLanjutkan(est.mcNo) },
                onQuickEdit = { onQuickEdit(est.mcNo) },
                onEditTipe = { onEditTipe(est.mcNo) },
                onEditCorak = { onEditCorak(est.mcNo) },
                onEditWaktu = { onEditWaktu(est.mcNo) },
                modifier = Modifier.animateItem(),
                entranceDelayMs = (index * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS),
            )
        }
    }
    if (segeraList.isNotEmpty()) {
        item(key = "segera_head") {
            UrgencyBandHeader(label = "Segera", count = segeraList.size, color = Red400, icon = Icons.Outlined.LocalFireDepartment, modifier = Modifier.animateItem())
        }
        // Machines whose estAbsMin clash (within 5 minutes of a neighbor) used to each carry their
        // own "Bentrok Mc X, Y" badge — crowded the title row, and repeated the same warning once
        // per card in the group. Grouped instead: every clash run renders as one ClashGroupCard
        // wrapping its member cards, rather than one card each with a marker above the first.
        var segeraIndex = 0
        groupClashRuns(segeraList).forEach { group ->
            val startIndex = segeraIndex
            segeraIndex += group.size
            if (group.size > 1) {
                item(key = "clash_${group.first().mcNo}") {
                    ClashGroupCard(
                        group = group,
                        db = db,
                        nowAbs = nowAbs,
                        onDoff = onDoff,
                        onDoffMatching = onDoffMatching,
                        onHapus = onHapus,
                        onJeda = onJeda,
                        onLanjutkan = onLanjutkan,
                        onQuickEdit = onQuickEdit,
                        onEditTipe = onEditTipe,
                        onEditCorak = onEditCorak,
                        onEditWaktu = onEditWaktu,
                        entranceDelayMsFor = { i -> ((startIndex + i) * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS) },
                        modifier = Modifier.animateItem(),
                    )
                }
            } else {
                val est = group[0]
                item(key = est.mcNo) {
                    RadarCard(
                        est = est,
                        mesin = db[est.mcNo],
                        nowAbs = nowAbs,
                        onDoff = { onDoff(est.mcNo) },
                        onDoffMatching = { onDoffMatching(est.mcNo) },
                        onHapus = { onHapus(est.mcNo) },
                        onJeda = { onJeda(est.mcNo) },
                        onLanjutkan = { onLanjutkan(est.mcNo) },
                        onQuickEdit = { onQuickEdit(est.mcNo) },
                        onEditTipe = { onEditTipe(est.mcNo) },
                        onEditCorak = { onEditCorak(est.mcNo) },
                        onEditWaktu = { onEditWaktu(est.mcNo) },
                        modifier = Modifier.animateItem(),
                        entranceDelayMs = (startIndex * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS),
                    )
                }
            }
        }
    }
    if (menungguList.isNotEmpty()) {
        item(key = "menunggu_head") {
            UrgencyBandHeader(label = "Menunggu", count = menungguList.size, color = menungguAccent, icon = Icons.Outlined.Schedule, modifier = Modifier.animateItem())
        }
        // Every row full-width, in order — the 2-column grid pairing this band used to do for
        // calm/distant cards was an experiment; real floor use showed operators prefer scanning
        // one wide column over parsing a denser 2-up grid (Master Blueprint v9.2 §6).
        //
        // A BreakGapCard (MenungguRow.GapRow) between two shift-handover cards used to reset the
        // "is this the first one" check back to "yes" (it only ever looked at the immediately
        // preceding row), so the divider redrew itself before every handover card instead of just
        // the first one. Precomputing the one true first-crossing index up front, over the whole
        // list rather than one neighbor at a time, fixes that for good.
        val firstHandoverIdx = menungguRows.indexOfFirst { it is MenungguRow.CardRow && it.est.estAbsMin > shiftBoundary }
        // Same grouped-clash rendering as the Segera band above, computed against menungguList
        // (the plain time-sorted machines) rather than menungguRows — BreakGapCards interleaved
        // between cards don't carry a real estAbsMin of their own to chain against. A clash never
        // straddles a BreakGapCard (its members are always within 5 minutes of each other, far
        // under the gap this band's own BreakGapCard exists to flag), so each run's members stay
        // consecutive CardRows in menungguRows too — [groupByMcNo]/[consumedMcNos] below can key
        // purely off mcNo without re-deriving row adjacency.
        val menungguGroups = groupClashRuns(menungguList).filter { it.size > 1 }
        val groupByMcNo = menungguGroups.flatMap { g -> g.map { it.mcNo to g } }.toMap()
        val consumedMcNos = menungguGroups.flatMap { g -> g.drop(1).map { it.mcNo } }.toSet()
        menungguRows.forEachIndexed { index, row ->
            // A group's 2nd..Nth member renders as part of its ClashGroupCard at the group's own
            // start index (below) — nothing left to draw for it here.
            if (row is MenungguRow.CardRow && row.est.mcNo in consumedMcNos) return@forEachIndexed
            val entranceDelayMs = (index * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS)
            when (row) {
                is MenungguRow.CardRow -> {
                    val group = groupByMcNo[row.est.mcNo]
                    // The handover boundary can land on any member of a group, not just its first
                    // — the divider still belongs above the group as a whole, so its whole
                    // [index, index + group.size) span is checked, not just this exact index.
                    val spanEnd = index + (group?.size ?: 1)
                    val showHandoverDivider = firstHandoverIdx in index until spanEnd
                    item(key = rowKey(row)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (showHandoverDivider) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = Amber400.copy(alpha = 0.45f))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Icon(imageVector = Icons.Outlined.SwapHoriz, contentDescription = null, tint = Amber400, modifier = Modifier.size(14.dp))
                                        Text("OPERAN SHIFT · $shiftHandoverCount MESIN", style = AppType.Caption.copy(color = Amber400, fontWeight = FontWeight.Bold))
                                    }
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = Amber400.copy(alpha = 0.45f))
                                }
                            }
                            if (group != null) {
                                ClashGroupCard(
                                    group = group,
                                    db = db,
                                    nowAbs = nowAbs,
                                    onDoff = onDoff,
                                    onDoffMatching = onDoffMatching,
                                    onHapus = onHapus,
                                    onJeda = onJeda,
                                    onLanjutkan = onLanjutkan,
                                    onQuickEdit = onQuickEdit,
                                    onEditTipe = onEditTipe,
                                    onEditCorak = onEditCorak,
                                    onEditWaktu = onEditWaktu,
                                    entranceDelayMsFor = { i -> ((index + i) * Motion.LIST_STAGGER_STEP_MS).coerceAtMost(Motion.LIST_STAGGER_MAX_MS) },
                                )
                            } else {
                                RadarCard(
                                    est = row.est,
                                    mesin = db[row.est.mcNo],
                                    nowAbs = nowAbs,
                                    onDoff = { onDoff(row.est.mcNo) },
                                    onDoffMatching = { onDoffMatching(row.est.mcNo) },
                                    onHapus = { onHapus(row.est.mcNo) },
                                    onJeda = { onJeda(row.est.mcNo) },
                                    onLanjutkan = { onLanjutkan(row.est.mcNo) },
                                    onQuickEdit = { onQuickEdit(row.est.mcNo) },
                                    onEditTipe = { onEditTipe(row.est.mcNo) },
                                    onEditCorak = { onEditCorak(row.est.mcNo) },
                                    onEditWaktu = { onEditWaktu(row.est.mcNo) },
                                    modifier = Modifier.animateItem(),
                                    entranceDelayMs = entranceDelayMs,
                                )
                            }
                        }
                    }
                }
                is MenungguRow.GapRow -> item(key = rowKey(row)) {
                    BreakGapCard(
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
}

private fun rowKey(row: MenungguRow): String = when (row) {
    is MenungguRow.CardRow -> row.est.mcNo
    is MenungguRow.GapRow -> "gap_after_${row.afterMcNo}"
}

/** Bordered box wrapping every card of a run of adjacent machines whose estAbsMin clash (within 5
 * minutes of each other) — replaces both the old per-card "Bentrok Mc X, Y" badge (repeated the
 * same warning on every card in the group) and this group's own earlier divider-only treatment (a
 * thin rule with a label floating on it, easy to miss against a card-heavy list). A real amber
 * border around the whole run — closer to the AI Studio web experiment's own boxed clash
 * grouping — reads as "these belong together" at a glance the way a bare divider line didn't.
 * Only the machine *count* shows in the header, not each mcNo — the cards themselves, now visibly
 * boxed together, already say which machines are meant.
 *
 * "GASPOL" (not the more clinical "Bentrok") plus a lightning bolt that zaps every few seconds —
 * a pile-up of machines due at once framed as a call to move fast, not a fault to worry over. The
 * zap is a one-shot flash-and-settle (quick scale/brighten spike, eased back down), repeated on a
 * long ~5s idle rather than looped continuously like OVERDUE's steady breathing pulse — that
 * pulse means "ongoing danger, don't look away"; this one just means "yeah, still here," so it
 * stays lively without nagging. Now that the zap sits on a header badge inside a much bigger
 * boxed area instead of a lone icon on a thin line, the box's own border briefly brightens in the
 * same beat — a small icon flash alone would get lost against the larger container. */
@Composable
private fun ClashGroupCard(
    group: List<Estimasi>,
    db: Map<String, MesinData>,
    nowAbs: Long,
    onDoff: (String) -> Unit,
    onDoffMatching: (String) -> Unit,
    onHapus: (String) -> Unit,
    onJeda: (String) -> Unit,
    onLanjutkan: (String) -> Unit,
    onQuickEdit: (String) -> Unit,
    onEditTipe: (String) -> Unit,
    onEditCorak: (String) -> Unit,
    onEditWaktu: (String) -> Unit,
    entranceDelayMsFor: (Int) -> Long,
    modifier: Modifier = Modifier,
) {
    val zap = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            zap.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
            zap.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            delay(5000)
        }
    }
    val shape = RoundedCornerShape(Dimens.RadiusCard)
    val borderColor = lerp(Amber400, Color.White, zap.value * 0.5f).copy(alpha = 0.55f + zap.value * 0.3f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.5.dp, borderColor, shape)
            .padding(Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Amber500.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = lerp(Amber400, Color.White, zap.value),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        val scale = 1f + 0.35f * zap.value
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Text("GASPOL · ${group.size} MESIN", style = AppType.Caption.copy(color = Amber400, fontWeight = FontWeight.Bold))
        }
        group.forEachIndexed { i, est ->
            RadarCard(
                est = est,
                mesin = db[est.mcNo],
                nowAbs = nowAbs,
                onDoff = { onDoff(est.mcNo) },
                onDoffMatching = { onDoffMatching(est.mcNo) },
                onHapus = { onHapus(est.mcNo) },
                onJeda = { onJeda(est.mcNo) },
                onLanjutkan = { onLanjutkan(est.mcNo) },
                onQuickEdit = { onQuickEdit(est.mcNo) },
                onEditTipe = { onEditTipe(est.mcNo) },
                onEditCorak = { onEditCorak(est.mcNo) },
                onEditWaktu = { onEditWaktu(est.mcNo) },
                entranceDelayMs = entranceDelayMsFor(i),
            )
        }
    }
}

// Carries its own count instead of a separate "Estimasi N"/"Doffing N" header above it — the
// mode toggle already says which list this is, and the header's own shift-progress bar already
// covers the overall total, so a second standalone count row was just repeating the same number.
@Composable
private fun UrgencyBandHeader(label: String, count: Int, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
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
            Icon(imageVector = icon, contentDescription = null, tint = animatedColor, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp, color = animatedColor),
            )
        }
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = animatedColor.copy(alpha = 0.18f),
        ) {
            Text(
                text = "$count Mesin",
                style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = animatedColor),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp),
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
            // Glossy, not flat — this card sits directly between RadarCard fronts in the same
            // list (they're the only other place this treatment is used), and the flat tone it
            // had before read as a visibly different, less-finished card material next to them.
            .glossyListCard(baseColor = lerp(colors.bgElevated, Emerald500, 0.08f))
            .padding(horizontal = Dimens.Space16, vertical = 14.dp),
        // Centers this card's content when stretched taller than it needs to match a grid-paired
        // RadarCard sibling (see MenungguGridSlot) — a no-op when its own height already fits.
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = Emerald500,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Selang Waktu ${if (isActive) remainingMin else gapMin} Menit",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Emerald500),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Dimens.Space4))
        Text(
            text = formatDeltaMin(if (isActive) remainingMin else gapMin),
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, color = colors.textPrimary),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Sampai ${absMinToTimeStr(nextAbsMin)} — sebelum Mc $nextMcNo",
            style = AppType.Caption.copy(color = colors.textFaint),
            textAlign = TextAlign.Center,
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
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = "Hapus pencarian",
                        tint = colors.textFaint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else null,
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
