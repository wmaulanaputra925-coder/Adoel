package com.jekael.adoel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// SendPulseState / ErrorFlashState used to live here — a scale bounce + checkmark on the old
// single "Kirim" button, and a red ring around the console input. Both were driven every submit
// but nothing had read them since the console became `[nomor mesin] + [Estimasi][Doffing]`: the
// button they animated is gone, and by the time a submit succeeds or fails the field is already
// cleared with a guided sheet covering it. The feedback they promised now lands where the
// operator is actually looking — the haptic in MainScreenHandlers plus the toast, which carries
// a red ring of its own on a rejected command (see ToastHost).

/** "Selesai Shift" celebration: checkmark scale-in over a dimming backdrop, both fading out on
 * their own after a beat. */
internal class ShiftFinishedState {
    var key by mutableIntStateOf(0)
    var visible by mutableStateOf(false)
    val checkScale = Animatable(0f)
    val backdropAlpha = Animatable(0f)
}

/** System permission/setting flags, refreshed on ON_RESUME (see MainScreen's DisposableEffect) —
 * notification permission, exact-alarm scheduling, and battery-optimization exemption. */
internal class PermissionState {
    var notifGranted by mutableStateOf(true)
    var exactAlarmGranted by mutableStateOf(true)
    var batteryUnrestricted by mutableStateOf(true)
}

/** One reversible mutation — [undo] restores the prior state, [redo] re-applies the original
 * action. Both sides are plain closures over whatever the call site already had on hand (same
 * data the old toast-attached "Undo" button used), so undo/redo cost nothing extra to build per
 * action site. */
internal class UndoableAction(val undo: () -> Unit, val redo: () -> Unit)

/** Session-scoped undo/redo history for console actions (doff, hapus estimasi, hapus aktual —
 * see MainScreenHandlers), surfaced as the Undo/Redo icon buttons in ConsoleBar (Master Blueprint
 * v9.2 §7) instead of a per-toast "Undo" action (§9). Not persisted across process death — same
 * lifetime the old toast-undo had, since it was never meant to survive the app being backgrounded
 * and killed either. Capped so a long shift of doffing doesn't grow this unboundedly. */
internal class UndoRedoState {
    private val undoStack = mutableStateListOf<UndoableAction>()
    private val redoStack = mutableStateListOf<UndoableAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records a newly-performed action. Clears the redo stack — once a new action happens, the
     * old "future" (whatever redo would have replayed) is no longer valid. */
    fun push(action: UndoableAction) {
        undoStack.add(action)
        if (undoStack.size > 20) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        action.undo()
        redoStack.add(action)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        action.redo()
        undoStack.add(action)
    }

    /** Called on "Selesai Shift" — once the console log is archived, undoing past it would
     * resurrect an aktual entry that no longer lives in [DoffState.aktual] but inside a
     * just-created [ShiftRecord] instead, so the whole history is invalidated at once. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
