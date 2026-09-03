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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DataTab(
    state: DoffState,
    headerHeight: Dp,
    onResetDb: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onExportJson: () -> String,
    onImport: (String) -> Unit,
    onAddKeteranganShortcut: (String) -> Unit,
    onRemoveKeteranganShortcut: (String) -> Unit,
    onResetKeteranganShortcuts: () -> Unit,
    onAddCorakShortcut: (String) -> Unit,
    onRemoveCorakShortcut: (String) -> Unit,
    onResetCorakShortcuts: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    showToast: (String) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var newKetInput by remember { mutableStateOf("") }
    var newCorakInput by remember { mutableStateOf("") }

    val ketShortcuts = remember(state.keteranganShortcuts) {
        state.keteranganShortcuts ?: DEFAULT_KETERANGAN_SHORTCUTS
    }
    val corakShortcuts = remember(state.corakShortcuts) {
        state.corakShortcuts ?: DEFAULT_CORAK_SHORTCUTS
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(10.dp + headerHeight + Dimens.Space16))

        // 1. Tema Tampilan
        FieldLabel("Tema Tampilan")
        Text(
            "Pilih tema antarmuka yang nyaman untuk operasional kerja.",
            style = AppType.Caption.copy(color = colors.textMuted),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            ChipBtn("Sistem", currentTheme == ThemeMode.SYSTEM) { onSetThemeMode(ThemeMode.SYSTEM) }
            ChipBtn("Gelap", currentTheme == ThemeMode.DARK) { onSetThemeMode(ThemeMode.DARK) }
            ChipBtn("Terang", currentTheme == ThemeMode.LIGHT) { onSetThemeMode(ThemeMode.LIGHT) }
        }

        Spacer(Modifier.height(Dimens.Space4))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(Dimens.Space4))

        // 2. Shortcut Keterangan
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                FieldLabel("Shortcut Keterangan")
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
            }
            if (ketShortcuts.isNotEmpty()) {
                Text(
                    "Hapus Semua",
                    style = AppType.Caption.copy(color = Red400, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            showConfirm("Hapus semua shortcut keterangan kustom?") {
                                onResetKeteranganShortcuts()
                                showToast("Shortcut keterangan dikosongkan")
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
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

        Spacer(Modifier.height(Dimens.Space4))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(Dimens.Space4))

        // 3. Shortcut Kode Corak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                FieldLabel("Shortcut Kode Corak")
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
            }
            if (corakShortcuts.isNotEmpty()) {
                Text(
                    "Hapus Semua",
                    style = AppType.Caption.copy(color = Red400, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            showConfirm("Hapus semua shortcut kode corak kustom?") {
                                onResetCorakShortcuts()
                                showToast("Shortcut corak dikosongkan")
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
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

        Spacer(Modifier.height(Dimens.Space4))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(Dimens.Space4))

        // 4. Cadangan & Pemulihan
        FieldLabel("Cadangan & Pemulihan Data")
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
            ) { Text("Cadangkan Data") }
            OutlinedButton(
                onClick = {
                    runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500),
                border = BorderStroke(1.dp, Cyan500),
            ) { Text("Pulihkan Data") }
        }

        Spacer(Modifier.height(Dimens.Space4))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(Dimens.Space4))

        // 5. Reset Data
        FieldLabel("Reset Data")
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
        ) { Text("Reset Semua ke Default") }

        Spacer(Modifier.height(Dimens.Space4))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(Dimens.Space4))

        // 6. Bantuan & Informasi
        FieldLabel("Bantuan & Informasi")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            OutlinedButton(
                onClick = onOpenHelp,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
            ) { Text("Panduan Penggunaan") }
            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
            ) { Text("Tentang Adoel") }
        }

        Spacer(Modifier.height(Dimens.Space24))
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
