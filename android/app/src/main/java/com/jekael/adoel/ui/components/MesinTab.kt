package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.theme.*

private enum class MesinStatusFilter { ALL, ACTIVE, STOPPED }

private data class CorakSummaryItem(
    val corak: String,
    val machines: List<String>,
    val tipes: Set<MesinTipe>,
)

/** One corak card inside the "Corak Sedang Produksi" 2-up grid — name + machine-count chip up
 * top, mc-number quick-access pills below. Port 1:1 of web's .corak-summary-item/-count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorakSummaryCard(
    item: CorakSummaryItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPillClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = modifier.clickable(onClick = onToggleSelect),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Cyan600.copy(alpha = 0.16f) else colors.bg,
        border = BorderStroke(1.dp, if (isSelected) Cyan500 else colors.border),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.corak,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Cyan400 else colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Cyan500.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.25f)),
                ) {
                    Text(
                        "${item.machines.size} mc",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = Cyan400),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.machines.forEach { m ->
                    Surface(
                        // A nested clickable consumes its own tap in Compose, so this doesn't
                        // also trigger the parent Surface's corak-filter click — tapping a pill
                        // is quick access straight to that one machine's edit dialog instead of
                        // just filtering the list down to it.
                        modifier = Modifier.clickable { onPillClick(m) },
                        shape = RoundedCornerShape(4.dp),
                        color = colors.bgElevated,
                    ) {
                        Text(
                            m,
                            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.textSecondary),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Small bordered pill for a row's secondary specs (target yard, D405 speed, D408 koreksi) —
 * kept as a distinct tag rather than plain inline text so several can sit side by side without
 * running into each other visually. */
@Composable
private fun MetaTag(text: String) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.bgElevated,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textFaint),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MesinTab(
    state: DoffState,
    headerHeight: Dp,
    onSetMesin: (String, MesinData) -> Unit,
    onResetMesin: (String) -> Unit,
    showToast: (String) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
    onAddCorakShortcut: (String) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    var activeMcNo by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<MesinData?>(null) }
    var hadExistingData by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(MesinStatusFilter.ALL) }
    var selectedCorak by remember { mutableStateOf<String?>(null) }
    var consoleHeight by remember { mutableStateOf(0.dp) }

    fun loadFrom(mcNo: String, mesin: MesinData) {
        activeMcNo = mcNo
        form = mesin.copy()
        hadExistingData = mesin.corak.isNotEmpty() && mesin.corak != "-"
    }

    fun toggleMachineActive(mcNo: String, current: MesinData) {
        val nextActive = !current.isActive
        onSetMesin(mcNo, current.copy(isActive = nextActive))
        if (nextActive) {
            showToast("Mc $mcNo diaktifkan (ON) ✓")
        } else {
            showToast("Mc $mcNo stop produksi sementara (OFF) ⏸")
        }
    }

    // Mesin terkonfigurasi
    val configuredEntries = remember(state.db) {
        state.db.entries.filter { (_, v) -> v.corak.isNotEmpty() && v.corak != "-" }
    }

    val activeProduksiEntries = remember(configuredEntries) {
        configuredEntries.filter { (_, v) -> v.isActive }
    }

    val stoppedProduksiEntries = remember(configuredEntries) {
        configuredEntries.filter { (_, v) -> !v.isActive }
    }

    // Ringkasan corak aktif
    val activeCorakSummary = remember(activeProduksiEntries) {
        val map = mutableMapOf<String, MutableList<String>>()
        val tipeMap = mutableMapOf<String, MutableSet<MesinTipe>>()
        for ((mcNo, v) in activeProduksiEntries) {
            val c = v.corak.trim().uppercase()
            map.getOrPut(c) { mutableListOf() }.add(mcNo)
            tipeMap.getOrPut(c) { mutableSetOf() }.add(v.tipe)
        }
        map.map { (c, mcList) ->
            CorakSummaryItem(
                corak = c,
                machines = mcList.sortedBy { it.toIntOrNull() ?: 0 },
                tipes = tipeMap[c] ?: emptySet(),
            )
        }.sortedWith(compareByDescending<CorakSummaryItem> { it.machines.size }.thenBy { it.corak })
    }

    // Filtered entries
    val filteredEntries = remember(configuredEntries, statusFilter, selectedCorak, search) {
        val searchTrim = search.trim().uppercase()
        configuredEntries.filter { (k, v) ->
            if (statusFilter == MesinStatusFilter.ACTIVE && !v.isActive) return@filter false
            if (statusFilter == MesinStatusFilter.STOPPED && v.isActive) return@filter false
            if (selectedCorak != null && v.corak.trim().uppercase() != selectedCorak) return@filter false
            if (searchTrim.isNotEmpty()) {
                val mcMatch = k.contains(searchTrim)
                val corakMatch = v.corak.uppercase().contains(searchTrim)
                if (!mcMatch && !corakMatch) return@filter false
            }
            true
        }.sortedBy { (k, _) -> k.toIntOrNull() ?: 0 }
    }

    val groupedEntries = remember(filteredEntries) {
        val order = listOf(MesinTipe.TAPPET, MesinTipe.CAM, MesinTipe.D405, MesinTipe.D408)
        val byTipe = filteredEntries.groupBy { (_, v) -> v.tipe }
        order.mapNotNull { tipe -> byTipe[tipe]?.let { tipe to it } }
    }

    val unconfigured = remember(state.db, search) {
        val n = search.trim()
        if (n.matches(Regex("^\\d{1,4}$"))) {
            val existing = state.db[n]
            if (existing == null) {
                n to MesinData()
            } else if (existing.corak.isEmpty() || existing.corak == "-") {
                n to existing
            } else null
        } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "top_spacer") { Spacer(Modifier.height(10.dp + headerHeight + Dimens.Space16)) }

            // 1. Corak Summary Card
            item(key = "corak_summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .elevatedListCard(backgroundColor = colors.bgElevated2)
                        .padding(Dimens.Space12),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space10),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space6),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Texture,
                            contentDescription = null,
                            tint = Cyan500,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Corak Sedang Produksi",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        )
                    }
                    // Badges sit on their own row below the title (not squeezed inline beside it)
                    // so a long title never fights the badges for space — matches web's stacked
                    // .corak-summary-badges layout.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Emerald500.copy(alpha = 0.15f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Emerald500))
                                Text(
                                    "${activeProduksiEntries.size} Mesin Aktif (${activeCorakSummary.size} Corak)",
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald500),
                                )
                            }
                        }
                        if (stoppedProduksiEntries.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Amber500.copy(alpha = 0.15f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Amber500))
                                    Text(
                                        "${stoppedProduksiEntries.size} Stop Sementara",
                                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber500),
                                    )
                                }
                            }
                        }
                    }

                    if (activeCorakSummary.isEmpty()) {
                        Text(
                            "Tidak ada mesin yang aktif berproduksi saat ini.",
                            style = AppType.BodySmall.copy(color = colors.textFaint),
                        )
                    } else {
                        // Two-up grid, matching web's .corak-summary-grid — a single full-width
                        // column read as a much longer scroll for the same 11 corak.
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                            activeCorakSummary.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                                ) {
                                    rowItems.forEach { item ->
                                        CorakSummaryCard(
                                            item = item,
                                            isSelected = selectedCorak == item.corak,
                                            onToggleSelect = { selectedCorak = if (selectedCorak == item.corak) null else item.corak },
                                            onPillClick = { m -> loadFrom(m, state.db[m] ?: MesinData()) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Status Filter Tabs
            item(key = "status_filter_tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = statusFilter == MesinStatusFilter.ALL && selectedCorak == null,
                        onClick = {
                            statusFilter = MesinStatusFilter.ALL
                            selectedCorak = null
                        },
                        label = { Text("Semua (${configuredEntries.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan600,
                            selectedLabelColor = Color.White,
                            containerColor = colors.bgElevated2,
                            labelColor = colors.textSecondary,
                        ),
                    )
                    FilterChip(
                        selected = statusFilter == MesinStatusFilter.ACTIVE && selectedCorak == null,
                        onClick = {
                            statusFilter = MesinStatusFilter.ACTIVE
                            selectedCorak = null
                        },
                        leadingIcon = { Box(Modifier.size(6.dp).clip(CircleShape).background(Emerald500)) },
                        label = { Text("Aktif (${activeProduksiEntries.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan600,
                            selectedLabelColor = Color.White,
                            containerColor = colors.bgElevated2,
                            labelColor = colors.textSecondary,
                        ),
                    )
                    if (stoppedProduksiEntries.isNotEmpty()) {
                        FilterChip(
                            selected = statusFilter == MesinStatusFilter.STOPPED && selectedCorak == null,
                            onClick = {
                                statusFilter = MesinStatusFilter.STOPPED
                                selectedCorak = null
                            },
                            leadingIcon = { Box(Modifier.size(6.dp).clip(CircleShape).background(Amber500)) },
                            label = { Text("Stop (${stoppedProduksiEntries.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan600,
                                selectedLabelColor = Color.White,
                                containerColor = colors.bgElevated2,
                                labelColor = colors.textSecondary,
                            ),
                        )
                    }
                    if (selectedCorak != null) {
                        FilterChip(
                            selected = true,
                            onClick = { selectedCorak = null },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            },
                            label = { Text("Corak: $selectedCorak") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber500.copy(alpha = 0.2f),
                                selectedLabelColor = Amber500,
                            ),
                        )
                    }
                }
            }

            if (unconfigured != null) {
                item(key = "unconfigured_banner") {
                    val (n, m) = unconfigured
                    OutlinedButton(
                        onClick = { loadFrom(n, m) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.RadiusControl),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500),
                        border = BorderStroke(1.dp, Cyan500),
                    ) { Text("Konfigurasi Mc $n (belum diatur)") }
                }
            }

            if (groupedEntries.isEmpty()) {
                item(key = "empty") {
                    val isFiltered = search.isNotBlank() || selectedCorak != null || statusFilter != MesinStatusFilter.ALL
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.Space12)
                            .clip(RoundedCornerShape(Dimens.RadiusCard))
                            .border(1.dp, colors.border, RoundedCornerShape(Dimens.RadiusCard))
                            .padding(horizontal = Dimens.Space20, vertical = Dimens.Space24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (isFiltered) "Mesin Tidak Ditemukan" else "Belum Ada Mesin Terkonfigurasi",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (isFiltered) {
                                "Coba sesuaikan kata kunci pencarian atau bersihkan filter status"
                            } else {
                                "Masukkan nomor mesin pada kolom di bawah untuk mulai mengatur corak & tipe"
                            },
                            style = AppType.Caption.copy(color = colors.textMuted, lineHeight = 18.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // 3. Machine Group Rows
            groupedEntries.forEach { (tipe, rows) ->
                item(key = "head_${tipe.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bg)
                            .padding(vertical = Dimens.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                    ) {
                        MesinTipeIcon(
                            tipe = tipe,
                            tint = mesinTipeColor(tipe),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = tipe.name,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = mesinTipeColor(tipe)),
                        )
                        Text(
                            text = "${rows.size}",
                            style = AppType.Caption.copy(color = colors.textFaint),
                        )
                    }
                }
                items(rows, key = { (k, _) -> k }) { (k, v) ->
                    val isRunning = v.isActive
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isRunning) 1f else 0.72f)
                            .elevatedListCard(
                                backgroundColor = if (isRunning) {
                                    colors.bgElevated2
                                } else {
                                    Amber500.copy(alpha = 0.05f).compositeOver(colors.bgElevated2)
                                },
                                borderColor = if (isRunning) null else Amber500.copy(alpha = 0.4f),
                                dashedBorder = !isRunning,
                            )
                            .clickable { loadFrom(k, v) }
                            .padding(horizontal = Dimens.Space12, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space10),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            k,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            modifier = Modifier.width(36.dp),
                        )
                        Icon(
                            imageVector = Icons.Outlined.Texture,
                            contentDescription = null,
                            tint = colors.textFaint,
                            modifier = Modifier.size(13.dp),
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                v.corak,
                                style = AppType.FieldText.copy(color = colors.textPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Unweighted, this Text would claim the row's whole width before the
                                // STOP badge below gets a turn to be measured, squeezing/hiding the
                                // badge instead of the corak text shrinking to make room for it (the
                                // way web's flexbox does automatically). weight(fill = false) makes
                                // Compose measure the badge first and gives corak only what's left.
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (!isRunning) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Amber500.copy(alpha = 0.18f),
                                ) {
                                    Text(
                                        "STOP",
                                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, color = Amber500),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (v.targetYard != null) {
                                MetaTag("${formatYard(v.targetYard)}y")
                            }
                            if (v.speed != null && v.tipe == MesinTipe.D405) {
                                MetaTag("${formatYard(v.speed)}y/m")
                            }
                            val koreksi = v.koreksi
                            if (koreksi != null && v.tipe == MesinTipe.D408) {
                                MetaTag(if (koreksi > 0) "+${formatYard(koreksi)}m" else "${formatYard(koreksi)}m")
                            }
                        }

                        // Toggle ON/OFF button
                        Surface(
                            modifier = Modifier.clickable { toggleMachineActive(k, v) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRunning) Emerald500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isRunning) Emerald500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) Emerald500 else Amber500),
                                )
                                Text(
                                    if (isRunning) "ON" else "OFF",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRunning) Emerald500 else Amber500,
                                    ),
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.textFaint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(consoleHeight + Dimens.Space16)) }
        }

        EdgeFadeScrim(atTop = true, height = 10.dp + headerHeight + 16.dp)
        EdgeFadeScrim(atTop = false, height = consoleHeight + 16.dp)

        // Floating console bar for search & quick edit
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .onGloballyPositioned { coords ->
                    consoleHeight = with(density) { coords.size.height.toDp() }
                }
                .padding(horizontal = Dimens.Space12)
                .padding(bottom = Dimens.Space12)
                .floatingHeaderCard(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Space12)
                    .padding(vertical = 10.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Cari nomor mesin / corak", color = colors.textFaint) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(18.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber500,
                        unfocusedBorderColor = colors.border,
                        cursorColor = Amber500,
                        focusedContainerColor = colors.bgElevated2,
                        unfocusedContainerColor = colors.bgElevated2,
                    ),
                    shape = RoundedCornerShape(50.dp),
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = search.trim()
                        state.db[trimmed]?.let { mesin -> loadFrom(trimmed, mesin) }
                    }),
                    singleLine = true,
                )
                val directMc = state.db[search.trim()]
                Button(
                    onClick = {
                        val trimmed = search.trim()
                        if (directMc != null) {
                            loadFrom(trimmed, directMc)
                        } else if (trimmed.matches(Regex("^\\d{1,4}$"))) {
                            loadFrom(trimmed, MesinData())
                        }
                    },
                    enabled = search.isNotBlank() && (directMc != null || search.trim().matches(Regex("^\\d{1,4}$"))),
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan600,
                        disabledContainerColor = colors.bgElevated2,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit Mc $search",
                        tint = if (search.isNotBlank() && (directMc != null || search.trim().matches(Regex("^\\d{1,4}$")))) Color.White else colors.textFaint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    val mcNo = activeMcNo
    val f = form
    if (mcNo != null && f != null) {
        MesinEditPanel(
            mcNo = mcNo,
            form = f,
            showReset = hadExistingData,
            showToast = showToast,
            onFormChange = { form = it },
            onClose = { activeMcNo = null; form = null },
            onCancel = { activeMcNo = null; form = null },
            onReset = {
                showConfirm("Reset Mc $mcNo ke default? Corak, target yard, dan pengaturan lain akan dihapus.") {
                    onResetMesin(mcNo)
                    showToast("Mc $mcNo direset ke default")
                    activeMcNo = null; form = null
                }
            },
            onSave = {
                val corak = f.corak.trim().ifEmpty { "-" }
                onSetMesin(mcNo, f.copy(corak = corak))
                showToast("Mc $mcNo disimpan ✓")
                activeMcNo = null; form = null; search = ""
            },
            corakShortcuts = state.corakShortcuts,
            onAddCorakShortcut = onAddCorakShortcut,
        )
    }
}
