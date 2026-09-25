package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.LocalAppColors

/**
 * Fast path for the machine fields that actually change often on the floor — tipe mesin, corak
 * and target yard — reachable by tapping a RadarCard directly instead of the full Pengaturan >
 * Mesin flow (search, open, edit, save, close, close). Speed/koreksi only show up when the
 * selected tipe actually needs them (D405/D408) and are only overwritten if the operator edits
 * them while that tipe is selected — switching tipe away and back doesn't lose the other tipe's
 * calibration. The fuller helpers (koreksi +/- stepper, "hitung dari jam") stay behind the full
 * Settings editor (MesinEditPanel) — this dialog is deliberately just the plain fields.
 */
@Composable
fun QuickEditCorakDialog(
    mcNo: String,
    tipe: MesinTipe,
    corak: String,
    targetYard: Double?,
    speed: Double?,
    koreksi: Double?,
    onDismiss: () -> Unit,
    onSave: (tipe: MesinTipe, corak: String, targetYard: Double?, speed: Double?, koreksi: Double?) -> Unit,
    corakShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
    showToast: ((String) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    var tipeInput by remember(mcNo) { mutableStateOf(tipe) }
    var corakInput by remember(mcNo) { mutableStateOf(if (corak == "-") "" else corak) }
    var targetYardInput by remember(mcNo) { mutableStateOf(targetYard?.let { formatYard(it) } ?: "") }
    var speedInput by remember(mcNo) { mutableStateOf(speed?.let { formatYard(it) } ?: "") }
    var koreksiInput by remember(mcNo) { mutableStateOf(koreksi?.let { formatYard(it) } ?: "") }

    FloatingEditDialog(onDismissRequest = onDismiss) {
        Text(
            text = "Ganti Cepat — Mc $mcNo",
            style = AppType.DialogTitle.copy(color = colors.textPrimary),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Tipe Mesin")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            MesinTipe.entries.forEach { t ->
                ChipBtn(t.name, tipeInput == t) { tipeInput = t }
            }
        }

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Corak")
        ClearableOutlinedTextField(
            value = corakInput,
            onValueChange = { corakInput = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        CorakShortcutPicker(
            value = corakInput,
            onSelect = { corakInput = it },
            shortcuts = corakShortcuts,
            onAddShortcut = onAddCorakShortcut,
            showToast = showToast,
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Target Yard")
        ClearableOutlinedTextField(
            value = targetYardInput,
            onValueChange = { targetYardInput = it },
            placeholder = "opsional",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        if (tipeInput == MesinTipe.D405) {
            Spacer(Modifier.height(Dimens.Space16))
            FieldLabel("Speed (yard/menit)")
            ClearableOutlinedTextField(
                value = speedInput,
                onValueChange = { speedInput = it },
                placeholder = "contoh: 0.158",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        if (tipeInput == MesinTipe.D408) {
            Spacer(Modifier.height(Dimens.Space16))
            FieldLabel("Koreksi Counter (menit)")
            ClearableOutlinedTextField(
                value = koreksiInput,
                onValueChange = { koreksiInput = it },
                placeholder = "contoh: 0 atau -15",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        Spacer(Modifier.height(Dimens.Space20))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
            ) { Text("Batal") }
            Button(
                onClick = {
                    val trimmed = corakInput.trim().ifEmpty { "-" }
                    // Blank clears the target on purpose; non-blank text that fails to parse falls
                    // back to the previous value instead of silently wiping it on a typo.
                    val yard = if (targetYardInput.isBlank()) {
                        null
                    } else {
                        targetYardInput.trim().replace(',', '.').toDoubleOrNull() ?: targetYard
                    }
                    // A tipe that isn't currently selected keeps its old speed/koreksi untouched —
                    // only the active tipe's field is allowed to overwrite it, so switching tipe
                    // away and back doesn't silently wipe the other tipe's calibration.
                    val newSpeed = if (tipeInput == MesinTipe.D405) {
                        if (speedInput.isBlank()) null else speedInput.trim().replace(',', '.').toDoubleOrNull() ?: speed
                    } else {
                        speed
                    }
                    val newKoreksi = if (tipeInput == MesinTipe.D408) {
                        if (koreksiInput.isBlank()) null else koreksiInput.trim().replace(',', '.').toDoubleOrNull() ?: koreksi
                    } else {
                        koreksi
                    }
                    onSave(tipeInput, trimmed, yard, newSpeed, newKoreksi)
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
            ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
        }
    }
}
