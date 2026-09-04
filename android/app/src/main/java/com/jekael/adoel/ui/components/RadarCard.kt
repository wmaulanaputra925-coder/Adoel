@file:OptIn(ExperimentalFoundationApi::class)

package com.jekael.adoel.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which swipe direction triggered a doff completion — drives the celebration icon/color/exit
 * direction in RadarCard (see completingKind below). */
private enum class DoffCompletionKind { NORMAL, MATCHING }

/** Which side of the card is showing. FRONT is the normal live card; long-press flips to ACTIONS
 * (Jeda/Hapus). A paused estimate never reaches either of these — RadarCard returns early into
 * [PausedRadarCardFront] before this state is even consulted, so there's no PAUSED face here. */
private enum class CardFace { FRONT, ACTIONS }

private data class UrgencyStyle(
    val accent: Color,
    val barColor: Color,
    val textColor: Color,
    val labelColor: Color,
    val pulse: Boolean,
    val icon: ImageVector?,
    // Tonal elevation, not shadow: how far the card's face tints from bgElevated toward its own
    // accent color climbs with urgency — the flat/minimal "raised = urgent" cue, but as a color mix
    // (no Modifier.shadow RenderNode, so it can never visibly lag a frame behind the card's content
    // the way a shadow can on a freshly-composed LazyColumn item). OVERDUE ignores this and pulses
    // instead (see faceBg in RadarCard below).
    val tintFraction: Float,
)

private fun urgency(remaining: Long): UrgencyStyle = when (urgencyLevel(remaining)) {
    UrgencyLevel.CALM -> UrgencyStyle(Cyan500, Cyan500, Cyan400, Cyan700, false, null, 0f)
    // tintFraction kept modest — Amber600 already drives both this background wash AND the
    // progress bar fill below; stacking a strong wash on top of that read as too dominant/orange
    // for a card that isn't overdue yet (see clr.barColor/clr.accent usage further down).
    UrgencyLevel.SOON -> UrgencyStyle(Amber500, Amber400, Amber400, Amber400, false, Icons.Outlined.Schedule, 0.06f)
    // textColor is Orange400, not Amber — the last-10-minutes countdown needs to read as visibly
    // hotter than Segera's amber at a glance, not just a slightly darker shade of the same color.
    UrgencyLevel.IMMINENT -> UrgencyStyle(Amber600, Amber600, Orange400, Amber500, false, Icons.Outlined.Warning, 0.10f)
    UrgencyLevel.OVERDUE -> UrgencyStyle(Red500, Red500, Red400, Red400, true, Icons.Filled.Warning, 0f)
}

@Composable
fun RadarCard(
    est: Estimasi,
    mesin: MesinData?,
    nowAbs: Long,
    // Swipe right: doffing normal. Swipe left: doffing dengan keterangan "Matching" (cek kualitas
    // kain dari beam baru) — same instant-commit shape as onDoff, just a different keterangan
    // token, so Teks and Terpandu behave identically here (no confirmation sheet either way; the
    // full Ada-keterangan/kendala flow is only reached from the Doffing console's own entry point).
    onDoff: () -> Unit,
    onDoffMatching: () -> Unit,
    // Called instead of animating straight into onDoffMatching when non-null — lets the caller
    // show a confirm dialog first (the "potongan awal 70y" reminder) and only invoke the passed
    // lambda to actually start the slide-out once the operator confirms. Swipe-right (Normal) has
    // no such gate, only Matching does.
    guardDoffMatching: ((() -> Unit) -> Unit)? = null,
    // Hapus moved off the swipe gesture (which now means Matching, not delete) onto long-press —
    // long-press now flips the card to reveal Jeda/Hapus as two explicit buttons instead of
    // hapus firing directly, so an accidental long-press can no longer delete an estimate outright.
    onHapus: () -> Unit,
    onJeda: () -> Unit,
    onLanjutkan: () -> Unit,
    // Tap the mcNo/corak column: edit corak + target yard. Tap the time column (onEditWaktu):
    // edit the estimasi's own time instead — two different fields, two different tap zones,
    // rather than one tap target guessing which the operator meant (Master Blueprint v9.2 §2).
    onQuickEdit: () -> Unit,
    onEditWaktu: () -> Unit,
    modifier: Modifier = Modifier,
    entranceDelayMs: Long = 0L,
    clashingMcNos: List<String> = emptyList(),
    shiftHandover: Boolean = false,
) {
    // Frozen while paused (see Estimasi.pausedAtAbsMin/effectiveRemaining) so a long Jeda doesn't
    // quietly count itself into OVERDUE against wall-clock time.
    val remaining = est.effectiveRemaining(nowAbs)
    val clr = urgency(remaining)
    val totalDur = est.estAbsMin - est.startAbsMin
    val elapsed = nowAbs - est.startAbsMin
    val progress = if (totalDur > 0) (elapsed.toFloat() / totalDur).coerceIn(0f, 1f) else 0f
    val remStr = formatDeltaMin(remaining)
    val corak = est.corakOverride ?: mesin?.corak ?: "—"
    val standardYard = est.yardOverride ?: mesin?.targetYard
    val corakLine = if (standardYard != null) "$corak · ${formatYard(standardYard)}y" else corak
    val tipe = mesin?.tipe?.name ?: "?"
    val showDot = remaining <= 5
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    // Doffing before a machine is actually near due doesn't make sense operationally — swipe (and
    // its screen-reader equivalent) only turns on once the card's within the same lead time as the
    // reminder notification/physical warning light. The topmost card in Menunggu always renders
    // wide regardless of this (see groupMenungguRowsForGrid), so wide-ness alone can no longer be
    // used as a stand-in for "actionable" the way it once could.
    val swipeEnabled = remaining <= REMINDER_LEAD_MIN

    // Only OVERDUE cards actually render the pulse, so only they should pay for it — an
    // unconditional rememberInfiniteTransition here would tick a frame-by-frame animation for
    // every card on screen (CALM/SOON/IMMINENT included) for the entire shift, for no visible effect.
    // Ambient alert breathing, not a micro-interaction — deliberately outside the 150-250ms range
    // (see PingDot's comment above for why a loop this fast would read as flickering, not calm).
    val faceBg = if (clr.pulse) {
        val criticalPulse = rememberInfiniteTransition(label = "criticalPulse")
        val pulseFraction by criticalPulse.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseFraction",
        )
        lerp(colors.bgElevated, colors.criticalPulseTarget, pulseFraction)
    } else {
        lerp(colors.bgElevated, clr.accent, clr.tintFraction)
    }

    // Celebrate completion — card slides out + an icon pops before the state is actually mutated.
    // Normal and Matching get different icon/color/exit-direction so the two feel distinguishable
    // at a glance (Emerald checkmark sliding right vs Sky "verified" badge sliding left). Hapus
    // deliberately does NOT get this treatment: it's gated by a ConfirmDialog (see handleHapusEst
    // in MainScreen.kt), so animating the card away before the user has even confirmed would hide
    // it during the dialog and leave it stuck gone after Batal, since nothing would reset it.
    // Hapus instead relies on the LazyColumn's own Modifier.animateItem() to reflow once the
    // deletion is actually confirmed, plus its own long-press "charge" animation below.
    var completingKind by remember(est.mcNo) { mutableStateOf<DoffCompletionKind?>(null) }
    val completing = completingKind != null
    val scope = rememberCoroutineScope()
    val exitProgress by animateFloatAsState(
        targetValue = if (completing) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "exitProgress",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (completing) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale",
    )
    // One-shot diagonal sweep across the completion tint instead of a generic flash.
    val weaveSweep by animateFloatAsState(
        targetValue = if (completing) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "weaveSweep",
    )
    val completionColor = if (completingKind == DoffCompletionKind.MATCHING) Sky500 else Emerald500
    // Same icon pair as the swipe-in-progress reveal above — one consistent "cut"/"matching" icon
    // vocabulary from the first drag pixel through to the completion pop, not a switch mid-gesture.
    val completionIcon = if (completingKind == DoffCompletionKind.MATCHING) Icons.Outlined.AutoAwesome else Icons.Outlined.ContentCut
    val exitDirection = if (completingKind == DoffCompletionKind.MATCHING) -1f else 1f

    // Long-press to reveal Jeda/Hapus: a "charge up" red tint/scale while held (distinct from the
    // swipe-driven completions above, which slide the card away) so an operator gets feedback
    // during the hold itself, not just a sudden flip. Cancelled/reset if released or dragged before
    // the system long-press timeout fires.
    val pressCharge = remember(est.mcNo) { Animatable(0f) }

    // Which side of the card is showing — isPaused (from the actual persisted estimate) always
    // wins over the local "did the operator long-press to reveal actions" flag, so a card that's
    // genuinely paused shows that face on first composition too (e.g. after a scroll recycles it,
    // or the app restarts), not just right after Jeda is tapped in the same session.
    val isPaused = est.pausedAtAbsMin != null
    var showActionsFace by remember(est.mcNo) { mutableStateOf(false) }
    // Still resets on pause even though the paused branch below returns before `face` is ever
    // read — so that when Lanjutkan later resumes it, the card doesn't come back showing a stale
    // revealed-actions face from whatever state it was in right before Jeda was tapped.
    LaunchedEffect(isPaused) { if (isPaused) showActionsFace = false }
    val face = if (showActionsFace) CardFace.ACTIONS else CardFace.FRONT
    val flipRotation = remember(est.mcNo) { Animatable(0f) }
    LaunchedEffect(face) {
        flipRotation.animateTo(if (face == CardFace.FRONT) 0f else 180f, tween(420, easing = FastOutSlowInEasing))
    }

    // Press-charge + long-press-flip for the outer Box's own pointerInput below (the card's
    // "edge" area — everything the mcNo/corak and waktu zones' own combinedClickable further
    // down don't cover). Those two zones drive the exact same [pressCharge] Animatable off their
    // own interactionSource instead (see [ChargeWhilePressed] and its call sites below) rather
    // than reusing these two lambdas directly — they need to keep combinedClickable for its
    // accessibility semantics (TalkBack's only way to discover onQuickEdit/onEditWaktu), and a
    // raw pointerInput here would have dropped that entirely. Before this, those zones only
    // duplicated the long-press *flip*, not this charge visual/haptic — so pressing-and-holding
    // anywhere on the card's actual content silently skipped the "kartu tenggelam" feedback and
    // only played it in the edge strip outside both zones.
    val handlePressCharge: suspend PressGestureScope.(Offset) -> Unit = {
        coroutineScope {
            val chargeJob = launch { pressCharge.animateTo(1f, tween(450, easing = LinearEasing)) }
            val vibrationJob = if (clr.pulse) {
                launch {
                    while (true) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        delay(200)
                    }
                }
            } else null
            // try/finally ensures interrupted gestures cancel both animation and haptic work.
            try {
                tryAwaitRelease()
            } finally {
                chargeJob.cancel()
                vibrationJob?.cancel()
                scope.launch { pressCharge.animateTo(0f, tween(150)) }
            }
        }
    }
    val handleLongPressFlip: (Offset) -> Unit = {
        scope.launch { pressCharge.snapTo(0f) }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        showActionsFace = true
    }

    val mcNoZoneInteraction = remember(est.mcNo) { MutableInteractionSource() }
    val waktuZoneInteraction = remember(est.mcNo) { MutableInteractionSource() }
    ChargeWhilePressed(mcNoZoneInteraction, pressCharge, clr.pulse, haptic)
    ChargeWhilePressed(waktuZoneInteraction, pressCharge, clr.pulse, haptic)

    // Staggered fade+rise entrance when a batch of cards first appears (e.g. switching into
    // ESTIMASI mode from empty), instead of every card popping in at once — keyed to mcNo so it
    // only plays once per card, not on every recomposition (nowAbs ticks every 5s).
    val entranceAlpha = remember(est.mcNo) { Animatable(0f) }
    val entranceOffsetY = remember(est.mcNo) { Animatable(16f) }
    LaunchedEffect(est.mcNo) {
        delay(entranceDelayMs)
        launch { entranceAlpha.animateTo(1f, tween(220)) }
        entranceOffsetY.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
    }

    // Swipe right = doffing normal, swipe left = doffing dengan keterangan Matching, long-press =
    // hapus — the only ways to act on a card now that the always-visible buttons are gone (see
    // SwipeActionBackground for the swipe reveal panel).
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { Dimens.SwipeThreshold.toPx() }
    val maxSwipePx = with(density) { Dimens.SwipeMax.toPx() }
    val offsetX = remember(est.mcNo) { Animatable(0f) }

    fun triggerDoff(kind: DoffCompletionKind) {
        if (completing) return

        fun startAnim() {
            completingKind = kind
            // Distinct haptic per kind (Master Blueprint §3A/§3B): Normal gets one deep tap — the
            // cutter closing on a taut roll of finished cloth; Matching gets two sharp ones — a
            // scissor snipping a quick quality-check sample.
            when (kind) {
                DoffCompletionKind.NORMAL -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                DoffCompletionKind.MATCHING -> scope.launch {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(90)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            scope.launch {
                // 950ms, matching web's own triggerDoff (RadarCard.tsx) — the card needs to sit on
                // the celebration text long enough to actually read it, not just flash it. Was
                // 420ms, cut short with no text on the celebration at all (see below); that combo
                // read as a glitch, especially for Matching.
                delay(950)
                when (kind) {
                    DoffCompletionKind.NORMAL -> onDoff()
                    DoffCompletionKind.MATCHING -> onDoffMatching()
                }
            }
        }

        if (kind == DoffCompletionKind.MATCHING && guardDoffMatching != null) {
            // Snap the card back to neutral right away instead of optimistically sliding it off —
            // guardDoffMatching may show a confirm dialog, and if the operator cancels there'd be
            // nothing left to undo the slide-out with. startAnim only runs if/when the guard calls
            // proceed(), same pattern as web's RadarCard.tsx.
            scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            guardDoffMatching { startAnim() }
            return
        }
        startAnim()
    }

    fun settleSwipe() {
        val value = offsetX.value
        when {
            value >= swipeThresholdPx -> triggerDoff(DoffCompletionKind.NORMAL) // exitProgress takes over from here
            value <= -swipeThresholdPx -> triggerDoff(DoffCompletionKind.MATCHING)
            else -> scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        }
    }

    // Paused cards get their own simpler front-card treatment instead of ever entering the
    // swipe/flip machinery below — no swipe-to-doff (a paused machine isn't running, doffing it
    // makes no sense), no flip either: Lanjutkan/Hapus sit right on the front. Mirrors web's own
    // early `if (isPaused) return <front-with-inline-actions>` in RadarCard.tsx exactly, rather
    // than the flip-to-a-back-face this used to do (CardPausedFace, now removed).
    if (isPaused) {
        PausedRadarCardFront(
            est = est,
            mesin = mesin,
            remaining = remaining,
            corakLine = corakLine,
            tipe = tipe,
            onQuickEdit = onQuickEdit,
            onLanjutkan = onLanjutkan,
            onHapus = onHapus,
            entranceAlpha = entranceAlpha.value,
            entranceOffsetY = entranceOffsetY.value,
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeActionBackground(
            offsetX = offsetX.value,
            thresholdPx = swipeThresholdPx,
            // Same scissors/sparkle icon pair as ConsoleBar's Doffing button and the completion
            // celebration below — one consistent "cut" vs "matching" icon vocabulary app-wide.
            rightIcon = Icons.Outlined.ContentCut,
            leftIcon = Icons.Outlined.AutoAwesome,
            rightColor = Emerald500,
            leftColor = Sky500,
            rightLabel = "Doffing Normal",
            leftLabel = "Doffing Matching",
            rightDescription = "Target yard selesai",
            leftDescription = "Sampel beam baru · Uji kualitas",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Stretches to match a taller sibling when grid-paired (see MenungguGridSlot),
                // which relies on the outer Box above actually being given that height via
                // Modifier.weight(1f).fillMaxHeight() at the call site — a no-op otherwise.
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = offsetX.value + exitProgress * exitDirection * size.width
                    translationY = entranceOffsetY.value
                    scaleX = 1f - 0.03f * pressCharge.value
                    scaleY = 1f - 0.03f * pressCharge.value
                    alpha = (1f - exitProgress) * entranceAlpha.value
                    rotationY = flipRotation.value
                    // this.density (GraphicsLayerScope's own Float property), not the outer
                    // Density-typed `density` val above — plain `density` here actually resolves
                    // to that outer local, not the receiver, so 8 * density fails to typecheck.
                    cameraDistance = 8 * this.density
                }
                .elevatedListCard(backgroundColor = lerp(faceBg, Red500, 0.16f * pressCharge.value))
                // Swipe is the fast path, but TalkBack intercepts swipe gestures for its own
                // navigation before they ever reach this card — without this, a screen-reader
                // user would have no way at all to doff or delete. These custom actions surface
                // in TalkBack's local context menu as a non-gesture alternative to the swipe above.
                .semantics(mergeDescendants = true) {
                    // TalkBack gets these as direct actions regardless of which face is showing —
                    // it can't perform the flip gesture at all, so it shouldn't need to.
                    customActions = buildList {
                        if (swipeEnabled && face == CardFace.FRONT) {
                            add(CustomAccessibilityAction("Doff mesin ${est.mcNo}") { triggerDoff(DoffCompletionKind.NORMAL); true })
                            add(
                                CustomAccessibilityAction("Doff mesin ${est.mcNo} dengan keterangan Matching") {
                                    triggerDoff(DoffCompletionKind.MATCHING)
                                    true
                                },
                            )
                        }
                        // No isPaused branch here — a paused estimate returns from this composable
                        // entirely before this Box is ever reached (see above), so Jeda/Hapus is
                        // always the right pair of actions by the time TalkBack sees this list.
                        add(CustomAccessibilityAction("Jeda mesin ${est.mcNo}") { onJeda(); true })
                        add(CustomAccessibilityAction("Hapus estimasi Mc ${est.mcNo}") { onHapus(); true })
                    }
                }
                .pointerInput(completing, swipeEnabled, face) {
                    if (completing || !swipeEnabled || face != CardFace.FRONT) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = { settleSwipe() },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-maxSwipePx, maxSwipePx))
                            }
                        },
                    )
                }
                .pointerInput(completing, face) {
                    if (completing || face != CardFace.FRONT) return@pointerInput
                    detectTapGestures(
                        onLongPress = handleLongPressFlip,
                        onPress = handlePressCharge,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
          // Front face stays composed at all times (never conditionally removed) so its own
          // intrinsic content — mcNo/corak/progress row — is what fixes this card's height,
          // regardless of which face is actually showing. The back face below then matches that
          // exact footprint via matchParentSize() instead of the card resizing itself to
          // whichever face happens to be composed (that was leaving Jeda/Hapus/Lanjutkan
          // floating in a card several times taller than a normal one). Content swaps at the
          // halfway point of the flip (not tied to [face] directly) so the card visually reads as
          // turning over, not just cross-fading — see the un-mirror Box further down for why the
          // back content needs its own counter-rotation. Hidden via alpha (not removed) and its
          // own tap zones disabled then too, so an invisible front can't still swallow taps meant
          // for the back's buttons.
          val frontVisible = flipRotation.value <= 90f
          Box(modifier = Modifier.graphicsLayer { alpha = if (frontVisible) 1f else 0f }) {
            // Left accent — reverted back to a flush flat strip (the pre-redesign look) instead
            // of the inset "twisted thread" capsule this had grown into: no independent clip/
            // rounding of its own here, so its top/bottom corners aren't hand-matched to the
            // card's curve — they're just cropped by it for free, since the outer elevatedListCard
            // above already clips everything to RoundedCornerShape(Dimens.RadiusCard).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(clr.accent),
            )

            // Content — swipe right = doff, swipe left = doff+Matching, long-press = hapus.
            // Symmetric padding now that the accent is back to a flush 4dp strip (no more wide
            // pill inset to clear on the start side).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.Space16, end = Dimens.Space16, top = Dimens.Space16, bottom = Dimens.Space16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: mc number + type + corak — tappable on its own (separate from the
                // swipe-the-whole-card drag above) as a fast path to correct corak/target yard,
                // the two fields that actually change often on the floor, without leaving Radar
                // for the full Pengaturan > Mesin flow. Also carries its own long-press-to-flip
                // (mirroring the outer Box's) since this zone's own tap handling would otherwise
                // swallow the touch before the outer Box's long-press detector ever saw it,
                // shrinking the effective press-and-hold area down to a thin strip around the
                // split zones instead of covering the whole card. Its own interactionSource feeds
                // [ChargeWhilePressed] above so holding here charges the same as the edge does.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            interactionSource = mcNoZoneInteraction,
                            indication = LocalIndication.current,
                            enabled = frontVisible,
                            onClickLabel = "Ubah corak dan target yard Mc ${est.mcNo}",
                            onClick = onQuickEdit,
                            onLongClickLabel = "Jeda atau hapus Mc ${est.mcNo}",
                            onLongClick = { handleLongPressFlip(Offset.Zero) },
                        ),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                            // Lets this cluster shrink instead of pushing the trailing badges (or
                            // this whole row) wider than the card — see RadarStatusBar's own
                            // weight(fill=false) fix for the same squeeze-vs-grow issue.
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = est.mcNo,
                                // A 3-digit mcNo at the 2-digit size wraps mid-number in a half-width
                                // grid card (e.g. "104" breaking into "10"/"4") — shrink it instead of
                                // letting it wrap, since maxLines=1 alone would just clip a digit.
                                style = TextStyle(
                                    fontSize = if (est.mcNo.length >= 3) 30.sp else 40.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-2).sp,
                                    color = colors.textPrimary,
                                ),
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (mesin != null) {
                                MesinTipeIcon(
                                    tipe = mesin.tipe,
                                    tint = mesinTipeColor(mesin.tipe),
                                    modifier = Modifier.size(12.dp).padding(bottom = Dimens.Space4),
                                )
                            }
                            Text(
                                text = tipe,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    // Same per-type color as the icon right before it (Tappet/Cam/D405/
                                    // D408 each have their own), not the urgency color — the machine
                                    // type is its own identity, independent of how close the doff is.
                                    color = mesin?.tipe?.let { mesinTipeColor(it) } ?: colors.textFaint,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = Dimens.Space4),
                            )
                            if (clr.icon != null) {
                                // 15dp, not the 12dp everything else in this row uses — Material's
                                // Schedule/Warning outlines carry more internal linework than web's
                                // minimalist 2-stroke SVGs, so at 12dp they read as a smudge instead
                                // of a recognizable glyph. Bumped up rather than swapped to a filled
                                // variant, since Outlined vs Filled here is deliberately meaningful
                                // (OVERDUE alone is filled — see the `urgency()` levels above).
                                Icon(
                                    imageVector = clr.icon,
                                    contentDescription = null,
                                    tint = clr.labelColor,
                                    modifier = Modifier.size(15.dp).padding(bottom = Dimens.Space4),
                                )
                            }
                        }
                        // Bentrok/OPERAN SHIFT sit in this same title row (not their own stacked
                        // rows below) so a card carrying either doesn't grow taller than one
                        // without it — matches web's single nowrap .radar-card-title-row, where
                        // the shift badge's margin-left:auto is mirrored here by the weight(1f)
                        // spacer right before it.
                        if (clashingMcNos.isNotEmpty()) {
                            RadarCardBadge(
                                icon = Icons.Filled.Warning,
                                text = "Bentrok Mc ${clashingMcNos.joinToString(", ")}",
                                accent = Amber400,
                                modifier = Modifier.padding(bottom = Dimens.Space4),
                            )
                        }
                        if (shiftHandover) {
                            Spacer(Modifier.weight(1f))
                            RadarCardBadge(
                                icon = Icons.Outlined.SwapHoriz,
                                text = "OPERAN SHIFT",
                                accent = Orange400,
                                modifier = Modifier.padding(bottom = Dimens.Space4),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                        // Small standard Material icon marking "this line is about the fabric
                        // itself" (corak/yard) — not a fabric illustration, just a modest visual
                        // anchor next to the one line of text that's actually about the kain,
                        // as opposed to the machine/timing info the rest of the card shows.
                        Icon(
                            imageVector = Icons.Outlined.Texture,
                            contentDescription = null,
                            tint = colors.textFaint,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = corakLine,
                            style = AppType.LabelBold.copy(color = colors.textMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressBar(
                        fraction = progress,
                        trackColor = colors.bgElevated2,
                        fillColor = clr.barColor,
                        modifier = Modifier.fillMaxWidth(),
                        fillMaxWidth = true,
                        height = 3.dp,
                    )
                }

                // Right: ping dot + estimated time + remaining
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showDot) {
                        PingDot(color = if (remaining < 0) Red500 else Emerald500)
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        // Same long-press-to-flip as the mcNo/corak zone above — see that
                        // comment for why this zone needs its own copy instead of relying on the
                        // outer Box's detector, and its own interactionSource for the same
                        // press-charge reason too.
                        modifier = Modifier.combinedClickable(
                            interactionSource = waktuZoneInteraction,
                            indication = LocalIndication.current,
                            enabled = frontVisible,
                            onClickLabel = "Ubah waktu estimasi Mc ${est.mcNo}",
                            onClick = onEditWaktu,
                            onLongClickLabel = "Jeda atau hapus Mc ${est.mcNo}",
                            onLongClick = { handleLongPressFlip(Offset.Zero) },
                        ),
                    ) {
                        Text(
                            text = absMinToTimeStr(est.estAbsMin),
                            style = TextStyle(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                                fontFamily = FontFamily.Monospace,
                                color = clr.textColor,
                            ),
                        )
                        Text(
                            text = remStr,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = if (remaining < 0) Red400 else clr.textColor,
                            ),
                        )
                    }
                }
            }
          }

          // Back of the card — only composed once the flip has passed the halfway point, sized via
          // matchParentSize() to exactly match the front's footprint above (see that Box's own
          // comment for why), and pre-rotated another 180° so it reads right-side-up once the outer
          // graphicsLayer has turned all the way over, instead of mirrored.
          if (!frontVisible) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                when (face) {
                    CardFace.ACTIONS -> CardActionsFace(
                        mcNo = est.mcNo,
                        onJeda = onJeda,
                        onHapus = onHapus,
                        onDismiss = { showActionsFace = false },
                    )
                    CardFace.FRONT -> Unit // unreachable — flipRotation only passes 90° once face != FRONT
                }
            }
          }
        }

        // Celebrate completion — a sibling of the card Box above, NOT a child of it: that Box's
        // own graphicsLayer fades its alpha to 0 as exitProgress climbs (see translationX/alpha
        // above), which was silently fading this celebration out in lockstep with the split still
        // trying to sweep in — the reveal and the icon pop never really got a chance to be seen
        // before both vanished together. Living outside that graphicsLayer, this stays at full
        // opacity for its own duration regardless of how the card underneath is fading/sliding away.
        // The card visibly "splits" as the tint sweeps in behind a clipped boundary (straight
        // diagonal for Normal, a jagged swatch-clip edge for Matching — Master Blueprint §3A/§3B),
        // then an icon+title/subtitle pill pops in on top (web's .radar-card-celebrate-content).
        // Which shape, icon, and copy depends on completingKind.
        if (checkScale > 0f) {
            val isMatching = completingKind == DoffCompletionKind.MATCHING
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(Dimens.RadiusCard))
                        .drawWithContent {
                            drawContent()
                            val splitPath = if (isMatching) {
                                jaggedSplitPath(size, weaveSweep)
                            } else {
                                diagonalSplitPath(size, weaveSweep)
                            }
                            clipPath(splitPath) {
                                drawRect(completionColor.copy(alpha = 0.30f))
                            }
                            // Bright flash racing along the split boundary as it sweeps across.
                            rotate(if (isMatching) -12f else 20f) {
                                val bandWidth = size.width * 0.18f
                                val travel = size.width * 1.6f
                                val x = -bandWidth + weaveSweep * travel
                                drawRect(
                                    color = Color.White.copy(alpha = 0.5f * (1f - weaveSweep)),
                                    topLeft = Offset(x, -size.height),
                                    size = Size(bandWidth, size.height * 3f),
                                )
                            }
                        },
                )
                // Icon + title/subtitle pill — Android equivalent of web's .radar-card-celebrate-
                // content, which was missing here entirely (a bare icon with no text is what read
                // as "not displayed correctly" for Matching: nothing on screen actually said
                // Matching, just an unlabeled icon indistinguishable at a glance from Normal's).
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkScale.coerceIn(0f, 1f)
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.26f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = completionIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isMatching) "Doffing Matching (Sampel)" else "Doffing Normal",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White),
                        )
                        Text(
                            text = if (isMatching) "Tercatat untuk cek kualitas beam" else "Tercatat selesai target yard",
                            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.94f)),
                        )
                    }
                }
            }
        }
    }
}

/** Back-of-card face revealed by a long-press (Master Blueprint v9.2 "Jeda/Hapus") — two explicit
 * buttons instead of hapus firing directly off the hold itself, so an accidental long-press can no
 * longer delete an estimate outright. Tapping anywhere else on this face (the padding around the
 * buttons) dismisses back to the front, same as tapping either button's own indication would just
 * without picking one — a discoverable "never mind" for an accidental flip. */
@Composable
private fun CardActionsFace(
    mcNo: String,
    onJeda: () -> Unit,
    onHapus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
            Text(
                "Opsi Mesin $mcNo",
                style = AppType.LabelBold.copy(color = colors.textMuted),
            )
            Row(
                modifier = Modifier.padding(horizontal = Dimens.Space16),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CardActionChip(icon = Icons.Outlined.Pause, label = "Jeda Mesin", accent = Amber400, onClick = onJeda, modifier = Modifier.weight(1f))
                CardActionChip(icon = Icons.Outlined.Delete, label = "Hapus Estimasi", accent = Red400, onClick = onHapus, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Front-and-only card for an Mc that's Jeda'd — no swipe, no flip, Lanjutkan/Hapus sit right on
 * the card. Direct port of web's early `if (isPaused) return <front with inline actions>` in
 * RadarCard.tsx (`.radar-card-front.radar-card-paused`), replacing the flip-to-back-face this
 * used to do (the old CardPausedFace, reachable only via long-press, is gone). */
@Composable
private fun PausedRadarCardFront(
    est: Estimasi,
    mesin: MesinData?,
    remaining: Long,
    corakLine: String,
    tipe: String,
    onQuickEdit: () -> Unit,
    onLanjutkan: () -> Unit,
    onHapus: () -> Unit,
    entranceAlpha: Float,
    entranceOffsetY: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceAlpha
                translationY = entranceOffsetY
            }
            .elevatedListCard(
                backgroundColor = lerp(colors.bgElevated, Amber500, 0.08f),
                borderColor = Amber500.copy(alpha = 0.30f),
            )
            .drawBehind {
                // Faint diagonal stripe wash, same idea as the old CardPausedFace's — much
                // subtler here (0.035 vs 0.08) since it now sits behind live text/badges instead
                // of an otherwise-empty back face.
                val stripe = Amber500.copy(alpha = 0.035f)
                for (x in -size.height.toInt()..size.width.toInt() step 18) {
                    drawLine(stripe, Offset(x.toFloat(), 0f), Offset(x + size.height, size.height), strokeWidth = 2f)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(6.dp)
                .background(Amber500),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.Space16 + 6.dp, end = Dimens.Space16, top = Dimens.Space12, bottom = Dimens.Space12),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(onClickLabel = "Ubah corak dan target yard Mc ${est.mcNo}", onClick = onQuickEdit),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space6)) {
                    Text(
                        text = est.mcNo,
                        style = TextStyle(
                            fontSize = if (est.mcNo.length >= 3) 23.sp else 27.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (mesin != null) {
                        MesinTipeIcon(tipe = mesin.tipe, tint = mesinTipeColor(mesin.tipe), modifier = Modifier.size(12.dp))
                    }
                    Text(
                        text = tipe,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = mesin?.tipe?.let { mesinTipeColor(it) } ?: colors.textFaint,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RadarCardBadge(icon = Icons.Outlined.Pause, text = "DIJEDA", accent = Amber400)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                    Icon(imageVector = Icons.Outlined.Texture, contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(11.dp))
                    Text(
                        text = corakLine,
                        style = AppType.Caption.copy(color = colors.textMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dibekukan pada sisa:", style = AppType.Caption.copy(color = colors.textMuted))
                    Text(
                        formatDeltaMin(remaining),
                        style = AppType.Caption.copy(color = Amber400, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ResumeChip(onClick = onLanjutkan)
                DeleteIconButton(onClick = onHapus)
            }
        }
    }
}

/** Small icon+label chip for a title-row badge (Bentrok/OPERAN SHIFT) — Android equivalent of
 * web's .radar-clash-badge/.shift-badge (RadarCard.tsx), a real vector icon instead of a raw
 * emoji glyph so it tints with [accent] and matches the rest of the icon system. */
@Composable
private fun RadarCardBadge(icon: ImageVector, text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(10.dp))
        Text(
            text = text,
            style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = accent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Wide icon+label pill for a Jeda/Hapus choice on [CardActionsFace] — Android equivalent of
 * web's .radar-action-chip (RadarCard.tsx), so the "Opsi Mesin" quick menu reads the same on
 * both platforms instead of Android's own smaller circle-with-label-below treatment. */
@Composable
private fun CardActionChip(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // No haptic here — [onClick] (onJeda/onHapus) already triggers it in MainScreenHandlers, the
    // single source both this tap and RadarCard's TalkBack custom actions funnel through (see
    // triggerDoff for the same pattern), so adding one here too would double-buzz.
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(label, style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = accent))
    }
}

/** Wide primary "Lanjutkan" pill on [PausedRadarCardFront] — Android equivalent of web's
 * .radar-resume-btn. */
@Composable
private fun ResumeChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Emerald500)
            .clickable(onClickLabel = "Lanjutkan", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text("Lanjutkan", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White))
    }
}

/** Small icon-only delete button next to [ResumeChip] — Android equivalent of web's
 * .radar-paused-delete-icon-btn, de-emphasized since resuming is the expected action here, not
 * deleting. */
@Composable
private fun DeleteIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Red500.copy(alpha = 0.12f))
            .border(1.dp, Red500.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
            .clickable(onClickLabel = "Hapus", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = Red400, modifier = Modifier.size(16.dp))
    }
}

/** Straight diagonal boundary sweeping left-to-right as [progress] goes 0→1 — everything left of
 * the boundary is inside the returned path (see [clipPath] call site). Slanted like a single
 * cutter pass through taut cloth, for the routine "Potong Normal" completion. */
private fun diagonalSplitPath(size: Size, progress: Float): Path {
    val slant = size.height * 0.55f
    val edgeX = size.width * progress
    return Path().apply {
        moveTo(0f, 0f)
        lineTo(edgeX, 0f)
        lineTo(edgeX - slant, size.height)
        lineTo(0f, size.height)
        close()
    }
}

/** Zigzag boundary sweeping left-to-right as [progress] goes 0→1 — a "swatch clip" sampling snip
 * rather than a clean blade pass, for the "Potong Matching" quality-check completion. */
private fun jaggedSplitPath(size: Size, progress: Float): Path {
    val edgeX = size.width * progress
    val teeth = 8
    val toothH = size.height / teeth
    val toothDepth = size.minDimension * 0.03f
    return Path().apply {
        moveTo(0f, 0f)
        lineTo(edgeX, 0f)
        for (i in 1..teeth) {
            val y = (toothH * i).coerceAtMost(size.height)
            val x = edgeX + (if (i % 2 == 0) toothDepth else -toothDepth)
            lineTo(x, y)
        }
        lineTo(0f, size.height)
        close()
    }
}

/** Drives the card's shared [pressCharge] Animatable off [interactionSource]'s own pressed state
 * instead of a dedicated pointerInput — used by the mcNo/corak and waktu zones, which each need
 * to keep combinedClickable for its accessibility semantics (see their call sites), so they can't
 * reuse the outer Box's raw detectTapGestures the way the card's edge area does. Charges up over
 * the same 450ms the edge area uses while held, including the same OVERDUE rhythmic haptic. */
@Composable
private fun ChargeWhilePressed(
    interactionSource: MutableInteractionSource,
    pressCharge: Animatable<Float, AnimationVector1D>,
    pulseHaptic: Boolean,
    haptic: HapticFeedback,
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) {
        if (isPressed) {
            val vibrationJob = if (pulseHaptic) {
                launch {
                    while (true) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        delay(200)
                    }
                }
            } else null
            pressCharge.animateTo(1f, tween(450, easing = LinearEasing))
            vibrationJob?.cancel()
        } else {
            pressCharge.animateTo(0f, tween(150))
        }
    }
}

// Continuous ambient indicator, not a one-shot micro-interaction — the 150-250ms range (see
// Motion.kt) is for animations that respond to something happening; a breathing/ping loop that
// fast would read as flickering rather than "halus", so it's intentionally left outside that range.
@Composable
private fun PingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "ping")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "pingScale",
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "pingAlpha",
    )
    Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = pingAlpha }
                .clip(CircleShape)
                .background(color),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
