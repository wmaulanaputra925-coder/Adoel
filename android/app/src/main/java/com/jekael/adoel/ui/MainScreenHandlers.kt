package com.jekael.adoel.ui

import android.content.Context
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.jekael.adoel.data.Estimasi
import com.jekael.adoel.data.ProsesResult
import com.jekael.adoel.data.effectiveRemaining
import com.jekael.adoel.data.formatDeltaMin
import com.jekael.adoel.data.nowAbsMin
import com.jekael.adoel.notification.NotificationHelper
import com.jekael.adoel.viewmodel.DoffViewModel
import com.jekael.adoel.viewmodel.UIViewModel

/** Console/radar/doffing action handlers, hoisted out of MainScreen's composable body and held via
 * `remember { MainScreenHandlers(...) }` so this instance's identity is stable across recomposition
 * (letting callbacks passed to children stay stable too, instead of being redefined every time
 * MainScreen recomposes). Reads [doffVm]'s state fresh inside every call via `.state.value` — never
 * captures a snapshot at construction time — since the instance itself is not recreated when the
 * app state changes. */
internal class MainScreenHandlers(
    private val context: Context,
    private val doffVm: DoffViewModel,
    private val uiVm: UIViewModel,
    private val haptic: HapticFeedback,
    private val shiftFinished: ShiftFinishedState,
    private val undoRedo: UndoRedoState,
) {
    /** Rejected command: deep haptic + a toast the operator can feel and see. The "⚠ " prefix is
     * what ToastHost keys its red-ring error styling off, so every rejection reads the same. */
    private fun flashError(msg: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        uiVm.showToast("⚠ $msg")
    }

    private fun submitEstimasi(cmd: String, onCleared: () -> Unit) {
        val result = doffVm.prosesBarisKondisiMesin(cmd, nowAbsMin())
        when (result) {
            is ProsesResult.Ok -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                uiVm.showToast(result.msg)
                onCleared()
                result.estAbs?.let { NotificationHelper.scheduleNotif(context, result.mcNo, it) }
            }
            is ProsesResult.Err -> flashError(result.msg)
        }
    }

    fun handleCommand(mode: Mode, input: String, onCleared: () -> Unit) {
        val cmd = input.trim().uppercase()
        if (cmd.isEmpty()) return

        when (mode) {
            Mode.ESTIMASI -> {
                val mcNo = cmd.substringBefore(' ')
                val existing = doffVm.state.value.estimasi[mcNo]
                val remaining = existing?.effectiveRemaining(nowAbsMin())
                // Salah masuk mode ESTIMASI lalu ngetik cepat bisa nggak sadar menimpa timer yang
                // masih jalan — prosesBarisKondisiMesin tidak punya undo, jadi khusus estimasi yang
                // masih aktif & mepet (<10 menit lagi), minta konfirmasi dulu, bukan menimpa diam-diam.
                if (existing != null && remaining != null && remaining in 0 until 10) {
                    uiVm.showConfirm("Mc $mcNo sudah diestimasi ${formatDeltaMin(remaining)} lagi. Timpa dengan estimasi baru?") {
                        submitEstimasi(cmd, onCleared)
                    }
                } else {
                    submitEstimasi(cmd, onCleared)
                }
            }
            Mode.AKTUAL -> {
                val result = doffVm.prosesBarisUmum(cmd)
                when (result) {
                    is ProsesResult.Ok -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        NotificationHelper.cancelNotif(context, result.mcNo)
                        undoRedo.push(
                            UndoableAction(
                                undo = { result.undoFn?.invoke(); rescheduleEstimasi(result.prevEst) },
                                // Restores the exact entry the doff created (same id) instead of
                                // re-running prosesBarisUmum, which would mint a brand new entry —
                                // every extra undo/redo cycle would otherwise leave one more
                                // duplicate row in Riwayat, since the stale undoFn above only ever
                                // knows how to remove the original id.
                                redo = {
                                    result.entry?.let { doffVm.restoreAktual(it) }
                                    doffVm.hapusEstimasi(result.mcNo)
                                    NotificationHelper.cancelNotif(context, result.mcNo)
                                },
                            ),
                        )
                        uiVm.showToast(result.msg)
                        onCleared()
                    }
                    is ProsesResult.Err -> flashError(result.msg)
                }
            }
        }
    }

    // "MATCHING" doffs from the radar card's swipe gesture are gated one layer up, via
    // RadarCard's guardDoffMatching (wired from MainScreen through RadarSection) — that has to
    // run *before* the swipe's slide-out animation starts, not here after it's already played.
    // GuidedDoffingSheet's Matching pick gates itself the same way, in its own composable. This
    // function's own keterangan is trusted as already-confirmed by the time it's called.
    fun handleDoff(mcNo: String, keterangan: String? = null) {
        val cmd = if (keterangan != null) "$mcNo $keterangan" else mcNo
        val result = doffVm.prosesBarisUmum(cmd)
        when (result) {
            is ProsesResult.Ok -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                NotificationHelper.cancelNotif(context, result.mcNo)
                undoRedo.push(
                    UndoableAction(
                        undo = { result.undoFn?.invoke(); rescheduleEstimasi(result.prevEst) },
                        // Restores the exact entry the doff created (same id) instead of re-running
                        // prosesBarisUmum, which would mint a brand new entry — every extra
                        // undo/redo cycle would otherwise leave one more duplicate row in Riwayat,
                        // since the stale undoFn above only ever knows how to remove the original id.
                        redo = {
                            result.entry?.let { doffVm.restoreAktual(it) }
                            doffVm.hapusEstimasi(result.mcNo)
                            NotificationHelper.cancelNotif(context, result.mcNo)
                        },
                    ),
                )
                uiVm.showToast(result.msg)
            }
            is ProsesResult.Err -> uiVm.showToast("⚠ ${result.msg}")
        }
    }

    fun handleHapusEst(mcNo: String) {
        uiVm.showConfirm("Hapus estimasi Mc $mcNo?") {
            val prevEst = doffVm.state.value.estimasi[mcNo]
            doffVm.hapusEstimasi(mcNo)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            NotificationHelper.cancelNotif(context, mcNo)
            undoRedo.push(
                UndoableAction(
                    undo = {
                        if (prevEst != null) {
                            doffVm.restoreEstimasi(prevEst)
                            rescheduleEstimasi(prevEst)
                        }
                    },
                    redo = { doffVm.hapusEstimasi(mcNo); NotificationHelper.cancelNotif(context, mcNo) },
                ),
            )
            uiVm.showToast("Mc $mcNo dihapus")
        }
    }

    /** Freezes Mc [mcNo]'s countdown (RadarCard's tekan-tahan → Jeda), suppressing its reminder
     * notification until Lanjutkan reschedules it against the shifted estimate. No confirm dialog
     * — unlike Hapus this is fully reversible with one more tap (Lanjutkan), so gating it behind a
     * dialog would just slow down the one action meant to be fast. */
    fun handleJeda(mcNo: String) {
        val prevEst = doffVm.state.value.estimasi[mcNo] ?: return
        doffVm.pauseEstimasi(mcNo)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        NotificationHelper.cancelNotif(context, mcNo)
        undoRedo.push(
            UndoableAction(
                undo = { doffVm.restoreEstimasi(prevEst); rescheduleEstimasi(prevEst) },
                redo = { doffVm.pauseEstimasi(mcNo); NotificationHelper.cancelNotif(context, mcNo) },
            ),
        )
        uiVm.showToast("Mc $mcNo dijeda")
    }

    fun handleLanjutkan(mcNo: String) {
        val prevEst = doffVm.state.value.estimasi[mcNo] ?: return
        doffVm.resumeEstimasi(mcNo)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val resumedEst = doffVm.state.value.estimasi[mcNo]
        rescheduleEstimasi(resumedEst)
        undoRedo.push(
            UndoableAction(
                undo = { doffVm.restoreEstimasi(prevEst); rescheduleEstimasi(prevEst) },
                redo = {
                    doffVm.resumeEstimasi(mcNo)
                    rescheduleEstimasi(doffVm.state.value.estimasi[mcNo])
                },
            ),
        )
        uiVm.showToast("Mc $mcNo dilanjutkan")
    }

    fun handleHapusAktual(id: Int, onCleared: () -> Unit) {
        val entry = doffVm.state.value.aktual.find { it.id == id } ?: return
        uiVm.showConfirm("Hapus riwayat Mc ${entry.mcNo}?") {
            doffVm.hapusAktualById(id)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onCleared()
            undoRedo.push(
                UndoableAction(
                    undo = { doffVm.restoreAktual(entry) },
                    redo = { doffVm.hapusAktualById(id) },
                ),
            )
            uiVm.showToast("Mc ${entry.mcNo} dihapus")
        }
    }

    fun handleFinishShift() {
        val state = doffVm.state.value
        // finishShift() itself already no-ops on an empty console — mirror that here so an
        // accidental/duplicate tap doesn't show a confirm dialog and checkmark celebration for
        // archiving "0 doff & 0 estimasi".
        if (state.aktual.isEmpty() && state.estimasi.isEmpty()) {
            uiVm.showToast("Tidak ada yang perlu diarsipkan")
            return
        }
        // Doffing history is archived; active estimasi are deleted outright (not archived — see
        // finishShift's own doc), so the message says "diarsipkan" for one and "dihapus" for the
        // other rather than implying both survive into Riwayat.
        val confirmMsg = if (state.aktual.isNotEmpty()) {
            if (state.estimasi.isNotEmpty()) {
                "Akhiri shift? ${state.aktual.size} riwayat doffing akan diarsipkan ke Riwayat, dan ${state.estimasi.size} estimasi aktif akan dihapus."
            } else {
                "Akhiri shift? ${state.aktual.size} riwayat doffing akan diarsipkan ke Riwayat."
            }
        } else {
            "Akhiri shift? Tidak ada riwayat doffing untuk diarsipkan, ${state.estimasi.size} estimasi aktif akan dihapus."
        }
        uiVm.showConfirm(confirmMsg) {
            NotificationHelper.cancelAll(context, state.estimasi.keys.toList())
            doffVm.finishShift()
            undoRedo.clear()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shiftFinished.key++
        }
    }

    private fun rescheduleEstimasi(est: Estimasi?) {
        est?.let {
            if (it.pausedAtAbsMin == null) {
                NotificationHelper.scheduleNotif(context, it.mcNo, it.estAbsMin)
            } else {
                NotificationHelper.cancelNotif(context, it.mcNo)
            }
        }
    }
}
