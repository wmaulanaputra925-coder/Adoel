package com.jekael.adoel.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jekael.adoel.data.*
import com.jekael.adoel.notification.NotificationHelper
import com.jekael.adoel.ui.components.*
import com.jekael.adoel.ui.theme.*
import com.jekael.adoel.viewmodel.DoffViewModel
import com.jekael.adoel.viewmodel.UIViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    doffVm: DoffViewModel,
    uiVm: UIViewModel,
) {
    // DoffViewModel's _state starts out holding a 174-unconfigured-machine placeholder until the
    // real persisted data finishes loading from disk — bail out to a brief loading screen instead
    // of letting that placeholder data render for a frame or two on a slow cold start.
    val isLoaded by doffVm.isLoaded.collectAsStateWithLifecycle()
    if (!isLoaded) {
        LoadingPlaceholder()
        return
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = LocalAppColors.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by doffVm.state.collectAsStateWithLifecycle()
    val toast by uiVm.toast.collectAsStateWithLifecycle()
    val confirm by uiVm.confirm.collectAsStateWithLifecycle()

    // Which top-level list is on screen — a pure view switcher now, decoupled from the console
    // (see MainScreenHeader's page tab row). Saveable so a rotation or process death doesn't drop
    // which page the operator was looking at.
    var page by rememberSaveable { mutableStateOf(Page.RADAR) }
    var radarFilter by rememberSaveable { mutableStateOf("") }
    var doffFilter by rememberSaveable { mutableStateOf("") }

    val sendPulse = remember { SendPulseState() }
    LaunchedEffect(sendPulse.key) {
        if (sendPulse.key == 0) return@LaunchedEffect
        sendPulse.showCheck = true
        sendPulse.scale.snapTo(0.8f)
        launch {
            sendPulse.scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        delay(500)
        sendPulse.showCheck = false
    }

    // Error feedback on a rejected console command — a toast alone (3.5s, auto-dismiss) can be
    // missed if the operator looks back at the machine right after hitting send, so this pairs it
    // with a haptic + a brief red ring around the input that doesn't depend on eyes staying on screen.
    val errorFlash = remember { ErrorFlashState() }
    LaunchedEffect(errorFlash.key) {
        if (errorFlash.key == 0) return@LaunchedEffect
        errorFlash.active = true
        delay(200)
        errorFlash.active = false
    }

    // "Selesai Shift" closes out a full work shift — worth a beat more than a toast that's gone
    // in 3.5s, so this pops a big checkmark over a dimmed backdrop before fading on its own.
    val shiftFinished = remember { ShiftFinishedState() }
    LaunchedEffect(shiftFinished.key) {
        if (shiftFinished.key == 0) return@LaunchedEffect
        shiftFinished.visible = true
        shiftFinished.backdropAlpha.snapTo(0f)
        shiftFinished.checkScale.snapTo(0f)
        launch { shiftFinished.backdropAlpha.animateTo(1f, tween(150)) }
        shiftFinished.checkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        delay(700)
        shiftFinished.backdropAlpha.animateTo(0f, tween(250))
        shiftFinished.visible = false
    }

    var nowAbs by remember { mutableLongStateOf(nowAbsMin()) }
    val permission = remember { PermissionState() }

    var activeOverlay by rememberSaveable(stateSaver = ActiveOverlaySaver) { mutableStateOf<ActiveOverlay>(ActiveOverlay.None) }
    var syncOpen by rememberSaveable { mutableStateOf(false) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }

    var consoleBarHeight by remember { mutableStateOf(0.dp) }
    var headerHeight by remember { mutableStateOf(0.dp) }

    val undoRedo = remember { UndoRedoState() }
    val handlers = remember(context, doffVm, uiVm, haptic) {
        MainScreenHandlers(context, doffVm, uiVm, haptic, sendPulse, errorFlash, shiftFinished, undoRedo)
    }

    // Request notification permission launcher
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permission.notifGranted = granted
        if (!granted) uiVm.showToast("⚠ Izin notifikasi ditolak")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= 33) {
                    val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    permission.notifGranted = nm.areNotificationsEnabled()
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    val am = context.getSystemService(AlarmManager::class.java)
                    permission.exactAlarmGranted = am.canScheduleExactAlarms()
                }
                val powerManager = context.getSystemService(PowerManager::class.java)
                permission.batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            nowAbs = nowAbsMin()
        }
    }

    val radarList = remember(state.estimasi) {
        sortedByNearest(state.estimasi)
    }
    // Filters by mc number only so an operator can jump straight to a machine instead of
    // scanning past everything else when a lot of machines are running at once.
    val filteredRadarList = remember(radarList, radarFilter) {
        if (radarFilter.isBlank()) {
            radarList
        } else {
            radarList.filter { est -> est.mcNo.contains(radarFilter, ignoreCase = true) }
        }
    }
    // Derived (not a plain remember(nowAbs, ...)) — partitioning only needs to actually re-propagate
    // to whatever reads segeraList/menungguList when a card crosses the Segera/Menunggu boundary,
    // not on every 5-second nowAbs tick that leaves the partition unchanged.
    val (segeraList, menungguList) = remember(filteredRadarList) {
        derivedStateOf { partitionSegeraMenunggu(filteredRadarList, nowAbs) }
    }.value
    val hasPreviousShiftData = remember(state.aktual, state.estimasi, nowAbs) {
        val shiftStart = currentShiftStartAbsMin(nowAbs)
        state.aktual.any { it.tsEpochMin?.let { ts -> ts < shiftStart } == true } ||
            state.estimasi.values.any { it.startAbsMin < shiftStart }
    }
    // Menunggu bucket spans CALM through IMMINENT (Segera already claims OVERDUE) — tint the
    // band header by its most urgent member so it doesn't read "calm" while cards inside are
    // already amber/orange. Derived so this only re-propagates when the accent color itself changes.
    val menungguAccent by remember(menungguList) {
        derivedStateOf {
            when (menungguList.maxOfOrNull { urgencyLevel(it.effectiveRemaining(nowAbs)) }) {
                UrgencyLevel.IMMINENT -> Orange400
                UrgencyLevel.SOON -> Amber400
                else -> Cyan400
            }
        }
    }
    // Anchor for the leading break (see below) — frozen at the nowAbs value from the moment this
    // became the nearest upcoming machine with nothing overdue, not recomputed every tick. Keying
    // remember on (noSegera, mcNo) instead of nowAbs is what makes it "stick": as long as the same
    // machine stays the nearest one, this keeps returning the nowAbs it captured the first time,
    // so the gap's total duration stays fixed while only the live remaining time (read straight
    // from nowAbs in BreakGapCard) shrinks — otherwise both numbers shrink in lockstep and the
    // progress bar reads permanently empty (elapsedFraction stuck at ~0).
    val noSegera = segeraList.isEmpty()
    val firstMenungguMcNo = menungguList.firstOrNull()?.mcNo
    val leadingGapAnchor = remember(noSegera, firstMenungguMcNo) { nowAbs }

    // Flag long idle stretches between two upcoming doffs so the operator knows when it's
    // actually safe to step away, instead of having to eyeball the gap between two times.
    val menungguRows = remember(menungguList, segeraList, leadingGapAnchor) {
        buildList {
            // Leading break: a gap card used to anchor to whichever machine sat just before it
            // in the list — doffing/menghapus that machine removed it from menungguList, which
            // silently swallowed the card even though the free time itself hadn't gone anywhere
            // (it's just measured from leadingGapAnchor instead of from that machine's estimate).
            // Only valid when nothing is overdue (Segera empty) — an operator with something
            // already due doesn't have free time to call out yet.
            val first = menungguList.firstOrNull()
            if (noSegera && first != null) {
                val gap = first.estAbsMin - leadingGapAnchor
                if (gap >= BREAK_GAP_THRESHOLD_MIN) {
                    add(MenungguRow.GapRow(afterMcNo = "now", nextMcNo = first.mcNo, gapMin = gap, nextAbsMin = first.estAbsMin))
                }
            }
            menungguList.forEachIndexed { index, est ->
                add(MenungguRow.CardRow(est))
                val next = menungguList.getOrNull(index + 1)
                if (next != null) {
                    val gap = next.estAbsMin - est.estAbsMin
                    if (gap >= BREAK_GAP_THRESHOLD_MIN) {
                        add(MenungguRow.GapRow(afterMcNo = est.mcNo, nextMcNo = next.mcNo, gapMin = gap, nextAbsMin = next.estAbsMin))
                    }
                }
            }
        }
    }

    val doffCount = state.aktual.size
    val totalMc = remember(state.estimasi, state.aktual, nowAbs) {
        val shiftStart = currentShiftStartAbsMin(nowAbs)
        val shiftEnd = shiftStart + 480L
        val activeEstimasi = state.estimasi.values
            .filter { it.estAbsMin in shiftStart until shiftEnd }
            .map { it.mcNo }
        (activeEstimasi + state.aktual.map { it.mcNo }).toSet().size
    }
    val aktualReversed = remember(state.aktual) { sortAktualChronological(state.aktual, nowAbsMin()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .systemBarsPadding()
            // Tapping any empty area (list gaps, header, background — anywhere a descendant
            // doesn't already consume the tap for its own click) dismisses the keyboard and
            // drops focus from whatever field was open. Without this, ConsoleBar's mcNo field
            // stayed "ready to type" indefinitely once tapped — the keyboard wouldn't go away
            // until something explicitly submitted or navigated elsewhere, and could resurface
            // on the next app open since the field never actually lost focus.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Page body — Radar/Riwayat switch with a "shedding motion" transition (Master
            // Blueprint §3D): the outgoing and incoming page slide vertically in opposite
            // directions at once, echoing the loom's heddle frames splitting the warp shed. Each
            // page keeps its own LazyColumn (and so its own scroll position) rather than sharing
            // one list whose items change wholesale.
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { h -> dir * h } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { h -> -dir * h } + fadeOut(tween(160)))
                },
                label = "pageShedding",
            ) { p ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = 10.dp + headerHeight + 16.dp,
                        bottom = 10.dp + consoleBarHeight + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
                ) {
                    permissionBanners(
                        notifGranted = permission.notifGranted,
                        exactAlarmGranted = permission.exactAlarmGranted,
                        batteryUnrestricted = permission.batteryUnrestricted,
                        onNotifBannerClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onExactAlarmBannerClick = {
                            if (Build.VERSION.SDK_INT >= 31) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        onBatteryBannerClick = {
                            // On some OEM skins (e.g. OriginOS), tapping "Tetapkan sekarang" on
                            // the system dialog doesn't grant the exemption directly — it drops
                            // the user onto an app-battery-usage page where the real toggle is
                            // one tap deeper and defaults back to "Dioptimalkan". Walk the user
                            // through it explicitly instead of assuming the dialog alone works.
                            uiVm.showConfirm(
                                "Supaya notifikasi tidak telat, ikuti langkah ini (cukup sekali saja):\n\n" +
                                    "1. Pada dialog berikutnya, ketuk \"Tetapkan sekarang\".\n" +
                                    "2. Di halaman \"Penggunaan baterai aplikasi\", KETUK baris \"Izinkan penggunaan latar belakang\" (walau kelihatan sudah aktif).\n" +
                                    "3. Pilih \"Tidak dibatasi\" (bukan \"Dioptimalkan\").",
                            ) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        },
                    )

                    when (p) {
                        Page.RADAR -> {
                            estimasiSection(
                                radarList = radarList,
                                segeraList = segeraList,
                                menungguList = menungguList,
                                menungguRows = menungguRows,
                                menungguAccent = menungguAccent,
                                db = state.db,
                                nowAbs = nowAbs,
                                radarFilter = radarFilter,
                                onRadarFilterChange = { radarFilter = it },
                                onDoff = { mcNo -> handlers.handleDoff(mcNo) },
                                onDoffMatching = { mcNo -> handlers.handleDoff(mcNo, "MATCHING") },
                                onHapus = { mcNo -> handlers.handleHapusEst(mcNo) },
                                onJeda = { mcNo -> handlers.handleJeda(mcNo) },
                                onLanjutkan = { mcNo -> handlers.handleLanjutkan(mcNo) },
                                // Two distinct tap zones (Master Blueprint v9.2 §2): mcNo/corak
                                // column edits corak+target yard, the time column edits the
                                // estimasi's own time — no more one tap target for two fields.
                                onQuickEdit = { mcNo -> activeOverlay = ActiveOverlay.QuickEditMesin(mcNo) },
                                onEditWaktu = { mcNo -> activeOverlay = ActiveOverlay.GuidedEstimasi(mcNo) },
                            )
                        }
                        Page.RIWAYAT -> {
                            doffingSection(
                                state = state,
                                aktualReversed = aktualReversed,
                                doffFilter = doffFilter,
                                onDoffFilterChange = { doffFilter = it },
                                onEntryClick = { id -> activeOverlay = ActiveOverlay.EditAkt(id) },
                                onHapusEntry = { id -> handlers.handleHapusAktual(id) { activeOverlay = ActiveOverlay.None } },
                            )
                        }
                    }
                }
            }
        }

        // Top/bottom fade — softens the hard edge where list items scroll behind the floating
        // header/console instead of cutting off sharply (Master Blueprint v9.2 §10).
        EdgeFadeScrim(atTop = true, height = 10.dp + headerHeight + 16.dp)
        EdgeFadeScrim(atTop = false, height = 10.dp + consoleBarHeight + 16.dp)

        // Header — floating card, overlays the list (list scrolls behind it)
        MainScreenHeader(
            nowAbs = nowAbs,
            totalMc = totalMc,
            doffCount = doffCount,
            showRemaining = showRemaining,
            onToggleShowRemaining = { showRemaining = !showRemaining },
            onDaftarMesin = { activeOverlay = ActiveOverlay.Mesin },
            onGearClick = { activeOverlay = ActiveOverlay.Settings },
            onSyncClick = { syncOpen = true },
            onShare = { shareHistory(context, state) },
            onFinishShift = { handlers.handleFinishShift() },
            showFinishShift = hasPreviousShiftData,
            onStatistik = { activeOverlay = ActiveOverlay.Statistik },
            page = page,
            onPageSelect = { page = it },
            onHeightMeasured = { headerHeight = it },
            haptic = haptic,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        )

        // Console command bar — floating card, overlays the list (list scrolls behind it). Pure
        // machine-number entry point: the operator picks the action by which icon they tap
        // (Estimasi or Doffing, Master Blueprint v9.2 §1) instead of a single button that then
        // asks which one was meant.
        ConsoleBar(
            onEstimasiClick = { mcNo ->
                if (state.db[mcNo] == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    uiVm.showToast("⚠ Mc $mcNo tidak ditemukan")
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // GuidedEstimasiSheet itself detects an unconfigured machine (blank corak) and
                    // offers the quick corak/yard setup inline before the value step (§3) — no
                    // separate routing needed here for that case.
                    activeOverlay = ActiveOverlay.GuidedEstimasi(mcNo)
                }
            },
            onDoffingClick = { mcNo ->
                if (state.db[mcNo] == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    uiVm.showToast("⚠ Mc $mcNo tidak ditemukan")
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    activeOverlay = ActiveOverlay.GuidedDoffing(mcNo)
                }
            },
            onUndo = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                undoRedo.undo()
            },
            onRedo = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                undoRedo.redo()
            },
            canUndo = undoRedo.canUndo,
            canRedo = undoRedo.canRedo,
            onHeightMeasured = { consoleBarHeight = it },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )

        // Toast — floats just below the floating header. Anchored to the top (not the console
        // bar at the bottom) so the on-screen keyboard, which only ever covers the bottom of the
        // screen while typing a command, can never hide it right when it matters most.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = headerHeight + Dimens.Space8),
        ) {
            ToastHost(toast = toast, onDismiss = { uiVm.dismissToast() })
        }

        // Daftar Mesin / Pengaturan panels — rendered in this same Box (not a separate Dialog
        // window) so its own AnimatedVisibility is the only thing animating it in/out, drawn
        // last to sit on top. Two separate screens (not tabs of one drawer) matching the web
        // app's own separate "Daftar Mesin" and "Pengaturan" menu items.
        if (activeOverlay is ActiveOverlay.Mesin) {
            MesinDrawer(
                state = state,
                onClose = { activeOverlay = ActiveOverlay.None },
                onSetMesin = { mcNo, data -> doffVm.setMesin(mcNo, data) },
                onResetMesin = { mcNo -> doffVm.resetMesin(mcNo) },
                onAddCorakShortcut = { sc -> doffVm.addCorakShortcut(sc) },
                showToast = { uiVm.showToast(it) },
                showConfirm = { msg, fn -> uiVm.showConfirm(msg, onConfirm = fn) },
            )
        }

        if (activeOverlay is ActiveOverlay.Settings) {
            PengaturanDrawer(
                state = state,
                onClose = { activeOverlay = ActiveOverlay.None },
                onResetDb = {
                    NotificationHelper.cancelAll(context, state.estimasi.keys.toList())
                    doffVm.resetDb()
                },
                onSetThemeMode = { mode -> doffVm.setThemeMode(mode.name) },
                onExportJson = { doffVm.exportJson() },
                onAddKeteranganShortcut = { sc -> doffVm.addKeteranganShortcut(sc) },
                onRemoveKeteranganShortcut = { sc -> doffVm.removeKeteranganShortcut(sc) },
                onResetKeteranganShortcuts = { doffVm.resetKeteranganShortcuts() },
                onAddCorakShortcut = { sc -> doffVm.addCorakShortcut(sc) },
                onRemoveCorakShortcut = { sc -> doffVm.removeCorakShortcut(sc) },
                onResetCorakShortcuts = { doffVm.resetCorakShortcuts() },
                onImport = { json ->
                    uiVm.showConfirm("Pulihkan data dari file ini? Semua data saat ini akan diganti.") {
                        val oldKeys = state.estimasi.keys.toList()
                        doffVm.importJson(json) { imported ->
                            if (imported != null) {
                                NotificationHelper.cancelAll(context, oldKeys)
                                NotificationHelper.rescheduleAll(context, imported.estimasi.values)
                                uiVm.showToast("Data dipulihkan ✓")
                            } else {
                                uiVm.showToast("⚠ File cadangan tidak valid")
                            }
                        }
                    }
                },
                showToast = { uiVm.showToast(it) },
                showConfirm = { msg, fn -> uiVm.showConfirm(msg, onConfirm = fn) },
            )
        }

        if (activeOverlay is ActiveOverlay.Statistik) {
            StatistikScreen(
                history = state.history,
                db = state.db,
                onClose = { activeOverlay = ActiveOverlay.None },
                onDeleteShift = { id -> doffVm.hapusShift(id) },
                showConfirm = { msg, fn -> uiVm.showConfirm(msg, onConfirm = fn) },
                showToast = { uiVm.showToast(it) },
                onEditEntrySave = { shiftId, id, jam, ket, corakOverride, customYard ->
                    doffVm.updateAktualInShift(shiftId, id, jam, ket, corakOverride, customYard)
                },
                onDeleteEntry = { shiftId, id -> doffVm.hapusAktualDariShift(shiftId, id) },
                onAddEntry = { shiftId, mcNo, jam, ket, corakOverride, customYard ->
                    doffVm.tambahAktualKeShift(shiftId, mcNo, jam, ket, corakOverride, customYard)
                },
                corakShortcuts = state.corakShortcuts,
                keteranganShortcuts = state.keteranganShortcuts,
                onAddCorakShortcut = { doffVm.addCorakShortcut(it) },
                onAddKeteranganShortcut = { doffVm.addKeteranganShortcut(it) },
            )
        }

        if (shiftFinished.visible) {
            ShiftFinishedOverlay(
                checkScale = { shiftFinished.checkScale.value },
                backdropAlpha = { shiftFinished.backdropAlpha.value },
            )
        }
    }

    // Overlays
    val editAktId = (activeOverlay as? ActiveOverlay.EditAkt)?.id
    // activeOverlay is rememberSaveable now, so a restored EditAkt can point at an entry that no
    // longer exists (e.g. deleted from another device before this process was recreated) — reset
    // instead of leaving the overlay stuck non-None with nothing to show for it.
    LaunchedEffect(editAktId, state.aktual) {
        if (editAktId != null && state.aktual.none { it.id == editAktId }) {
            activeOverlay = ActiveOverlay.None
        }
    }
    val editAktEntry = editAktId?.let { id -> state.aktual.find { it.id == id } }
    if (editAktEntry != null) {
        EditAktSheet(
            entry = editAktEntry,
            mesin = state.db[editAktEntry.mcNo],
            onClose = { activeOverlay = ActiveOverlay.None },
            onSave = { id, jam, ket, corakOverride, customYard ->
                doffVm.updateAktual(id, jam, ket, corakOverride, customYard)
                uiVm.showToast("Riwayat diperbarui")
                activeOverlay = ActiveOverlay.None
            },
            onInvalidYard = { uiVm.showToast("Yard tidak valid") },
            onInvalidJam = { uiVm.showToast("Jam tidak valid — format 14.30") },
            onDelete = { handlers.handleHapusAktual(editAktId) { activeOverlay = ActiveOverlay.None } },
            corakShortcuts = state.corakShortcuts,
            keteranganShortcuts = state.keteranganShortcuts,
            onAddCorakShortcut = { doffVm.addCorakShortcut(it) },
            onAddKeteranganShortcut = { doffVm.addKeteranganShortcut(it) },
            showToast = { uiVm.showToast(it) },
        )
    }

    val quickEditMcNo = (activeOverlay as? ActiveOverlay.QuickEditMesin)?.mcNo
    if (quickEditMcNo != null) {
        val mesin = state.db[quickEditMcNo] ?: MesinData()
        QuickEditCorakDialog(
            mcNo = quickEditMcNo,
            corak = mesin.corak,
            targetYard = mesin.targetYard,
            onDismiss = { activeOverlay = ActiveOverlay.None },
            onSave = { corak, targetYard ->
                doffVm.setMesin(quickEditMcNo, mesin.copy(corak = corak, targetYard = targetYard))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                uiVm.showToast("Mc $quickEditMcNo disimpan ✓")
                activeOverlay = ActiveOverlay.None
            },
            corakShortcuts = state.corakShortcuts,
            onAddCorakShortcut = { doffVm.addCorakShortcut(it) },
            showToast = { uiVm.showToast(it) },
        )
    }

    val guidedEstimasiMcNo = (activeOverlay as? ActiveOverlay.GuidedEstimasi)?.mcNo
    if (guidedEstimasiMcNo != null) {
        GuidedEstimasiSheet(
            mcNo = guidedEstimasiMcNo,
            mesin = state.db[guidedEstimasiMcNo],
            onDismiss = { activeOverlay = ActiveOverlay.None },
            onSubmit = { value ->
                handlers.handleCommand(Mode.ESTIMASI, value) {
                    activeOverlay = ActiveOverlay.None
                }
            },
            onQuickUpdate = { corak, targetYard, tipe, koreksi, speed ->
                val mesin = state.db[guidedEstimasiMcNo] ?: MesinData()
                doffVm.setMesin(guidedEstimasiMcNo, mesin.copy(corak = corak, targetYard = targetYard, tipe = tipe, koreksi = koreksi, speed = speed))
            },
            showToast = { uiVm.showToast(it) },
            corakShortcuts = state.corakShortcuts,
            onAddCorakShortcut = { doffVm.addCorakShortcut(it) },
        )
    }

    val guidedDoffingMcNo = (activeOverlay as? ActiveOverlay.GuidedDoffing)?.mcNo
    if (guidedDoffingMcNo != null) {
        GuidedDoffingSheet(
            mcNo = guidedDoffingMcNo,
            mesin = state.db[guidedDoffingMcNo],
            estimasi = state.estimasi[guidedDoffingMcNo],
            onDismiss = { activeOverlay = ActiveOverlay.None },
            onSubmitDoffing = { value ->
                handlers.handleCommand(Mode.AKTUAL, value) {
                    activeOverlay = ActiveOverlay.None
                }
            },
            onQuickUpdate = { corak, targetYard, tipe, koreksi, speed ->
                val mesin = state.db[guidedDoffingMcNo] ?: MesinData()
                doffVm.setMesin(guidedDoffingMcNo, mesin.copy(corak = corak, targetYard = targetYard, tipe = tipe, koreksi = koreksi, speed = speed))
            },
            showToast = { uiVm.showToast(it) },
            corakShortcuts = state.corakShortcuts,
            keteranganShortcuts = state.keteranganShortcuts,
            onAddCorakShortcut = { doffVm.addCorakShortcut(it) },
            onAddKeteranganShortcut = { doffVm.addKeteranganShortcut(it) },
        )
    }

    if (!state.onboardingSeen) {
        OnboardingDialog(onClose = { doffVm.setOnboardingSeen() })
    }

    if (syncOpen) {
        SyncDialog(onClose = { syncOpen = false })
    }

    ConfirmDialog(
        confirm = confirm,
        onDismiss = { uiVm.dismissConfirm() },
    )
}
