package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.data.nowTimeStr
import com.jekael.adoel.data.parseJam
import com.jekael.adoel.data.selisihKoreksiD408
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.LocalAppColors

@Composable
internal fun MachineSetupForm(
    initial: MesinData,
    onSave: (corak: String, targetYard: Double?, tipe: MesinTipe, koreksi: Double?, speed: Double?) -> Unit,
    onCancel: () -> Unit,
    showToast: ((String) -> Unit)? = null,
    corakShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
) {
    val colors = LocalAppColors.current
    var tipe by remember { mutableStateOf(initial.tipe) }
    var corak by remember { mutableStateOf(initial.corak.takeUnless { it == "-" } ?: "") }
    var targetYard by remember { mutableStateOf(initial.targetYard?.let(::formatYard) ?: "") }
    var speed by remember { mutableStateOf(initial.speed?.let(::formatYard) ?: "") }
    var koreksi by remember { mutableStateOf(initial.koreksi?.let(::formatYard) ?: "") }
    var waktuAktual by remember { mutableStateOf(nowTimeStr()) }
    var bacaanCounter by remember { mutableStateOf("") }

    fun koreksiValue(): Double? = koreksi.replace(',', '.').toDoubleOrNull()
    fun nudge(delta: Double) {
        koreksi = formatYard((koreksiValue() ?: 0.0) + delta)
    }

    FieldLabel("Tipe Mesin")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        MesinTipe.entries.forEach { candidate ->
            ChipBtn(candidate.name, tipe == candidate) {
                tipe = candidate
                if (candidate != MesinTipe.D405) speed = ""
                if (candidate != MesinTipe.D408) koreksi = ""
            }
        }
    }
    Spacer(Modifier.height(Dimens.Space16))
    FieldLabel("Corak")
    ClearableOutlinedTextField(
        value = corak,
        onValueChange = { corak = it.uppercase() },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    CorakShortcutPicker(
        value = corak,
        onSelect = { corak = it },
        shortcuts = corakShortcuts,
        onAddShortcut = onAddCorakShortcut,
        showToast = showToast,
    )
    Spacer(Modifier.height(Dimens.Space12))
    FieldLabel("Target Yard")
    ClearableOutlinedTextField(
        value = targetYard,
        onValueChange = { targetYard = it },
        placeholder = "cth: 303",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )

    if (tipe == MesinTipe.D405) {
        Spacer(Modifier.height(Dimens.Space12))
        FieldLabel("Speed (yard/menit)")
        ClearableOutlinedTextField(
            value = speed,
            onValueChange = { speed = it },
            placeholder = "cth: 0.158",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
    if (tipe == MesinTipe.D408) {
        Spacer(Modifier.height(Dimens.Space12))
        FieldLabel("Koreksi (menit)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { nudge(-1.0) }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), border = BorderStroke(1.dp, colors.border)) { Text("-") }
            ClearableOutlinedTextField(
                value = koreksi,
                onValueChange = { koreksi = it },
                modifier = Modifier.weight(1f),
                placeholder = "cth: 18",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedButton(onClick = { nudge(1.0) }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), border = BorderStroke(1.dp, colors.border)) { Text("+") }
        }
        Spacer(Modifier.height(Dimens.Space12))
        FieldLabel("Hitung Koreksi dari Selisih")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            ClearableOutlinedTextField(value = waktuAktual, onValueChange = { waktuAktual = it }, modifier = Modifier.weight(1f), label = "Waktu Aktual", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            ClearableOutlinedTextField(value = bacaanCounter, onValueChange = { bacaanCounter = it }, modifier = Modifier.weight(1f), label = "Bacaan Counter", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Spacer(Modifier.height(Dimens.Space8))
        OutlinedButton(
            onClick = {
                val actual = parseJam(waktuAktual)
                val counter = parseJam(bacaanCounter)
                if (actual != null && counter != null) koreksi = selisihKoreksiD408(actual, counter).toString()
                else showToast?.invoke("Waktu dan bacaan counter harus berformat jam.menit")
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan600),
            border = BorderStroke(1.dp, colors.border),
        ) { Text("Hitung & Isi Koreksi") }
    }

    Spacer(Modifier.height(Dimens.Space20))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(Dimens.RadiusControl), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary), border = BorderStroke(1.dp, colors.border)) { Text("Batal") }
        Button(
            onClick = {
                val yard = targetYard.replace(',', '.').toDoubleOrNull()
                val machineSpeed = speed.replace(',', '.').toDoubleOrNull()
                val machineKoreksi = koreksiValue()
                when {
                    corak.isBlank() || yard == null -> showToast?.invoke("Corak dan Target Yard wajib diisi")
                    tipe == MesinTipe.D405 && (machineSpeed == null || machineSpeed <= 0) -> showToast?.invoke("Speed D405 wajib diisi")
                    tipe == MesinTipe.D408 && machineKoreksi == null -> showToast?.invoke("Koreksi D408 wajib diisi")
                    else -> onSave(
                        corak.trim(),
                        yard,
                        tipe,
                        machineKoreksi.takeIf { tipe == MesinTipe.D408 },
                        machineSpeed.takeIf { tipe == MesinTipe.D405 },
                    )
                }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
        ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
    }
}
