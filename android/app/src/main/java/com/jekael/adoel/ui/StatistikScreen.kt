package com.jekael.adoel.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.data.ShiftRecord
import com.jekael.adoel.data.buildShareShiftText
import com.jekael.adoel.data.currentShiftStartAbsMin
import com.jekael.adoel.data.formatDeltaMin
import com.jekael.adoel.data.formatShiftDate
import com.jekael.adoel.data.formatShiftShortDate
import com.jekael.adoel.data.formatShiftTime
import com.jekael.adoel.data.getRepresentativeEpochMin
import com.jekael.adoel.data.shareIntent
import com.jekael.adoel.data.shiftNumberForEpochMin
import com.jekael.adoel.data.sortAktualChronological
import com.jekael.adoel.ui.components.CloseIcon
import com.jekael.adoel.ui.components.DoffEntryRowContent
import com.jekael.adoel.ui.components.EditAktSheet
import com.jekael.adoel.ui.components.EmptyState
import com.jekael.adoel.ui.components.LinearProgressBar
import com.jekael.adoel.ui.components.SlidePanel
import com.jekael.adoel.ui.components.swipeRightToClose
import com.jekael.adoel.ui.components.TambahAktSheet
import com.jekael.adoel.ui.components.mesinTipeColor
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Red500
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.EdgeFadeScrim
import com.jekael.adoel.ui.theme.LocalAppColors
import com.jekael.adoel.ui.theme.Motion
import com.jekael.adoel.ui.theme.elevatedListCard
import com.jekael.adoel.ui.theme.floatingHeaderCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen panel listing archived shifts (see DoffViewModel.finishShift) with simple
 * aggregate productivity stats. Mirrors MesinDrawer/PengaturanDrawer's slide-in shell (no
 * drag-to-dismiss, kept simpler since this is a read-only view).
 */
@Composable
fun StatistikScreen(
    history: List<ShiftRecord>,
    db: Map<String, MesinData>,
    // Identitas yang berlaku sekarang — cadangan untuk arsip yang belum punya cap operator
    // sendiri (lihat buildShareShiftText).
    operatorNama: String,
    operatorGrup: String,
    onClose: () -> Unit,
    onDeleteShift: (Int) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
    showToast: (String) -> Unit,
    onEditEntrySave: (shiftId: Int, id: Int, jam: String, ket: String, corakOverride: String?, customYard: Double?) -> Unit,
    onDeleteEntry: (shiftId: Int, id: Int) -> Unit,
    onAddEntry: (shiftId: Int, mcNo: String, jam: String, ket: String, corakOverride: String?, customYard: Double?) -> Unit,
    corakShortcuts: List<String>? = null,
    keteranganShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
    onAddKeteranganShortcut: (String) -> Unit = {},
) {
    val colors = LocalAppColors.current
    var expandedShiftId by remember { mutableStateOf<Int?>(null) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // (shiftId, entryId) of the archived doff record being edited, if any — a finished shift's
    // entries aren't actually immutable, an operator can still spot a mistyped jam/corak/yard
    // after the fact, same as they could in Riwayat before "Selesai Shift" archived it here.
    var editingEntry by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Shift a missed doff is being backfilled into, if any — see TambahAktSheet.
    var addingToShiftId by remember { mutableStateOf<Int?>(null) }

    SlidePanel(onClose = onClose) { requestClose ->
        // Same "floating header overlays a full-bleed scrollable list" concept as MainScreen —
        // the list is measured/laid out from the very top and scrolls behind the header, instead
        // of just sitting in a Column below it.
        // Swipe-right-to-dismiss, the same gesture (and the same shared modifier) that closes
        // Pengaturan and Daftar Mesin — the shift cards below no longer take a swipe of their
        // own, so the whole page can have it without the two competing for the drag.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .swipeRightToClose(onClose),
        ) {
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "Belum ada riwayat shift",
                        subtitle = "Riwayat akan tersimpan otomatis setiap kali kamu tekan Selesai Shift",
                    )
                }
            } else {
                val totalDoff = history.sumOf { it.aktual.size }
                val avgPerShift = totalDoff.toFloat() / history.size
                val maxDoffCount = (history.maxOfOrNull { it.aktual.size } ?: 1).coerceAtLeast(1)
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()

                fun jumpToShift(shift: ShiftRecord) {
                    expandedShiftId = shift.id
                    val index = history.indexOfFirst { it.id == shift.id }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index + 1) }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 10.dp + headerHeight + 16.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
                ) {
                    item {
                        AggregateStatsCard(
                            history = history,
                            db = db,
                            totalDoff = totalDoff,
                            avgPerShift = avgPerShift,
                            selectedShiftId = expandedShiftId,
                            onBarClick = { jumpToShift(it) },
                        )
                    }
                    items(history, key = { it.id }) { shift ->
                        ShiftRow(
                            shift = shift,
                            db = db,
                            maxDoffCount = maxDoffCount,
                            expanded = expandedShiftId == shift.id,
                            onToggle = { expandedShiftId = if (expandedShiftId == shift.id) null else shift.id },
                            onDeleteShift = onDeleteShift,
                            showConfirm = showConfirm,
                            onEditEntry = { entryId -> editingEntry = shift.id to entryId },
                            onAddEntry = { addingToShiftId = shift.id },
                            operatorNama = operatorNama,
                            operatorGrup = operatorGrup,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            // Same soft cut-off the other scrolling screens get where content passes behind the
            // header. Top only — nothing floats at the bottom of this panel.
            EdgeFadeScrim(atTop = true, height = 10.dp + headerHeight + Dimens.Space16)

            // Floating header — overlays the list (list scrolls behind it), matching
            // MainScreen's header/console bar look: shadow + rounded corners + a subtle border
            // (shadows alone barely read on a near-black dark background).
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        headerHeight = with(density) { coords.size.height.toDp() }
                    }
                    .padding(horizontal = Dimens.Space12)
                    .padding(top = Dimens.Space12)
                    .floatingHeaderCard(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space20, vertical = Dimens.Space12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Statistik", style = AppType.DialogTitle.copy(color = colors.textPrimary))
                    IconButton(onClick = { requestClose() }) {
                        CloseIcon()
                    }
                }
            }
        }

        val (editingShiftId, editingEntryId) = editingEntry ?: (null to null)
        val editingRecord = editingShiftId?.let { sid -> history.find { it.id == sid } }
        val editingAktual = editingRecord?.aktual?.find { it.id == editingEntryId }
        if (editingRecord != null && editingAktual != null) {
            EditAktSheet(
                entry = editingAktual,
                mesin = db[editingAktual.mcNo],
                onClose = { editingEntry = null },
                onSave = { id, jam, ket, corakOverride, customYard ->
                    onEditEntrySave(editingRecord.id, id, jam, ket, corakOverride, customYard)
                    showToast("Riwayat diperbarui")
                    editingEntry = null
                },
                onInvalidYard = { showToast("Yard tidak valid") },
                onInvalidJam = { showToast("Jam tidak valid — format 14.30") },
                onDelete = {
                    onDeleteEntry(editingRecord.id, editingAktual.id)
                    editingEntry = null
                },
                corakShortcuts = corakShortcuts,
                keteranganShortcuts = keteranganShortcuts,
                onAddCorakShortcut = onAddCorakShortcut,
                onAddKeteranganShortcut = onAddKeteranganShortcut,
                showToast = showToast,
            )
        }

        val addingShiftId = addingToShiftId
        if (addingShiftId != null) {
            TambahAktSheet(
                db = db,
                onClose = { addingToShiftId = null },
                onSave = { mcNo, jam, ket, corakOverride, customYard ->
                    onAddEntry(addingShiftId, mcNo, jam, ket, corakOverride, customYard)
                    showToast("Potongan ditambahkan")
                    addingToShiftId = null
                },
                onInvalidMcNo = { showToast("Nomor mesin tidak ditemukan") },
                onInvalidYard = { showToast("Yard tidak valid") },
                onInvalidJam = { showToast("Jam tidak valid — format 14.30") },
                corakShortcuts = corakShortcuts,
                keteranganShortcuts = keteranganShortcuts,
                onAddCorakShortcut = onAddCorakShortcut,
                onAddKeteranganShortcut = onAddKeteranganShortcut,
                showToast = showToast,
            )
        }
    }
}

@Composable
private fun AggregateStatsCard(
    history: List<ShiftRecord>,
    db: Map<String, MesinData>,
    totalDoff: Int,
    avgPerShift: Float,
    selectedShiftId: Int?,
    onBarClick: (ShiftRecord) -> Unit,
) {
    val colors = LocalAppColors.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animatedTotal by animateIntAsState(
        targetValue = if (started) totalDoff else 0,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "totalDoff",
    )
    val animatedShifts by animateIntAsState(
        targetValue = if (started) history.size else 0,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "shiftCount",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .elevatedListCard(backgroundColor = colors.bgElevated)
            .padding(Dimens.Space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Total Doff",
                value = "$animatedTotal",
                highlight = true,
            )
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Total Shift",
                value = "$animatedShifts",
                highlight = false,
            )
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Rata-rata/Shift",
                value = "%.1f".format(avgPerShift),
                highlight = false,
            )
        }

        DoffCountChart(history = history, selectedShiftId = selectedShiftId, onBarClick = onBarClick)

        TipeBreakdownBar(history = history, db = db)
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgElevated2)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = AppType.NumberLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlight) Cyan400 else colors.textPrimary,
            ),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = AppType.Caption.copy(
                fontSize = 11.sp,
                color = colors.textFaint,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}
@Composable
private fun TipeBreakdownBar(history: List<ShiftRecord>, db: Map<String, MesinData>) {
    val colors = LocalAppColors.current
    val counts = remember(history, db) {
        val order = listOf(MesinTipe.TAPPET, MesinTipe.CAM, MesinTipe.D405, MesinTipe.D408)
        val byTipe = history.asSequence().flatMap { it.aktual }.mapNotNull { db[it.mcNo]?.tipe }
            .groupingBy { it }.eachCount()
        order.mapNotNull { tipe -> byTipe[tipe]?.let { tipe to it } }
    }
    val total = counts.sumOf { it.second }
    if (total == 0) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            counts.forEachIndexed { index, (tipe, count) ->
                // Same stagger-then-grow entrance as DoffCountChart's bars, reusing the identical
                // Animatable + delayed-LaunchedEffect pattern (and its stagger-step token) instead
                // of inventing a new animation shape for this second chart on the same screen.
                val animatedFraction = remember(tipe) { Animatable(0f) }
                LaunchedEffect(tipe, count) {
                    delay(index * Motion.CHART_STAGGER_STEP_MS)
                    animatedFraction.animateTo(count.toFloat(), animationSpec = tween(250, easing = FastOutSlowInEasing))
                }
                Box(
                    modifier = Modifier
                        .weight(animatedFraction.value.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(mesinTipeColor(tipe)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
            counts.forEach { (tipe, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(mesinTipeColor(tipe)),
                    )
                    Text(
                        text = "${tipe.name} $count",
                        style = TextStyle(fontSize = 12.sp, color = colors.textFaint),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatFigure(label: String, value: String) {
    val colors = LocalAppColors.current
    Column {
        Text(value, style = AppType.NumberLarge.copy(color = colors.textPrimary))
        Text(label, style = TextStyle(fontSize = 12.sp, color = colors.textFaint))
    }
}

/** Bar chart of doff count for the most recent shifts, oldest on the left — each bar carries its
 * own count label and a short date underneath, with a baseline so heights read unambiguously.
 * Tapping a bar jumps the list below to that shift's row and expands it, bridging chart and detail. */
@Composable
private fun DoffCountChart(history: List<ShiftRecord>, selectedShiftId: Int?, onBarClick: (ShiftRecord) -> Unit) {
    val colors = LocalAppColors.current
    val recent = history.take(10).asReversed()
    if (recent.isEmpty()) return
    val maxCount = (recent.maxOfOrNull { it.aktual.size } ?: 1).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tren 10 Shift Terakhir",
                style = AppType.CaptionBold.copy(color = colors.textSecondary),
            )
            Text(
                text = "Ketuk balok untuk lihat shift",
                style = TextStyle(fontSize = 11.sp, color = colors.textFaint),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            recent.forEachIndexed { index, shift ->
                // key(shift.id) (not just positional remember) — without it, deleting an earlier
                // shift shifts every later shift's position in `recent`, and a plain positional
                // remember would then hand a DIFFERENT shift's already-settled Animatable to a bar
                // that just moved into that slot, misanimating it as if its own count had changed.
                key(shift.id) {
                    val targetFraction = shift.aktual.size.toFloat() / maxCount
                    val animatedFraction = remember { Animatable(0f) }
                    LaunchedEffect(shift.id, targetFraction) {
                        delay(index * Motion.CHART_STAGGER_STEP_MS)
                        animatedFraction.animateTo(targetFraction, animationSpec = tween(250, easing = FastOutSlowInEasing))
                    }
                    val selected = shift.id == selectedShiftId
                    // 0.85 unselected / full-opacity Cyan400 selected — matches web's own
                    // .stat-chart-bar (opacity: 0.85) / .selected (opacity: 1, brighter cyan-400).
                    val barColor by animateColorAsState(
                        if (selected) Cyan400 else Cyan500.copy(alpha = 0.85f),
                        label = "barColor",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                onClickLabel = "Lihat detail Shift ${shiftNumberForEpochMin(shift.startedAtEpochMin)} · ${shift.aktual.size} doff",
                                onClick = { onBarClick(shift) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            "${shift.aktual.size}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Cyan400 else colors.textSecondary,
                            ),
                        )
                        Spacer(Modifier.height(2.dp))
                        // Flat solid bar, capped at 28dp wide like web's .stat-chart-bar (max-width:
                        // 28px) instead of stretching to fill the column on a short history list —
                        // and a plain flat fill, not the diagonal thread-texture this used to draw:
                        // web's bars are solid color, and the texture was the one clearly visible
                        // mismatch between the two platforms' charts.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 28.dp)
                                .height((44.dp * animatedFraction.value).coerceAtLeast(4.dp))
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 4.dp,
                                        bottomStart = 1.dp,
                                        bottomEnd = 1.dp,
                                    ),
                                )
                                .background(barColor),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(Dimens.Space4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            recent.forEach { shift ->
                Text(
                    text = formatShiftShortDate(shift.startedAtEpochMin),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, color = colors.textFaint),
                )
            }
        }
    }
}

@Composable
private fun ShiftRow(
    shift: ShiftRecord,
    db: Map<String, MesinData>,
    maxDoffCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDeleteShift: (Int) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
    onEditEntry: (entryId: Int) -> Unit,
    onAddEntry: () -> Unit,
    operatorNama: String,
    operatorGrup: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val representativeTime = remember(shift) { getRepresentativeEpochMin(shift) }
    val shiftNo = remember(representativeTime) { shiftNumberForEpochMin(representativeTime) }
    // Both the date and the range come off the shift's scheduled start rather than straight from
    // the record, so shifts archived before finishShift started storing it that way (first doff →
    // Selesai Shift tap) still read as the shift they actually were — a night shift whose first
    // doff landed after midnight used to be dated the following day. A no-op for new records:
    // their stored start is already a boundary, and a boundary maps to itself.
    val scheduledStart = remember(shift.startedAtEpochMin) { currentShiftStartAbsMin(shift.startedAtEpochMin) }
    val dateStr = remember(scheduledStart) { formatShiftDate(scheduledStart) }
    val timeRange = remember(scheduledStart) {
        "${formatShiftTime(scheduledStart)}–${formatShiftTime(scheduledStart + 480)}"
    }
    // +240 (4 jam setelah mulai) dipakai sebagai titik tengah yang aman dari pembungkusan
    // tanggal untuk shift 8 jam manapun — shift ini sudah diarsipkan, bisa dibuka
    // berhari-hari kemudian, jadi "sekarang" bukan acuan yang masuk akal.
    val chronological = remember(shift.aktual, shift.startedAtEpochMin) {
        sortAktualChronological(shift.aktual, shift.startedAtEpochMin + 240)
    }
    val avgGapMin = remember(chronological) {
        val stamped = chronological.mapNotNull { it.tsEpochMin }
        if (stamped.size >= 2) stamped.zipWithNext { a, b -> b - a }.average() else null
    }

    // Bagikan langsung — bukan salin, supaya tidak perlu ganti aplikasi lalu tempel manual.
    // Tidak berarti apa-apa untuk shift tanpa doff (mis. diarsipkan dengan estimasi yang belum
    // sempat diselesaikan), jadi tombol Bagikan-nya dinonaktifkan untuk shift kosong.
    fun requestShare() {
        if (shift.aktual.isNotEmpty()) shareShift(context, shift, db, operatorNama, operatorGrup)
    }
    fun requestDelete() {
        showConfirm("Hapus arsip Shift $shiftNo · $dateStr? Data ini tidak bisa dikembalikan.") {
            onDeleteShift(shift.id)
        }
    }

    // No swipe-to-act on this card. It carries visible Bagikan/Hapus buttons, so a hidden
    // gesture for the same two actions was only a second way to reach them — and it swallowed
    // the horizontal drag that now closes the whole page (see swipeRightToClose above). The
    // buttons are real focusable targets, so the custom accessibility actions that stood in for
    // the swipe went with it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .elevatedListCard(backgroundColor = colors.bgElevated)
            .clickable { onToggle() }
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Shift $shiftNo · $dateStr",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                )
                Text(timeRange, style = AppType.Caption.copy(color = colors.textFaint))
                Spacer(Modifier.height(6.dp))
                LinearProgressBar(
                    fraction = shift.aktual.size.toFloat() / maxDoffCount,
                    trackColor = colors.bgElevated2,
                    fillColor = Cyan500,
                    width = 60.dp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${shift.aktual.size} doff", style = AppType.TabLabel.copy(color = Cyan400))
                if (avgGapMin != null) {
                    Text(
                        "±${formatDeltaMin(avgGapMin.toLong())}/doff",
                        style = TextStyle(fontSize = 12.sp, color = colors.textFaint),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { requestShare() },
                enabled = shift.aktual.isNotEmpty(),
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bagikan", style = AppType.CaptionBold)
            }
            OutlinedButton(
                onClick = { requestDelete() },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500),
                border = BorderStroke(1.dp, Red500.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Red500)
                Spacer(Modifier.width(6.dp))
                Text("Hapus", style = AppType.CaptionBold.copy(color = Red500))
            }
        }

        // A shift can unfold a dozen detail rows at once; as a bare `if` the card jumped
        // straight to its new height, the one list interaction in the app that didn't move.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                if (chronological.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "RINCIAN POTONGAN",
                                style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = colors.textFaint),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Cyan400.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    "${chronological.size} DOFF",
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = Cyan400),
                                )
                            }
                        }
                        Text(
                            "Ketuk baris untuk edit",
                            style = TextStyle(fontSize = 10.5.sp, color = colors.textFaint, fontStyle = FontStyle.Italic),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                chronological.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.bgElevated2)
                            // Wins over the shift card's own onToggle clickable above it (innermost
                            // clickable consumes the tap) — tapping a single archived entry opens
                            // edit for just that record instead of collapsing the whole shift.
                            .clickable(onClickLabel = "Edit riwayat Mc ${entry.mcNo}") { onEditEntry(entry.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Shared with the Riwayat list so both read identically — see DoffEntryRow.kt.
                        DoffEntryRowContent(
                            num = index + 1,
                            entry = entry,
                            mesin = db[entry.mcNo],
                            showEditHint = true,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    // Wins over the outer onToggle clickable the same way the entry rows above do.
                    onClick = onAddEntry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Cyan400),
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah Potongan", style = AppType.LabelSmallBold)
                }
            }
        }
    }
}

/** Re-share a single archived shift — mirrors [buildShareHistoryText]'s format/tone exactly (same
 * "Bravo!!!" casual register, same audience: rekan kerja), for whenever an operator needs to
 * resend a specific day's record instead of the whole running total. Opens the share-sheet
 * directly instead of a copy-then-paste round trip. */
private fun shareShift(
    context: Context,
    shift: ShiftRecord,
    db: Map<String, MesinData>,
    operatorNama: String,
    operatorGrup: String,
) {
    shareIntent(context, buildShareShiftText(shift, db, operatorNama, operatorGrup), "Bagikan shift")
}
