package com.jekael.adoel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.Estimasi
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.data.absMinToTimeStr
import com.jekael.adoel.data.effectiveRemaining
import com.jekael.adoel.data.estAbsD408
import com.jekael.adoel.data.estimasiFieldHint
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.data.nowAbsMin
import com.jekael.adoel.data.parseDurasi
import com.jekael.adoel.data.parseJam
import com.jekael.adoel.data.sisaMenitD405
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Terpandu (guided) ESTIMASI entry — one field whose label/keyboard adapt to the tapped
 * machine's [MesinTipe], with a live "≈ jam" preview computed from the exact same pure formulas
 * DoffViewModel uses, so what's previewed here is guaranteed to match what actually gets saved.
 * On Simpan, builds the identical "$mcNo $value" string the console used to send in Teks mode and
 * hands it to [onSubmit] — the caller routes it through handlers.handleCommand so the overwrite
 * guard, notification scheduling, and toast/haptic feedback all still apply.
 *
 * A machine with no corak set yet (fresh install, or one that was never configured in Pengaturan)
 * gets an inline quick-setup step first instead of being turned away to go set it up elsewhere —
 * corak/target yard are the two fields that change constantly on the floor (Master Blueprint §4C),
 * so this dialog is the fast path for them same as [QuickEditCorakDialog] is from a RadarCard tap.
 */
@Composable
fun GuidedEstimasiSheet(
    mcNo: String,
    mesin: MesinData?,
    onDismiss: () -> Unit,
    onSubmit: (value: String) -> Unit,
    onQuickUpdate: (corak: String, targetYard: Double?, tipe: MesinTipe, koreksi: Double?, speed: Double?) -> Unit,
    showToast: (String) -> Unit = {},
    corakShortcuts: List<String>? = null,
    onAddCorakShortcut: (String) -> Unit = {},
    // The estimasi already running for this Mc, if any — e.g. tapping RadarCard's own waktu
    // zone to correct a live countdown, as opposed to setting one up fresh from the console.
    // When present, the field opens pre-filled with roughly that reading instead of blank, so
    // nudging a countdown that's already close to right doesn't mean retyping it from scratch.
    existing: Estimasi? = null,
) {
    val colors = LocalAppColors.current
    var activeMesin by remember(mcNo) { mutableStateOf(mesin) }
    val tipe = activeMesin?.tipe ?: MesinTipe.TAPPET
    var needQuickCorakSetup by remember(mcNo) { mutableStateOf(mesin == null || mesin.corak.isBlank() || mesin.corak.trim() == "-") }

    var corakInput by remember(mcNo) { mutableStateOf("") }
    var targetYardInput by remember(mcNo) { mutableStateOf("") }
    var valueInput by remember(mcNo) { mutableStateOf("") }
    // Guards the pre-fill effect below the same way the old wheel's `wheelTouched` guarded
    // Simpan: once the operator has actually typed something, [existing] ticking live (its
    // countdown moves every render) must never overwrite it back out from under them.
    var valueTouched by remember(mcNo) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // TAPPET/CAM ("sisa waktu") and D408 ("bacaan jam counter") pre-fill as an "H.MM" string from
    // the estimasi's current reading when editing one already running — TAPPET/CAM from its own
    // live remaining time, D408 by reconstructing the counter reading its koreksi was computed
    // against: estAbsD408 is `jamKeShiftAbs(counter, now) + koreksi`, and jamKeShiftAbs's own job
    // is just "snap a bare time-of-day reading onto the calendar day nearest now" — so inverting
    // it is exactly the time-of-day component of (estAbsMin − koreksi), no day-snapping needed
    // since that's already baked into estAbsMin. D405 ("yard sudah berjalan") isn't a time reading
    // at all, so it's left untouched: blank until typed, as before. A LaunchedEffect (not a
    // one-shot `remember` initializer) on purpose — [existing] can still be null on the very first
    // composition if the caller's own state hasn't finished resolving it yet, and a `remember`
    // block only ever runs once, so a same-frame null there would leave the field permanently
    // blank even once [existing] resolves a moment later. This re-fires whenever [existing] or
    // [tipe] change but bails immediately once [valueTouched], so it can never clobber typing.
    LaunchedEffect(mcNo, existing, tipe) {
        if (valueTouched) return@LaunchedEffect
        val prefill = when {
            existing == null -> return@LaunchedEffect
            tipe == MesinTipe.TAPPET || tipe == MesinTipe.CAM -> {
                val totalMin = existing.effectiveRemaining(nowAbsMin()).toInt().coerceAtLeast(0)
                "${totalMin / 60}.${(totalMin % 60).toString().padStart(2, '0')}"
            }
            tipe == MesinTipe.D408 -> {
                val koreksi = activeMesin?.koreksi ?: 0.0
                val totalMin = (existing.estAbsMin - koreksi.roundToInt()).mod(1440L).toInt()
                "${totalMin / 60}.${(totalMin % 60).toString().padStart(2, '0')}"
            }
            else -> return@LaunchedEffect
        }
        valueInput = prefill
    }

    LaunchedEffect(mcNo, needQuickCorakSetup) {
        if (!needQuickCorakSetup) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    FloatingEditDialog(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space10),
        ) {
            McBadgeBox(mcNo = mcNo, boxWidth = 44.dp, boxHeight = 44.dp, numberFontSize = 17.sp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Update Estimasi",
                        style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colors.textPrimary),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Cyan500.copy(alpha = 0.12f))
                            .border(1.dp, Cyan500.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(tipe.name, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Cyan400))
                    }
                }
                val subtitle = remember(activeMesin) {
                    buildList {
                        add("Mc $mcNo")
                        add(activeMesin?.corak?.takeIf { it.isNotBlank() && it != "-" } ?: "Belum diatur")
                        activeMesin?.targetYard?.let { add("${formatYard(it)}y") }
                    }.joinToString(" • ")
                }
                Text(subtitle, style = TextStyle(fontSize = 11.5.sp, color = colors.textFaint))
            }
        }
        Spacer(Modifier.height(Dimens.Space16))

        if (needQuickCorakSetup) {
            MachineSetupForm(
                initial = activeMesin ?: MesinData(),
                onSave = { corak, targetYard, selectedTipe, koreksi, speed ->
                    val updated = MesinData(selectedTipe, corak, targetYard, speed, koreksi)
                    activeMesin = updated
                    onQuickUpdate(corak, targetYard, selectedTipe, koreksi, speed)
                    needQuickCorakSetup = false
                },
                onCancel = onDismiss,
                showToast = showToast,
                corakShortcuts = corakShortcuts,
                onAddCorakShortcut = onAddCorakShortcut,
            )
        } else {
            val hint = estimasiFieldHint(tipe)
            val preview = remember(valueInput, activeMesin) { previewEstimasi(tipe, valueInput, activeMesin) }

            // One-shot cyan "water jet" pulse sweeping from the Simpan button toward the field's
            // left edge on submit — echoes the nozzle spraying the weft thread across the loom
            // (Master Blueprint §3F). onSubmit's success path clears activeOverlay synchronously
            // (see MainScreen's onCleared callback), which tears down this whole dialog the instant
            // it's called — so the animation has to run to completion *before* onSubmit fires, not
            // alongside it as fire-and-forget, or the dialog would vanish before a single animated
            // frame ever reached the screen.
            val nozzleProgress = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            fun submit() {
                if (valueInput.isBlank()) return
                val toSubmit = "$mcNo $valueInput"
                scope.launch {
                    nozzleProgress.snapTo(0f)
                    nozzleProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                    onSubmit(toSubmit)
                }
            }

            FieldLabel(hint.label)
            Box(modifier = Modifier.fillMaxWidth()) {
                ClearableOutlinedTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it; valueTouched = true },
                    modifier = Modifier.focusRequester(focusRequester),
                    placeholder = "cth: ${hint.example}",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                if (nozzleProgress.value > 0f && nozzleProgress.value < 1f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val progress = nozzleProgress.value
                        val y = size.height / 2f
                        val headX = size.width * (1f - progress)
                        drawLine(
                            color = Cyan500.copy(alpha = (1f - progress) * 0.9f),
                            start = Offset(size.width, y),
                            end = Offset(headX, y),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()), phase = progress * 40f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = preview?.let { "≈ jam $it" } ?: "Isi untuk melihat perkiraan jam",
                style = AppType.Caption.copy(color = if (preview != null) Cyan600 else colors.textFaint),
            )

            Spacer(Modifier.height(Dimens.Space20))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    border = BorderStroke(1.dp, colors.border),
                ) { Text("Batal") }
                Button(
                    onClick = ::submit,
                    enabled = valueInput.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private fun previewEstimasi(tipe: MesinTipe, raw: String, mesin: MesinData?): String? {
    if (raw.isBlank()) return null
    val now = nowAbsMin()
    return when (tipe) {
        MesinTipe.TAPPET, MesinTipe.CAM -> parseDurasi(raw)?.let { sisa -> absMinToTimeStr(now + sisa) }
        MesinTipe.D405 -> {
            val target = mesin?.targetYard
            val speed = mesin?.speed
            val yardBerjalan = raw.trim().trimEnd('y', 'Y').replace(',', '.').toDoubleOrNull()
            if (yardBerjalan != null && target != null && speed != null && speed > 0) {
                absMinToTimeStr(now + sisaMenitD405(target, yardBerjalan, speed))
            } else null
        }
        MesinTipe.D408 -> {
            val koreksi = mesin?.koreksi
            val jamMin = parseJam(raw)
            if (jamMin != null && koreksi != null) absMinToTimeStr(estAbsD408(jamMin, koreksi, now)) else null
        }
    }
}
