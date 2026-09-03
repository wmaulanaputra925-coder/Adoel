package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.theme.*

@Composable
internal fun MesinEditPanel(
    mcNo: String,
    form: MesinData,
    showReset: Boolean,
    showToast: (String) -> Unit,
    onFormChange: (MesinData) -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    corakShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val f = form
    // Kept as raw text (not re-derived from the Double each recomposition) so a whole-number
    // value like 303 doesn't force an unwanted ".0" suffix, and mid-typing text like "303." isn't
    // reformatted out from under the user before they finish entering a decimal.
    var targetYardText by remember(mcNo) { mutableStateOf(f.targetYard?.let { formatYard(it) } ?: "") }
    var speedText by remember(mcNo) { mutableStateOf(f.speed?.let { formatYard(it) } ?: "") }
    var koreksiText by remember(mcNo) { mutableStateOf(f.koreksi?.let { formatYard(it) } ?: "") }
    // "Hitung Koreksi" helper (D408 only, see below) — waktuAktualText defaults to the current
    // wall-clock time so the operator usually only has to type the counter reading, but stays
    // editable in case they're reading it off a different clock than the phone's.
    var waktuAktualText by remember(mcNo) { mutableStateOf(nowTimeStr()) }
    var bacaanCounterText by remember(mcNo) { mutableStateOf("") }

    FloatingEditDialog(onDismissRequest = onClose) {
        Text(
            text = "Mc $mcNo",
            style = AppType.NumberLarge.copy(color = colors.textPrimary),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Status Produksi")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            val isRunning = f.isActive
            OutlinedButton(
                onClick = { onFormChange(f.copy(isActive = true)) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isRunning) Emerald500.copy(alpha = 0.15f) else colors.bgElevated2,
                    contentColor = if (isRunning) Emerald500 else colors.textMuted,
                ),
                border = BorderStroke(1.dp, if (isRunning) Emerald500 else colors.border),
            ) {
                Text(
                    text = "● Aktif (ON)",
                    fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                )
            }
            OutlinedButton(
                onClick = { onFormChange(f.copy(isActive = false)) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (!isRunning) Amber500.copy(alpha = 0.15f) else colors.bgElevated2,
                    contentColor = if (!isRunning) Amber500 else colors.textMuted,
                ),
                border = BorderStroke(1.dp, if (!isRunning) Amber500 else colors.border),
            ) {
                Text(
                    text = "● Stop (OFF)",
                    fontWeight = if (!isRunning) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Tipe Mesin")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            MesinTipe.entries.forEach { t ->
                ChipBtn(t.name, f.tipe == t) { onFormChange(f.copy(tipe = t)) }
            }
        }

        Spacer(Modifier.height(Dimens.Space20))

        FieldLabel("Corak")
        OutlinedTextField(
            value = f.corak,
            onValueChange = { onFormChange(f.copy(corak = it)) },
            modifier = Modifier.fillMaxWidth(),
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            textStyle = AppType.FieldText.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        CorakShortcutPicker(
            value = f.corak,
            onSelect = { onFormChange(f.copy(corak = it)) },
            shortcuts = corakShortcuts,
            onAddShortcut = onAddCorakShortcut,
            showToast = showToast,
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Target Yard")
        OutlinedTextField(
            value = targetYardText,
            onValueChange = {
                targetYardText = it
                onFormChange(f.copy(targetYard = it.replace(',', '.').toDoubleOrNull()))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("opsional", color = colors.textFaint) },
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            textStyle = AppType.FieldText.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        if (f.tipe == MesinTipe.D405) {
            Spacer(Modifier.height(Dimens.Space16))
            FieldLabel("Speed (yard/menit)")
            OutlinedTextField(
                value = speedText,
                onValueChange = {
                    speedText = it
                    onFormChange(f.copy(speed = it.replace(',', '.').toDoubleOrNull()))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("cth: 0.158", color = colors.textFaint) },
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }

        if (f.tipe == MesinTipe.D408) {
            Spacer(Modifier.height(Dimens.Space16))
            FieldLabel("Koreksi (menit)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    val value = (koreksiText.replace(',', '.').toDoubleOrNull() ?: 0.0) - 1.0
                    koreksiText = formatYard(value)
                    onFormChange(f.copy(koreksi = value))
                }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("-") }
                OutlinedTextField(
                    value = koreksiText,
                    onValueChange = {
                        koreksiText = it
                        onFormChange(f.copy(koreksi = it.replace(',', '.').toDoubleOrNull()))
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("cth: 18", color = colors.textFaint) },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedButton(onClick = {
                    val value = (koreksiText.replace(',', '.').toDoubleOrNull() ?: 0.0) + 1.0
                    koreksiText = formatYard(value)
                    onFormChange(f.copy(koreksi = value))
                }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
            }

            Spacer(Modifier.height(Dimens.Space12))
            FieldLabel("Hitung Koreksi dari Selisih")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                OutlinedTextField(
                    value = waktuAktualText,
                    onValueChange = { waktuAktualText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Waktu Aktual", color = colors.textFaint) },
                    placeholder = { Text("12.48", color = colors.textFaint) },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = bacaanCounterText,
                    onValueChange = { bacaanCounterText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Bacaan Counter", color = colors.textFaint) },
                    placeholder = { Text("12.30", color = colors.textFaint) },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(Dimens.Space8))
            OutlinedButton(
                onClick = {
                    val aktualMin = parseJam(waktuAktualText)
                    val counterMin = parseJam(bacaanCounterText)
                    when {
                        aktualMin == null -> showToast("Waktu Aktual tidak valid, format jam.menit (cth 12.48)")
                        counterMin == null -> showToast("Bacaan Counter tidak valid, format jam.menit (cth 12.30)")
                        else -> {
                            val selisih = selisihKoreksiD408(aktualMin, counterMin)
                            koreksiText = selisih.toString()
                            onFormChange(f.copy(koreksi = selisih.toDouble()))
                            showToast("Koreksi diisi otomatis: $selisih menit")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan600),
                border = BorderStroke(1.dp, colors.border),
            ) { Text("Hitung & Isi Koreksi") }
        }

        Spacer(Modifier.height(Dimens.Space20))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
            ) { Text("Batal") }
            if (showReset) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red400),
                    border = BorderStroke(1.dp, Red400),
                ) { Text("Reset") }
            }
            Button(
                onClick = {
                    val targetYardInvalid = targetYardText.isNotBlank() && f.targetYard == null
                    val speedInvalid = f.tipe == MesinTipe.D405 && speedText.isNotBlank() && f.speed == null
                    val koreksiInvalid = f.tipe == MesinTipe.D408 && koreksiText.isNotBlank() && f.koreksi == null
                    when {
                        targetYardInvalid -> showToast("Target Yard tidak valid, cek kembali")
                        speedInvalid -> showToast("Speed tidak valid, cek kembali")
                        koreksiInvalid -> showToast("Koreksi tidak valid, cek kembali")
                        else -> onSave()
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
            ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
        }
    }
}
