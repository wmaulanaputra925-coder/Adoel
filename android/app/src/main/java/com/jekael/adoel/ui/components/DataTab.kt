package com.jekael.adoel.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Each settings group sits in its own elevated card — matches the web app's
 * `.settings-section-card` (a flat divider-separated Column read as one long list there, one
 * setting per card here so a group's boundary is unambiguous while scrolling). */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .elevatedListCard(backgroundColor = colors.bgElevated)
            .padding(Dimens.Space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
        content = content,
    )
}

/** Section title: an icon (plain, or in a tinted badge for the two shortcut sections — mirrors
 * web's cyan/emerald `settings-section-header` badges) plus the title text, [danger] swapping
 * both to red for the Reset Data section. [trailing] hosts the shortcut-count tag / "Hapus
 * Semua" link that sits at the opposite end of the same row on the two shortcut sections. */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    danger: Boolean = false,
    badgeColor: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalAppColors.current
    val titleColor = if (danger) Red400 else colors.textPrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            if (badgeColor != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(6.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(16.dp))
            }
            Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = titleColor))
        }
        trailing()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DataTab(
    state: DoffState,
    headerHeight: Dp,
    onResetDb: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetOperator: (nama: String, grup: String) -> Unit,
    onExportJson: () -> String,
    onImport: (String) -> Unit,
    onAddKeteranganShortcut: (String) -> Unit,
    onRemoveKeteranganShortcut: (String) -> Unit,
    onResetKeteranganShortcuts: () -> Unit,
    onAddCorakShortcut: (String) -> Unit,
    onRemoveCorakShortcut: (String) -> Unit,
    onResetCorakShortcuts: () -> Unit,
    onAddCorakPotonganAwal: (String) -> Unit,
    onRemoveCorakPotonganAwal: (String) -> Unit,
    onResetCorakPotonganAwal: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    showToast: (String) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var operatorEditing by remember { mutableStateOf(false) }
    var newKetInput by remember { mutableStateOf("") }
    var newCorakInput by remember { mutableStateOf("") }
    var newPotonganAwalInput by remember { mutableStateOf("") }

    val ketShortcuts = remember(state.keteranganShortcuts) {
        state.keteranganShortcuts ?: DEFAULT_KETERANGAN_SHORTCUTS
    }
    val corakShortcuts = remember(state.corakShortcuts) {
        state.corakShortcuts ?: DEFAULT_CORAK_SHORTCUTS
    }
    val corakPotonganAwal = remember(state.corakPotonganAwal) {
        state.corakPotonganAwal ?: DEFAULT_CORAK_POTONGAN_AWAL
    }

    val currentTheme = remember(state.themeMode) {
        runCatching { ThemeMode.valueOf(state.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
    }

    // Backup: user picks where to save a .json file; we write the full-state JSON to it.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(onExportJson().toByteArray()) }
                }.isSuccess
            }
            showToast(if (ok) "Data dicadangkan ✓" else "⚠ Gagal menyimpan file")
        }
    }

    // Restore: user picks a backup file; we read its text and hand it to onImport (which confirms).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text != null) onImport(text) else showToast("⚠ Gagal membaca file")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        Spacer(Modifier.height(10.dp + headerHeight + Dimens.Space16))

        // 1. Identitas Operator — didata sekali saat pertama buka (OperatorDialog), diubah dari
        // sini kapan saja. Yang dibaca teks bagikan, bukan sekadar catatan: ditaruh paling atas
        // supaya operator yang laporannya "tanpa nama" langsung menemukan tempat mengisinya.
        SectionCard {
            SectionHeader(icon = Icons.Outlined.Badge, title = "Identitas Operator")
            Text(
                "Dicantumkan di kepala teks laporan yang dibagikan ke WhatsApp.",
                style = AppType.Caption.copy(color = colors.textMuted),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.operatorNama.ifBlank { "Belum diisi" },
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.operatorNama.isBlank()) colors.textFaint else colors.textPrimary,
                        ),
                    )
                    Text(
                        if (state.operatorGrup.isBlank()) "Grup belum diisi" else "Grup ${state.operatorGrup}",
                        style = AppType.Caption.copy(color = colors.textFaint),
                    )
                }
                ChipBtn("Ubah", selected = false, icon = Icons.Outlined.Edit) { operatorEditing = true }
            }
        }

        // 2. Tema Tampilan
        SectionCard {
            SectionHeader(icon = Icons.Outlined.LightMode, title = "Tema Tampilan")
            Text(
                "Pilih tema antarmuka yang nyaman untuk operasional kerja.",
                style = AppType.Caption.copy(color = colors.textMuted),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                ChipBtn("Sistem", currentTheme == ThemeMode.SYSTEM, icon = Icons.Outlined.Computer) { onSetThemeMode(ThemeMode.SYSTEM) }
                ChipBtn("Gelap", currentTheme == ThemeMode.DARK, icon = Icons.Outlined.DarkMode) { onSetThemeMode(ThemeMode.DARK) }
                ChipBtn("Terang", currentTheme == ThemeMode.LIGHT, icon = Icons.Outlined.LightMode) { onSetThemeMode(ThemeMode.LIGHT) }
            }
        }

        // 3. Shortcut Keterangan
        SectionCard {
            SectionHeader(
                icon = Icons.Outlined.Sell,
                title = "Shortcut Keterangan",
                badgeColor = Cyan400,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.bgElevated2)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "${ketShortcuts.size} shortcut",
                                style = AppType.Caption.copy(fontSize = 11.sp, color = colors.textMuted),
                            )
                        }
                        if (ketShortcuts.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showConfirm("Hapus semua shortcut keterangan kustom?") {
                                            onResetKeteranganShortcuts()
                                            showToast("Shortcut keterangan dikosongkan")
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                // Icon only: the label never fit cleanly beside the count badge in
                                // this header row. The confirm dialog spells the action out anyway,
                                // so the wording lives there and in the content description.
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Hapus semua shortcut",
                                    tint = Red400,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                },
            )
            Text(
                "Tombol cepat keterangan untuk pencatatan Doffing (cth: HB, P.LP, P.SN, GANTI BEAM).",
                style = AppType.Caption.copy(color = colors.textMuted),
            )

            if (ketShortcuts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ketShortcuts.forEach { shortcut ->
                        ShortcutTagChip(
                            text = shortcut,
                            onDelete = { onRemoveKeteranganShortcut(shortcut) },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.RadiusControl))
                        .background(colors.bgElevated2)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Belum ada shortcut keterangan khusus.",
                        style = AppType.Caption.copy(color = colors.textFaint),
                    )
                }
            }

            // Add Keterangan Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newKetInput,
                    onValueChange = { newKetInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ketik keterangan baru...", color = colors.textFaint) },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val clean = newKetInput.trim()
                        if (clean.isNotEmpty()) {
                            onAddKeteranganShortcut(clean)
                            newKetInput = ""
                        }
                    }),
                )
                Button(
                    onClick = {
                        val clean = newKetInput.trim()
                        if (clean.isNotEmpty()) {
                            onAddKeteranganShortcut(clean)
                            newKetInput = ""
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah")
                }
            }
        }

        // 4. Shortcut Kode Corak
        SectionCard {
            SectionHeader(
                icon = Icons.Outlined.Texture,
                title = "Shortcut Kode Corak",
                badgeColor = Emerald500,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.bgElevated2)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "${corakShortcuts.size} shortcut",
                                style = AppType.Caption.copy(fontSize = 11.sp, color = colors.textMuted),
                            )
                        }
                        if (corakShortcuts.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showConfirm("Hapus semua shortcut kode corak kustom?") {
                                            onResetCorakShortcuts()
                                            showToast("Shortcut corak dikosongkan")
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                // Icon only: the label never fit cleanly beside the count badge in
                                // this header row. The confirm dialog spells the action out anyway,
                                // so the wording lives there and in the content description.
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Hapus semua shortcut",
                                    tint = Red400,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                },
            )
            Text(
                "Tombol cepat kode corak/kain untuk formulir mesin dan penggantian corak (cth: 4500, 4505, 5000, RAYON-30).",
                style = AppType.Caption.copy(color = colors.textMuted),
            )

            if (corakShortcuts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    corakShortcuts.forEach { shortcut ->
                        ShortcutTagChip(
                            text = shortcut,
                            onDelete = { onRemoveCorakShortcut(shortcut) },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.RadiusControl))
                        .background(colors.bgElevated2)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Belum ada shortcut kode corak khusus.",
                        style = AppType.Caption.copy(color = colors.textFaint),
                    )
                }
            }

            // Add Corak Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newCorakInput,
                    onValueChange = { newCorakInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ketik kode corak baru...", color = colors.textFaint) },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val clean = newCorakInput.trim()
                        if (clean.isNotEmpty()) {
                            onAddCorakShortcut(clean)
                            newCorakInput = ""
                        }
                    }),
                )
                Button(
                    onClick = {
                        val clean = newCorakInput.trim()
                        if (clean.isNotEmpty()) {
                            onAddCorakShortcut(clean)
                            newCorakInput = ""
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah")
                }
            }
        }

        // 3b. Corak Potongan Awal 70y
        SectionCard {
            SectionHeader(
                icon = Icons.Outlined.ContentCut,
                title = "Corak Potongan Awal 70y",
                badgeColor = Amber500,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.bgElevated2)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "${corakPotonganAwal.size} corak",
                                style = AppType.Caption.copy(fontSize = 11.sp, color = colors.textMuted),
                            )
                        }
                        if (corakPotonganAwal.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showConfirm("Kembalikan daftar ke 3 corak standar (80125, 21242, 66335)?") {
                                            onResetCorakPotonganAwal()
                                            showToast("Daftar dikembalikan ke default ✓")
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.RestartAlt,
                                    contentDescription = "Setel ke default",
                                    tint = colors.textFaint,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                },
            )
            Text(
                "Untuk corak di daftar ini, sampel Doffing Matching (1 yard) baru diambil setelah beam jalan minimal 70 yard — bukan langsung dari 0 — supaya sampel tidak kena cacat LTK/lusi putus di awal jalan. Pengingat ini muncul saat memilih aksi Doffing Matching.",
                style = AppType.Caption.copy(color = colors.textMuted),
            )

            if (corakPotonganAwal.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    corakPotonganAwal.forEach { corak ->
                        ShortcutTagChip(
                            text = corak,
                            onDelete = { onRemoveCorakPotonganAwal(corak) },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.RadiusControl))
                        .background(colors.bgElevated2)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Belum ada corak dengan aturan potongan awal.",
                        style = AppType.Caption.copy(color = colors.textFaint),
                    )
                }
            }

            // Add Corak Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newPotonganAwalInput,
                    onValueChange = { newPotonganAwalInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tambah kode corak (cth: 80125)...", color = colors.textFaint) },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val clean = newPotonganAwalInput.trim()
                        if (clean.isNotEmpty()) {
                            onAddCorakPotonganAwal(clean)
                            newPotonganAwalInput = ""
                        }
                    }),
                )
                Button(
                    onClick = {
                        val clean = newPotonganAwalInput.trim()
                        if (clean.isNotEmpty()) {
                            onAddCorakPotonganAwal(clean)
                            newPotonganAwalInput = ""
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah")
                }
            }
        }

        // 5. Cadangan & Pemulihan
        SectionCard {
            SectionHeader(icon = Icons.Outlined.Storage, title = "Cadangan & Pemulihan")
            Text(
                "Cadangkan seluruh data (database mesin, estimasi aktif, riwayat shift, tema) ke file JSON, atau pulihkan dari file cadangan sebelumnya.",
                style = AppType.Caption.copy(color = colors.textMuted),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        runCatching { exportLauncher.launch("adoel-backup-$stamp.json") }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cadangkan")
                }
                OutlinedButton(
                    onClick = {
                        runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500),
                    border = BorderStroke(1.dp, Cyan500),
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pulihkan")
                }
            }
        }

        // 6. Reset Data
        SectionCard {
            SectionHeader(icon = Icons.Outlined.WarningAmber, title = "Reset Data", danger = true)
            Text(
                "Mengembalikan database mesin ke konfigurasi bawaan pabrik dan menghapus seluruh estimasi serta riwayat shift.",
                style = AppType.Caption.copy(color = colors.textMuted),
            )
            OutlinedButton(
                onClick = {
                    showConfirm("Reset semua data ke default? Estimasi aktif & riwayat shift akan hilang secara permanen.") {
                        onResetDb()
                        showToast("Data direset ke default")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red400),
                border = BorderStroke(1.dp, Red700.copy(alpha = 0.5f)),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset Semua ke Default")
            }
        }

        // 7. Bantuan & Informasi
        SectionCard {
            SectionHeader(icon = Icons.Outlined.Info, title = "Bantuan & Informasi")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                OutlinedButton(
                    onClick = onOpenHelp,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Panduan")
                }
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tentang")
                }
            }
        }

        Spacer(Modifier.height(Dimens.Space24))
    }

    // Dialog yang sama persis dengan yang muncul saat pertama kali aplikasi dibuka — satu form,
    // satu tempat perbaikannya kalau bidangnya bertambah.
    if (operatorEditing) {
        OperatorDialog(
            nama = state.operatorNama,
            grup = state.operatorGrup,
            onDismiss = { operatorEditing = false },
            onSave = { nama, grup ->
                onSetOperator(nama, grup)
                operatorEditing = false
                showToast("Identitas operator disimpan")
            },
        )
    }
}

@Composable
private fun ShortcutTagChip(
    text: String,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.bgElevated2)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = text,
            style = AppType.Caption.copy(
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                fontSize = 12.sp,
            ),
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable { onDelete() }
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Hapus shortcut $text",
                tint = colors.textFaint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
