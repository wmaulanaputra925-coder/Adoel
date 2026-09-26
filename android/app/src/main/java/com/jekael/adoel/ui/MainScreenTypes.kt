package com.jekael.adoel.ui

import androidx.compose.runtime.saveable.Saver
import com.jekael.adoel.data.Estimasi
import com.jekael.adoel.ui.components.EditAktField
import com.jekael.adoel.ui.components.QuickEditField

/** Which pure-function path a guided sheet's submission routes through — ESTIMASI feeds
 * [com.jekael.adoel.viewmodel.DoffViewModel.prosesBarisKondisiMesin], AKTUAL feeds
 * `prosesBarisUmum`. No longer a console-level input mode (the console is guided-only now, see
 * ConsoleBar) — this only distinguishes which formula a given submission means. */
internal enum class Mode { ESTIMASI, AKTUAL }

/** Which top-level list is on screen — decoupled from [Mode] now that the console no longer
 * doubles as a page switcher (see MainScreenHeader's page tab row). */
internal enum class Page { RADAR, RIWAYAT }

/** Gaps at or above this many minutes between two upcoming doffs are worth flagging as a
 * break window — short enough to still be actionable, long enough to actually leave the floor. */
internal const val BREAK_GAP_THRESHOLD_MIN = 30L

internal sealed class MenungguRow {
    data class CardRow(val est: Estimasi) : MenungguRow()
    data class GapRow(val afterMcNo: String, val nextMcNo: String, val gapMin: Long, val nextAbsMin: Long) : MenungguRow()
}

/** The overlay panels/sheets MainScreen can show are mutually exclusive in practice (each opens
 * over a full-screen backdrop that blocks interaction with what triggers the others), so modeling
 * them as one sealed type instead of 4 independent flags makes that invariant structural. */
internal sealed interface ActiveOverlay {
    data object None : ActiveOverlay
    data object Mesin : ActiveOverlay
    data object Settings : ActiveOverlay
    data object Statistik : ActiveOverlay
    data class EditAkt(val id: Int, val field: EditAktField = EditAktField.ALL) : ActiveOverlay
    data class QuickEditMesin(val mcNo: String, val field: QuickEditField = QuickEditField.ALL) : ActiveOverlay
    data class GuidedEstimasi(val mcNo: String) : ActiveOverlay
    data class GuidedDoffing(val mcNo: String) : ActiveOverlay
}

/** Custom Saver for rememberSaveable — ActiveOverlay's variants aren't natively Bundle-storable
 * (no Parcelable/Serializable), so this encodes each as a (tag, payload) pair. Restoring
 * EditAkt/QuickEditMesin across process death can point at data that no longer exists (the entry
 * was deleted, e.g. via a backup restore, while the app was gone) — MainScreen validates that
 * against the freshly-loaded state before trusting the restored overlay. */
internal val ActiveOverlaySaver = Saver<ActiveOverlay, List<Any?>>(
    save = { overlay ->
        when (overlay) {
            is ActiveOverlay.None -> listOf("None", null)
            is ActiveOverlay.Mesin -> listOf("Mesin", null)
            is ActiveOverlay.Settings -> listOf("Settings", null)
            is ActiveOverlay.Statistik -> listOf("Statistik", null)
            is ActiveOverlay.EditAkt -> listOf("EditAkt", overlay.id, overlay.field.name)
            is ActiveOverlay.QuickEditMesin -> listOf("QuickEditMesin", overlay.mcNo, overlay.field.name)
            is ActiveOverlay.GuidedEstimasi -> listOf("GuidedEstimasi", overlay.mcNo)
            is ActiveOverlay.GuidedDoffing -> listOf("GuidedDoffing", overlay.mcNo)
        }
    },
    restore = { saved ->
        when (saved.getOrNull(0) as? String) {
            "Mesin" -> ActiveOverlay.Mesin
            "Settings" -> ActiveOverlay.Settings
            "Statistik" -> ActiveOverlay.Statistik
            "EditAkt" -> (saved.getOrNull(1) as? Int)?.let { id ->
                val field = (saved.getOrNull(2) as? String)?.let { runCatching { EditAktField.valueOf(it) }.getOrNull() } ?: EditAktField.ALL
                ActiveOverlay.EditAkt(id, field)
            } ?: ActiveOverlay.None
            "QuickEditMesin" -> (saved.getOrNull(1) as? String)?.let { mcNo ->
                val field = (saved.getOrNull(2) as? String)?.let { runCatching { QuickEditField.valueOf(it) }.getOrNull() } ?: QuickEditField.ALL
                ActiveOverlay.QuickEditMesin(mcNo, field)
            } ?: ActiveOverlay.None
            "GuidedEstimasi" -> (saved.getOrNull(1) as? String)?.let { ActiveOverlay.GuidedEstimasi(it) } ?: ActiveOverlay.None
            "GuidedDoffing" -> (saved.getOrNull(1) as? String)?.let { ActiveOverlay.GuidedDoffing(it) } ?: ActiveOverlay.None
            else -> ActiveOverlay.None
        }
    },
)
