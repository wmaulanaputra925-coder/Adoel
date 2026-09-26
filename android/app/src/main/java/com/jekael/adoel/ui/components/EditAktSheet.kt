package com.jekael.adoel.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.AktualEntry
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.data.minOfDayToTimeStr
import com.jekael.adoel.data.parseJam
import com.jekael.adoel.data.standarisasiKeterangan
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** ket tersimpan sebagai "jam(extra)" atau cuma "jam" kalau tanpa keterangan tambahan (lihat
 * prosesBarisUmum di DoffViewModel.kt) — field Jam & Keterangan di sheet ini dulu digabung
 * jadi satu teks bebas berbasis ket, jadi mengedit jam berarti retype semuanya termasuk tanda
 * kurungnya. Dipisah supaya tiap field independen: corak/yard yang sudah benar tidak perlu
 * diketik ulang hanya karena mau mengoreksi jam, dan sebaliknya. */
private fun extractExtraKeterangan(ket: String, jam: String): String {
    if (!ket.startsWith(jam)) return ""
    val rest = ket.removePrefix(jam)
    val m = Regex("""^\(([^)]*)\)$""").matchEntire(rest)
    return m?.groupValues?.get(1) ?: ""
}

/** Which single field a tap on [com.jekael.adoel.ui.components.DoffEntryRowContent] asked to
 * edit — [ALL] is the old whole-row/pencil behavior (every section shown), the other four each
 * narrow the sheet to just that one field (Android equivalent of web's EditAktualDialog
 * `specificField`, see DoffEntryRow.tsx/EditAktualDialog.tsx). */
enum class EditAktField { ALL, JAM, KET, CORAK, YARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAktSheet(
    entry: AktualEntry,
    mesin: MesinData?,
    onClose: () -> Unit,
    onSave: (id: Int, jam: String, ket: String, corakOverride: String?, customYard: Double?) -> Unit,
    onDelete: () -> Unit,
    onInvalidYard: () -> Unit = {},
    onInvalidJam: () -> Unit = {},
    corakShortcuts: List<String>? = null,
    keteranganShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
    onAddKeteranganShortcut: (String) -> Unit = {},
    showToast: ((String) -> Unit)? = null,
    specificField: EditAktField = EditAktField.ALL,
) {
    val isSpecific = specificField != EditAktField.ALL
    val corakDefault = entry.corakOverride ?: mesin?.corak ?: ""
    val colors = LocalAppColors.current

    var jamInput by remember(entry.id) { mutableStateOf(entry.jam) }
    var ketInput by remember(entry.id) { mutableStateOf(extractExtraKeterangan(entry.ket, entry.jam)) }
    var corakInput by remember(entry.id) { mutableStateOf(corakDefault) }
    var yardInput by remember(entry.id) {
        mutableStateOf(entry.customYard?.let { formatYard(it) } ?: "")
    }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var showCheck by remember { mutableStateOf(false) }

    fun doSave() {
        if (showCheck) return
        val jamMin = parseJam(jamInput.trim())
        if (jamMin == null) {
            onInvalidJam()
            return
        }
        val corakTrim = corakInput.trim()
        val corakOverride = if (corakTrim.isNotEmpty() && corakTrim != (mesin?.corak ?: "")) corakTrim else null
        val yardTrim = yardInput.trim().replace(',', '.')
        if (yardTrim.isNotEmpty() && yardTrim.toDoubleOrNull() == null) {
            onInvalidYard()
            return
        }
        val yardVal = yardTrim.toDoubleOrNull()
        val jamStr = minOfDayToTimeStr(jamMin)
        val extra = standarisasiKeterangan(ketInput.trim())
        val newKet = if (extra.isNotEmpty()) "$jamStr($extra)" else jamStr
        showCheck = true
        scope.launch {
            delay(450)
            onSave(entry.id, jamStr, newKet, corakOverride, yardVal)
        }
    }

    LaunchedEffect(entry.id) {
        delay(100)
        focusRequester.requestFocus()
    }

    val dialogTitle = when (specificField) {
        EditAktField.JAM -> "Ubah Jam Potongan"
        EditAktField.KET -> if (ketInput.trim().isNotEmpty()) "Ubah Keterangan" else "Tambah Keterangan"
        EditAktField.CORAK -> "Ubah Corak Potongan"
        EditAktField.YARD -> "Ubah Yard Potongan"
        EditAktField.ALL -> "Edit Riwayat Potongan"
    }

    FloatingEditDialog(onDismissRequest = onClose) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
            ) {
                McBadgeBox(mcNo = entry.mcNo, boxWidth = 42.dp, boxHeight = 42.dp, numberFontSize = 16.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dialogTitle,
                        style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colors.textPrimary),
                    )
                    Text(
                        text = "Mc ${entry.mcNo} · Jam ${entry.jam}",
                        style = TextStyle(fontSize = 11.sp, color = colors.textFaint),
                    )
                }
                if (!isSpecific) {
                    IconButton(onClick = onDelete) { TrashIcon() }
                }
            }

            Spacer(Modifier.height(Dimens.Space20))

            if (!isSpecific || specificField == EditAktField.JAM) {
                FieldLabel("Jam")
                ClearableOutlinedTextField(
                    value = jamInput,
                    onValueChange = { jamInput = it },
                    modifier = Modifier.focusRequester(focusRequester),
                    placeholder = "14.30",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(Dimens.Space16))
            }

            if (!isSpecific || specificField == EditAktField.CORAK) {
                FieldLabel("Corak")
                ClearableOutlinedTextField(
                    value = corakInput,
                    onValueChange = { corakInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                CorakShortcutPicker(
                    value = corakInput,
                    onSelect = { corakInput = it },
                    shortcuts = corakShortcuts,
                    onAddShortcut = onAddCorakShortcut,
                    showToast = showToast,
                )
                Spacer(Modifier.height(Dimens.Space16))
            }

            if (!isSpecific || specificField == EditAktField.YARD) {
                FieldLabel("Panjang / Batas Potong (yard)")
                ClearableOutlinedTextField(
                    value = yardInput,
                    onValueChange = { yardInput = it },
                    placeholder = mesin?.targetYard?.let { "Standar: ${formatYard(it)}y" },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(Dimens.Space16))
            }

            if (!isSpecific || specificField == EditAktField.KET) {
                FieldLabel(if (isSpecific) "Keterangan" else "Keterangan (opsional)")
                OutlinedTextField(
                    value = ketInput,
                    onValueChange = { ketInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { doSave() }),
                    singleLine = true,
                )
                KeteranganShortcutPicker(
                    value = ketInput,
                    onSelect = { ketInput = it },
                    shortcuts = keteranganShortcuts,
                    onAddShortcut = onAddKeteranganShortcut,
                    showToast = showToast,
                )
            }

            Spacer(Modifier.height(Dimens.Space20))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    border = BorderStroke(1.dp, colors.border),
                ) { Text("Batal") }
                Button(
                    onClick = { doSave() },
                    enabled = !showCheck,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan600,
                        disabledContainerColor = Emerald500,
                        disabledContentColor = Color.White,
                    ),
                ) {
                    Crossfade(targetState = showCheck, label = "saveIcon") { checked ->
                        if (checked) CheckIcon() else Text("Simpan", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(Dimens.Space8))
    }
}
