package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.Estimasi
import com.jekael.adoel.data.KETERANGAN_CODES
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.data.isPotonganAwalCorak
import com.jekael.adoel.data.potonganAwalReminderMessage
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.LocalAppColors

private enum class GuidedDoffingStep { SETUP, CHOOSE, NORMAL, KETERANGAN }

/** Terpandu (guided) DOFFING entry — Batch 5. Step 1 offers big buttons instead of free-text
 * ("Doffing normal" / "Ada keterangan"). Every path ends by building the identical command
 * string the Teks console would send for that action and handing it to the matching callback,
 * which the caller routes through the same handlers.handleCommand path Teks uses — so undo,
 * notification cancel/reschedule, and toast/haptic feedback all still apply exactly as they do
 * today.
 *
 * D408's counter reading is updated through the Estimasi button instead (its own field already
 * asks for "Bacaan jam counter" for that tipe) — this sheet used to also offer an "Update bacaan
 * counter" choice as a replacement for an old console-only "C" token shortcut, but with the
 * guided console's two direct action icons there's no mode-switch cost left to save, so that was
 * just a second path to the exact same result. Removed rather than kept as a redundant shortcut.
 *
 * A machine with no corak set yet gets an inline quick-setup step first (same corak/target-yard
 * fast path as [GuidedEstimasiSheet]) instead of being turned away — tapping the Doffing icon on
 * an unconfigured machine is exactly the case Master Blueprint v9.2 §3 calls out. */
@Composable
fun GuidedDoffingSheet(
    mcNo: String,
    mesin: MesinData?,
    estimasi: Estimasi?,
    onDismiss: () -> Unit,
    onSubmitDoffing: (value: String) -> Unit,
    onQuickUpdate: (corak: String, targetYard: Double?, tipe: MesinTipe, koreksi: Double?, speed: Double?) -> Unit,
    showToast: (String) -> Unit = {},
    corakShortcuts: List<String>? = null,
    keteranganShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
    onAddKeteranganShortcut: (String) -> Unit = {},
    corakPotonganAwal: List<String>? = null,
    showConfirm: (String, () -> Unit) -> Unit = { _, block -> block() },
) {
    val colors = LocalAppColors.current
    var activeMesin by remember(mcNo) { mutableStateOf(mesin) }
    val needsSetup = mesin == null || mesin.corak.isBlank() || mesin.corak.trim() == "-"
    var step by remember(mcNo) { mutableStateOf(if (needsSetup) GuidedDoffingStep.SETUP else GuidedDoffingStep.CHOOSE) }
    val standardYard = estimasi?.yardOverride ?: activeMesin?.targetYard

    fun handlePickMatching() {
        val corak = activeMesin?.corak
        if (isPotonganAwalCorak(corakPotonganAwal, corak)) {
            showConfirm(potonganAwalReminderMessage(corak!!)) { onSubmitDoffing("$mcNo MATCHING") }
        } else {
            onSubmitDoffing("$mcNo MATCHING")
        }
    }

    FloatingEditDialog(onDismissRequest = onDismiss) {
        Text(
            text = "Catat Doffing — Mc $mcNo",
            style = AppType.DialogTitle.copy(color = colors.textPrimary),
        )
        Spacer(Modifier.height(Dimens.Space16))

        when (step) {
            GuidedDoffingStep.SETUP -> SetupStep(
                initial = activeMesin ?: MesinData(),
                onSave = { corak, targetYard, tipe, koreksi, speed ->
                    activeMesin = MesinData(tipe, corak, targetYard, speed, koreksi)
                    onQuickUpdate(corak, targetYard, tipe, koreksi, speed)
                    step = GuidedDoffingStep.CHOOSE
                },
                onCancel = onDismiss,
                showToast = showToast,
                corakShortcuts = corakShortcuts,
                onAddCorakShortcut = onAddCorakShortcut,
            )
            GuidedDoffingStep.CHOOSE -> ChooseStep(
                onPickNormal = { step = GuidedDoffingStep.NORMAL },
                onPickMatching = { handlePickMatching() },
                onPickKeterangan = { step = GuidedDoffingStep.KETERANGAN },
                onCancel = onDismiss,
            )
            GuidedDoffingStep.NORMAL -> NormalYardStep(
                standardYard = standardYard,
                onBack = { step = GuidedDoffingStep.CHOOSE },
                onConfirm = { yard -> onSubmitDoffing("$mcNo $yard") },
            )
            GuidedDoffingStep.KETERANGAN -> KeteranganStep(
                standardYard = standardYard,
                onBack = { step = GuidedDoffingStep.CHOOSE },
                onConfirm = { cmd -> onSubmitDoffing("$mcNo $cmd") },
                keteranganShortcuts = keteranganShortcuts,
                onAddKeteranganShortcut = onAddKeteranganShortcut,
                showToast = showToast,
            )
        }
    }
}

@Composable
private fun SetupStep(
    initial: MesinData,
    onSave: (corak: String, targetYard: Double?, tipe: MesinTipe, koreksi: Double?, speed: Double?) -> Unit,
    onCancel: () -> Unit,
    showToast: (String) -> Unit,
    corakShortcuts: List<String>?,
    onAddCorakShortcut: (String) -> Unit,
) {
    MachineSetupForm(
        initial = initial,
        onSave = onSave,
        onCancel = onCancel,
        showToast = showToast,
        corakShortcuts = corakShortcuts,
        onAddCorakShortcut = onAddCorakShortcut,
    )
}

@Composable
private fun ChooseStep(
    onPickNormal: () -> Unit,
    onPickMatching: () -> Unit,
    onPickKeterangan: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BigChoiceButton(label = "Doffing normal", subtitle = "Selesai sesuai target yard", onClick = onPickNormal)
        BigChoiceButton(label = "Doffing matching", subtitle = "Potong sampel / cek kualitas beam baru", onClick = onPickMatching, accent = Color(0xFFF59E0B))
        BigChoiceButton(label = "Ada keterangan", subtitle = "HB, P.LP, P.SN, P.OH, P.EL, P.Sel, atau lainnya", onClick = onPickKeterangan)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
            border = BorderStroke(1.dp, colors.border),
        ) { Text("Batal") }
    }
}

@Composable
private fun BigChoiceButton(label: String, subtitle: String, onClick: () -> Unit, accent: Color = Cyan600) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.bgElevated2),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent))
            Text(subtitle, style = AppType.Caption.copy(color = colors.textFaint))
        }
    }
}

@Composable
private fun NormalYardStep(standardYard: Double?, onBack: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = LocalAppColors.current
    var yardInput by remember(standardYard) {
        mutableStateOf(standardYard?.let { formatYard(it) } ?: "")
    }
    YardDeltaField(standardYard = standardYard, yardInput = yardInput, onYardInputChange = { yardInput = it })

    Spacer(Modifier.height(Dimens.Space20))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
            border = BorderStroke(1.dp, colors.border),
        ) { Text("Kembali") }
        Button(
            onClick = { if (yardInput.isNotBlank()) onConfirm(yardInput.trim()) },
            enabled = yardInput.isNotBlank(),
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
        ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
    }
}

/** Yard field for [NormalYardStep] — prefilled from standard, nudged via tap targets since the
 * numeric keyboard (see ConsoleBar's Estimasi field) has no easy "+"/"-" key to type a delta like
 * Teks console's "+70". */
@Composable
private fun YardDeltaField(standardYard: Double?, yardInput: String, onYardInputChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    fun step(delta: Double) {
        val current = yardInput.trim().replace(',', '.').toDoubleOrNull() ?: standardYard ?: 0.0
        onYardInputChange(formatYard(current + delta))
    }

    FieldLabel("Yard aktual")
    OutlinedTextField(
        value = yardInput,
        onValueChange = onYardInputChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            if (standardYard != null) Text("Standar: ${formatYard(standardYard)}y", color = colors.textFaint)
        },
        colors = outlinedFieldColors(),
        shape = RoundedCornerShape(Dimens.RadiusControl),
        textStyle = AppType.FieldText.copy(color = colors.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(-5.0, -1.0, 1.0, 5.0).forEach { delta ->
            OutlinedButton(
                onClick = { step(delta) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = Dimens.Space8),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    if (delta > 0) "+${delta.toInt()}" else "${delta.toInt()}",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** Keterangan needs to cover more than the 6 preset codes (e.g. HB = habis beam, not really a
 * "kendala"/problem, just why the cut yard won't match standard) — chips are a quick-fill for the
 * common ones, but the text field underneath stays freely editable so anything not on that list
 * can still be typed, same freedom as the Teks console. Yard here is back to a plain manual field
 * (not [YardDeltaField]'s prefill+buttons) since in practice only HB ever needs a "+" delta —
 * one toggle button covers that instead of a whole row of tap targets most codes never use. */
@Composable
private fun KeteranganStep(
    standardYard: Double?,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
    keteranganShortcuts: List<String>?,
    onAddKeteranganShortcut: (String) -> Unit,
    showToast: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    var ket by remember { mutableStateOf("") }
    var yardInput by remember { mutableStateOf("") }
    fun toggleDelta() {
        yardInput = if (yardInput.startsWith("+")) yardInput.removePrefix("+") else "+" + yardInput.removePrefix("-")
    }

    FieldLabel("Keterangan")
    OutlinedTextField(
        value = ket,
        onValueChange = { ket = it.uppercase() },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Ketik keterangan (HB, P.LP, dll)", color = colors.textFaint) },
        colors = outlinedFieldColors(),
        shape = RoundedCornerShape(Dimens.RadiusControl),
        textStyle = AppType.FieldText.copy(color = colors.textPrimary),
        singleLine = true,
    )
    KeteranganShortcutPicker(
        value = ket,
        onSelect = { ket = it },
        shortcuts = keteranganShortcuts,
        onAddShortcut = onAddKeteranganShortcut,
        showToast = showToast,
    )

    Spacer(Modifier.height(14.dp))
    FieldLabel("Yard aktual (opsional)")
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = yardInput,
            onValueChange = { yardInput = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                val hint = if (standardYard != null) "Standar: ${formatYard(standardYard)}y" else "cth: 70"
                Text(hint, color = colors.textFaint)
            },
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            textStyle = AppType.FieldText.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        // Numeric keyboard has no "+" key — only HB in practice ever needs the cut read as a delta
        // off standard, so one toggle covers that instead of a whole row of +/-N buttons most
        // keterangan never use.
        OutlinedButton(
            onClick = ::toggleDelta,
            modifier = Modifier.height(56.dp),
            contentPadding = PaddingValues(horizontal = Dimens.Space16),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (yardInput.startsWith("+")) Cyan600 else colors.textSecondary,
            ),
            border = BorderStroke(1.dp, if (yardInput.startsWith("+")) Cyan600 else colors.border),
        ) { Text("+", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)) }
    }

    Spacer(Modifier.height(Dimens.Space20))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
            border = BorderStroke(1.dp, colors.border),
        ) { Text("Kembali") }
        Button(
            onClick = {
                val cmd = listOf(ket.trim(), yardInput.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                if (cmd.isNotBlank()) onConfirm(cmd)
            },
            enabled = ket.isNotBlank(),
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
        ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun FlowRowChips(codes: List<String>, selected: String?, onSelect: (String) -> Unit) {
    // Two fixed rows of up to 3 — six keterangan codes always fits, and this avoids pulling in
    // the separate accompanist/foundation FlowRow API just for a list this small and static.
    val rows = codes.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                row.forEach { code ->
                    Box(modifier = Modifier.weight(1f)) {
                        ChipBtn(label = code, selected = selected == code, onClick = { onSelect(code) })
                    }
                }
            }
        }
    }
}
