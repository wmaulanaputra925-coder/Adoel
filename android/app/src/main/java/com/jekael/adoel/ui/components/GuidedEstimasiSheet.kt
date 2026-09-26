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

/** A shift is 8 jam — TAPPET/CAM's "sisa waktu" wheel is capped here so it can never dial in a
 * remaining time longer than a whole shift itself, which would fall well outside what an
 * estimasi is actually for. D408's clock-reading wheel doesn't use this (it passes 23, the
 * normal 0-23 clock range) since it isn't a duration at all. */
private const val MAX_ESTIMASI_HOUR = 8

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
    // When present, the wheel opens already at roughly that reading instead of 0:00, so nudging
    // a countdown that's already close to right doesn't mean scrolling all the way up from zero
    // every time.
    existing: Estimasi? = null,
) {
    val colors = LocalAppColors.current
    var activeMesin by remember(mcNo) { mutableStateOf(mesin) }
    val tipe = activeMesin?.tipe ?: MesinTipe.TAPPET
    var needQuickCorakSetup by remember(mcNo) { mutableStateOf(mesin == null || mesin.corak.isBlank() || mesin.corak.trim() == "-") }

    var corakInput by remember(mcNo) { mutableStateOf("") }
    var targetYardInput by remember(mcNo) { mutableStateOf("") }
    var valueInput by remember(mcNo) { mutableStateOf("") }
    // TAPPET/CAM ("sisa waktu") and D408 ("bacaan jam counter") both key off a wheel picker
    // instead of the free-typed field D405 ("yard sudah berjalan") keeps — see
    // MAX_ESTIMASI_HOUR's own doc for why TAPPET/CAM's hour wheel is capped where D408's isn't.
    //
    // Initial position: TAPPET/CAM starts from the estimasi's own current countdown (the exact
    // number already on the radar card), clamped into the wheel's own 0..MAX_ESTIMASI_HOUR jam
    // range (a card can be OVERDUE — negative remaining — or the operator can open this from the
    // console before any countdown exists at all, neither of which is a position on this wheel,
    // so both fall back to 0:00). D408 reconstructs the counter reading its koreksi was computed
    // against: estAbsD408 is `jamKeShiftAbs(counter, now) + koreksi`, and jamKeShiftAbs's own job
    // is just "snap a bare time-of-day reading onto the calendar day nearest now" — so inverting
    // it is exactly the time-of-day component of (estAbsMin − koreksi), no day-snapping needed
    // since that's already baked into estAbsMin. D405 has no wheel at all.
    val initialWheelTotalMin = remember(mcNo, tipe, existing) {
        when {
            existing == null -> 0
            tipe == MesinTipe.TAPPET || tipe == MesinTipe.CAM ->
                existing.effectiveRemaining(nowAbsMin()).toInt().coerceIn(0, MAX_ESTIMASI_HOUR * 60)
            tipe == MesinTipe.D408 -> {
                val koreksi = activeMesin?.koreksi ?: 0.0
                (existing.estAbsMin - koreksi.roundToInt()).mod(1440L).toInt()
            }
            else -> 0
        }
    }
    // wheelTouched guards Simpan the same way `valueInput.isNotBlank()` did for the old text
    // field: a wheel always *has* a value, so without this an operator who opens the sheet and
    // taps Simpan without touching anything would silently submit whatever that starting value
    // means — fine when it's the estimasi's own current reading (that's the whole point of
    // pre-filling it), not fine when it's just 0:00 because nothing existed yet to pre-fill from.
    var wheelHour by remember(mcNo, tipe) { mutableStateOf(initialWheelTotalMin / 60) }
    var wheelMinute by remember(mcNo, tipe) { mutableStateOf(initialWheelTotalMin % 60) }
    var wheelTouched by remember(mcNo, tipe) { mutableStateOf(existing != null) }
    val usesWheel = tipe != MesinTipe.D405
    LaunchedEffect(wheelHour, wheelMinute, tipe) {
        if (usesWheel) valueInput = "$wheelHour.${wheelMinute.toString().padStart(2, '0')}"
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(mcNo, needQuickCorakSetup, usesWheel) {
        // Only D405's plain text field ever attaches this focusRequester (see below) — a wheel
        // picker has nothing to focus, and requesting focus on an unattached FocusRequester
        // throws, not just no-ops.
        if (!needQuickCorakSetup && !usesWheel) {
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
                if (usesWheel) {
                    HourMinuteWheelPicker(
                        hour = wheelHour,
                        minute = wheelMinute,
                        onHourChange = { wheelHour = it; wheelTouched = true },
                        onMinuteChange = { wheelMinute = it; wheelTouched = true },
                        maxHour = if (tipe == MesinTipe.D408) 23 else MAX_ESTIMASI_HOUR,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text("cth: ${hint.example}", color = colors.textFaint) },
                        colors = outlinedFieldColors(),
                        shape = RoundedCornerShape(Dimens.RadiusControl),
                        textStyle = AppType.FieldText.copy(color = colors.textPrimary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        singleLine = true,
                    )
                }
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
                    enabled = if (usesWheel) wheelTouched else valueInput.isNotBlank(),
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
