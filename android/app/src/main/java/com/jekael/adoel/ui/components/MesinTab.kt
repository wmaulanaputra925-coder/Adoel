package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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

/** Small bordered pill for a row's secondary specs (target yard, D405 speed, D408 koreksi, or the
 * tipe mesin tag when [tint] is given) — kept as a distinct tag rather than plain inline text so
 * several can sit side by side without running into each other visually. */
@Composable
private fun MetaTag(text: String, tint: Color? = null) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint?.copy(alpha = 0.14f) ?: colors.bgElevated,
        border = BorderStroke(1.dp, tint?.copy(alpha = 0.4f) ?: colors.border),
    ) {
        Text(
            text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (tint != null) FontWeight.Bold else FontWeight.SemiBold,
                color = tint ?: colors.textFaint,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

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
    var selectedCorak by remember { mutableStateOf<String?>(null) }
    var consoleHeight by remember { mutableStateOf(0.dp) }

    fun loadFrom(mcNo: String, mesin: MesinData) {
        activeMcNo = mcNo
        form = mesin.copy()
        hadExistingData = mesin.corak.isNotEmpty() && mesin.corak != "-"
    }

    // Mesin terkonfigurasi
    val configuredEntries = remember(state.db) {
        state.db.entries.filter { (_, v) -> v.corak.isNotEmpty() && v.corak != "-" }
    }

    // Ringkasan jumlah mesin per corak, untuk filter chip horizontal
    val corakSummary = remember(configuredEntries) {
        val map = mutableMapOf<String, Int>()
        for ((_, v) in configuredEntries) {
            val c = v.corak.trim().uppercase()
            map[c] = (map[c] ?: 0) + 1
        }
        map.entries
            .map { (corak, count) -> corak to count }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }

    // Daftar mesin yang difilter, urut nomor mesin (flat, tanpa pengelompokan tipe)
    val sortedEntries = remember(configuredEntries, selectedCorak, search) {
        val searchTrim = search.trim().uppercase()
        configuredEntries.filter { (k, v) ->
            if (selectedCorak != null && v.corak.trim().uppercase() != selectedCorak) return@filter false
            if (searchTrim.isNotEmpty()) {
                val mcMatch = k.contains(searchTrim)
                val corakMatch = v.corak.uppercase().contains(searchTrim)
                if (!mcMatch && !corakMatch) return@filter false
            }
            true
        }.sortedBy { (k, _) -> k.toIntOrNull() ?: 0 }
    }

    // Searching a number the list doesn't show is how a machine gets added: the mill's machine
    // count grows over time, and buildDefaultDb only seeds 1-174, so anything past that has to be
    // creatable from here. isNew separates "doesn't exist yet" from "exists but has no corak" so
    // the button below can say which one it is, same as web's searchedTarget.
    val unconfigured = remember(state.db, search) {
        val n = search.trim()
        if (n.matches(Regex("^\\d{1,4}$"))) {
            val existing = state.db[n]
            if (existing == null) {
                Triple(n, MesinData(), true)
            } else if (existing.corak.isEmpty() || existing.corak == "-") {
                Triple(n, existing, false)
            } else null
        } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "top_spacer") { Spacer(Modifier.height(10.dp + headerHeight + Dimens.Space16)) }

            // 1. Filter Corak Horizontal Cepat
            if (corakSummary.isNotEmpty()) {
                item(key = "corak_filter_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedCorak == null,
                            onClick = { selectedCorak = null },
                            label = { Text("Semua (${configuredEntries.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan600,
                                selectedLabelColor = Color.White,
                                containerColor = colors.bgElevated2,
                                labelColor = colors.textSecondary,
                            ),
                        )
                        corakSummary.forEach { (corak, count) ->
                            FilterChip(
                                selected = selectedCorak == corak,
                                onClick = { selectedCorak = if (selectedCorak == corak) null else corak },
                                label = { Text("$corak ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Cyan600,
                                    selectedLabelColor = Color.White,
                                    containerColor = colors.bgElevated2,
                                    labelColor = colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }

            if (unconfigured != null) {
                item(key = "unconfigured_banner") {
                    val (n, m, isNew) = unconfigured
                    OutlinedButton(
                        onClick = { loadFrom(n, m) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.RadiusControl),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500),
                        border = BorderStroke(1.dp, Cyan500),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                if (isNew) "Tambah Mesin Baru Mc $n" else "Konfigurasi Mc $n (belum diatur)",
                            )
                        }
                    }
                }
            }

            if (sortedEntries.isEmpty()) {
                item(key = "empty") {
                    val isFiltered = search.isNotBlank() || selectedCorak != null
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
                                "Coba sesuaikan kata kunci pencarian atau bersihkan filter corak"
                            } else {
                                "Masukkan nomor mesin pada kolom di bawah untuk mulai mengatur corak & tipe"
                            },
                            style = AppType.Caption.copy(color = colors.textMuted, lineHeight = 18.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // 2. Machine Rows (urut nomor mesin, tanpa pengelompokan tipe)
            items(sortedEntries, key = { (k, _) -> k }) { (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .elevatedListCard(
                            backgroundColor = colors.bgElevated2,
                            borderColor = colors.border,
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
                        tint = Cyan400,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        v.corak,
                        style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Black, color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetaTag(v.tipe.name, tint = mesinTipeColor(v.tipe))
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

                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.textFaint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(consoleHeight + Dimens.Space16)) }
        }

        // Top fade comes from SlideOverPanel, which owns the header this list scrolls behind —
        // only the fade for this tab's own floating console belongs here.
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
                    // maxLines = 1 matters here: this placeholder Text doesn't inherit the field's
                    // own singleLine=true (that only governs the editable value, not this slot), so
                    // without it "Cari mesin / corak" was wrapping to 2 lines and inflating the
                    // whole console's height well past web's compact single-line search pill.
                    placeholder = {
                        Text("Cari mesin / corak", color = colors.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
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
