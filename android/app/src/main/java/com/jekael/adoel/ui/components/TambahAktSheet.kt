package com.jekael.adoel.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.data.minOfDayToTimeStr
import com.jekael.adoel.data.parseJam
import com.jekael.adoel.data.standarisasiKeterangan
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Backfills a doff record straight into an already-archived Statistik shift — for a cut that got
 * missed while monitoring live and only gets noticed after "Selesai Shift" already closed the
 * shift out. Unlike [EditAktSheet] (which edits an entry that already exists), the machine number
 * itself is free-typed here since there's no existing [com.jekael.adoel.data.AktualEntry] to
 * anchor to yet — corak/target yard still prefill from [db] once a known Mc number is typed, same
 * convenience the guided Doffing sheet gives for a live entry. */
@Composable
fun TambahAktSheet(
    db: Map<String, MesinData>,
    onClose: () -> Unit,
    onSave: (mcNo: String, jam: String, ket: String, corakOverride: String?, customYard: Double?) -> Unit,
    onInvalidMcNo: () -> Unit = {},
    onInvalidYard: () -> Unit = {},
    onInvalidJam: () -> Unit = {},
    corakShortcuts: List<String>? = null,
    keteranganShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
    onAddKeteranganShortcut: (String) -> Unit = {},
    showToast: ((String) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    var mcNoInput by remember { mutableStateOf("") }
    var jamInput by remember { mutableStateOf("") }
    var corakInput by remember { mutableStateOf("") }
    var yardInput by remember { mutableStateOf("") }
    var ketInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var showCheck by remember { mutableStateOf(false) }

    val mesin = db[mcNoInput.trim()]
    // Prefills once a recognized Mc number resolves, same convenience as the guided sheets — but
    // only while corak/yard are still untouched, so it doesn't clobber something the operator
    // already typed if they correct the Mc number after starting to fill the rest in.
    LaunchedEffect(mesin) {
        if (mesin != null) {
            if (corakInput.isBlank()) corakInput = mesin.corak.takeIf { it != "-" } ?: ""
            if (yardInput.isBlank()) yardInput = mesin.targetYard?.let { formatYard(it) } ?: ""
        }
    }

    fun doSave() {
        if (showCheck) return
        val mcNoTrim = mcNoInput.trim()
        // No db membership check — mcNo here is only ever used for prefill convenience (mesin
        // above) and the AktualEntry record itself, neither of which needs the machine to already
        // exist in db. Requiring pre-registration would just be the same artificial cap the
        // console dropped for live entries.
        if (mcNoTrim.isEmpty()) {
            onInvalidMcNo()
            return
        }
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
            onSave(mcNoTrim, jamStr, newKet, corakOverride, yardVal)
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    FloatingEditDialog(onDismissRequest = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space10),
        ) {
            val mcTrim = mcNoInput.trim()
            if (mcTrim.isNotEmpty()) {
                McBadgeBox(mcNo = mcTrim, boxWidth = 42.dp, boxHeight = 42.dp, numberFontSize = 16.sp)
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Cyan500.copy(alpha = 0.14f))
                        .border(1.dp, Cyan500.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Outlined.ContentCut, contentDescription = null, tint = Cyan400, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(
                    text = "Tambah Potongan",
                    style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colors.textPrimary),
                )
                Text(
                    text = if (mcTrim.isNotEmpty() && mesin != null) "${mesin.corak} · ${mesin.tipe.name}" else "Catat doffing manual",
                    style = TextStyle(fontSize = 11.sp, color = colors.textFaint),
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space20))

        FieldLabel("Nomor Mesin")
        ClearableOutlinedTextField(
            value = mcNoInput,
            onValueChange = { mcNoInput = it.filter(Char::isDigit).take(3) },
            modifier = Modifier.focusRequester(focusRequester),
            placeholder = "cth: 12",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Jam")
        ClearableOutlinedTextField(
            value = jamInput,
            onValueChange = { jamInput = it },
            placeholder = "14.30",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(Dimens.Space16))

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

        FieldLabel("Panjang / Batas Potong (yard)")
        ClearableOutlinedTextField(
            value = yardInput,
            onValueChange = { yardInput = it },
            placeholder = mesin?.targetYard?.let { "Standar: ${formatYard(it)}y" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Keterangan (opsional)")
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
