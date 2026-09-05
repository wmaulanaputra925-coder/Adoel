package com.jekael.adoel.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

enum class MesinTipe { TAPPET, CAM, D405, D408 }

data class MesinData(
    val tipe: MesinTipe = MesinTipe.TAPPET,
    val corak: String = "-",
    val targetYard: Double? = null,
    val speed: Double? = null,
    val koreksi: Double? = null,
    val isActive: Boolean = true,
)

/** True when [db] has nothing an operator has actually set up yet — every entry (or no entries
 * at all) still has its untouched defaults. Gates the first-launch auto-QR-sync prompt (MainScreen)
 * so a fresh install offers to import a coworker's data before falling back to the plain onboarding
 * walkthrough. Port 1:1 of isMachineDataEmpty (web/src/domain/sync.ts). */
fun isMachineDataEmpty(db: Map<String, MesinData>): Boolean {
    if (db.isEmpty()) return true
    return db.values.all { m -> (m.corak.isBlank() || m.corak == "-") && m.targetYard == null && m.speed == null }
}

data class Estimasi(
    val mcNo: String,
    val estAbsMin: Long,
    val startAbsMin: Long,
    val corakOverride: String? = null,
    val yardOverride: Double? = null,
    // Non-null while the operator has Jeda'd this machine — the abs-minute timestamp Jeda was
    // pressed at. Time is frozen from the operator's point of view while paused (see
    // Estimasi.effectiveRemaining in EstimasiUtils.kt): estAbsMin itself doesn't move until
    // Lanjutkan shifts it forward by however long the pause lasted (DoffViewModel.resumeEstimasi).
    val pausedAtAbsMin: Long? = null,
)

data class AktualEntry(
    val id: Int,
    val mcNo: String,
    val jam: String,
    val ket: String,
    val corakOverride: String? = null,
    val customYard: Double? = null,
    // Null for entries persisted before this field existed (Gson leaves it null on old data).
    // "jam" is only a display string ("HH.mm") that's ambiguous across a midnight-crossing
    // shift; this absolute-minute timestamp lets shift-history stats sort/measure durations
    // correctly regardless of when the entry was recorded relative to midnight.
    val tsEpochMin: Long? = null,
)

/** One archived shift, created when "Selesai Shift" is confirmed (see DoffViewModel.finishShift). */
data class ShiftRecord(
    val id: Int,
    val startedAtEpochMin: Long,
    val endedAtEpochMin: Long,
    val aktual: List<AktualEntry> = emptyList(),
    val estimasiRemaining: Map<String, Estimasi> = emptyMap(),
    /** Operator & grup yang menutup shift ini, dicap saat diarsipkan — bukan dibaca ulang dari
     * pengaturan saat laporannya dibagikan, supaya arsip lama tidak berganti nama pemilik ketika
     * operator/grup di pengaturan berubah. Kosong untuk arsip yang dibuat sebelum ada pendataan
     * ini; teks bagikannya sekadar tidak mencantumkan baris operator. */
    val operatorNama: String = "",
    val operatorGrup: String = "",
)

val DEFAULT_KETERANGAN_SHORTCUTS = emptyList<String>()
val DEFAULT_CORAK_SHORTCUTS = emptyList<String>()

/** Corak dengan aturan "potongan awal 70 yard" — begitu beam lusi baru naik, kain di awal jalan
 * sering masih banyak cacat (LTK, lusi putus) sampai mesin stabil, jadi sampel Doffing Matching
 * (1 yard) untuk corak-corak ini baru boleh diambil setelah 70y, bukan langsung dari 0. Sama
 * persis dengan DEFAULT_CORAK_POTONGAN_AWAL di types.ts (web) — jaga daftar & pesannya identik. */
val DEFAULT_CORAK_POTONGAN_AWAL = listOf("80125", "21242", "66335")

data class DoffState(
    val db: Map<String, MesinData> = emptyMap(),
    val estimasi: Map<String, Estimasi> = emptyMap(),
    val aktual: List<AktualEntry> = emptyList(),
    val nextId: Int = 1,
    val themeMode: String = "SYSTEM",
    val history: List<ShiftRecord> = emptyList(),
    val nextShiftId: Int = 1,
    // Defaults true (already-seen) so existing users upgrading from a version that predates this
    // field don't suddenly get the first-run tutorial — it's only explicitly set false in
    // DoffRepository.parseState()'s genuinely-fresh-install fallback (no persisted state at all).
    val onboardingSeen: Boolean = true,
    /** Identitas operator pemakai aplikasi ini — ditanyakan sekali saat pertama kali dibuka dan
     * bisa diubah kapan saja di Pengaturan. Ikut tercetak di teks bagikan supaya rekan yang
     * membaca laporan di WhatsApp tahu laporan itu dari siapa tanpa harus bertanya. */
    val operatorNama: String = "",
    val operatorGrup: String = "",
    val keteranganShortcuts: List<String>? = null,
    val corakShortcuts: List<String>? = null,
    val corakPotonganAwal: List<String>? = null,
)

/** Cek apakah [corak] termasuk [corakPotonganAwal] (atau [DEFAULT_CORAK_POTONGAN_AWAL] kalau
 * null — belum pernah diset) — dipakai untuk menampilkan pengingat sebelum Doffing Matching
 * dicatat. Takes the bare list (not the whole DoffState) so composables that only have
 * corakPotonganAwal threaded through as a param (same convention as corakShortcuts) can call it
 * without needing the full state too. Sama persis (isi & pesan) dengan isPotonganAwalCorak di
 * matchingRules.ts (web), yang mengambil `state.corakPotonganAwal` langsung dari store-nya. */
fun isPotonganAwalCorak(corakPotonganAwal: List<String>?, corak: String?): Boolean {
    if (corak.isNullOrBlank()) return false
    val trimmed = corak.trim().uppercase()
    val list = corakPotonganAwal ?: DEFAULT_CORAK_POTONGAN_AWAL
    return list.any { it.trim().uppercase() == trimmed }
}

fun potonganAwalReminderMessage(corak: String): String =
    "Corak $corak termasuk daftar potongan awal 70 yard. Pastikan beam sudah jalan minimal 70y sebelum ambil sampel Matching (1 yard), supaya sampel tidak kena LTK/lusi putus di awal jalan. Lanjutkan catat Doffing Matching sekarang?"

fun getRepresentativeEpochMin(shift: ShiftRecord): Long {
    val timestamps = shift.aktual.mapNotNull { it.tsEpochMin }
    return if (timestamps.isNotEmpty()) {
        timestamps.average().toLong()
    } else {
        (shift.startedAtEpochMin + shift.endedAtEpochMin) / 2
    }
}

sealed class ProsesResult {
    data class Ok(
        val msg: String,
        val mcNo: String,
        val estAbs: Long? = null,
        val prevEst: Estimasi? = null,
        val undoFn: (() -> Unit)? = null,
        // The exact AktualEntry a doff (prosesBarisUmum) just created — lets a redo restore that
        // same row (same id) instead of re-running the whole command and minting a new one, which
        // would leave a duplicate behind every time undo/redo is repeated (nextId always advances).
        val entry: AktualEntry? = null,
    ) : ProsesResult()
    data class Err(val msg: String) : ProsesResult()
}

fun nowAbsMin(): Long = System.currentTimeMillis() / 60000L

/** [zone] hanya untuk unit test — call site produksi memakai default zona perangkat. */
fun absMinToTimeStr(absMin: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val cal = Calendar.getInstance(zone).apply { timeInMillis = absMin * 60000L }
    return "%02d.%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

/** [zone] hanya untuk unit test — call site produksi memakai default zona perangkat. */
fun formatShiftDate(epochMin: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMin * 60000L }
    return "%02d/%02d/%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

/** [zone] hanya untuk unit test — call site produksi memakai default zona perangkat. */
fun formatShiftShortDate(epochMin: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMin * 60000L }
    return "%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
}

/** [zone] hanya untuk unit test — call site produksi memakai default zona perangkat. */
fun formatShiftTime(epochMin: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMin * 60000L }
    return "%02d.%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

fun nowTimeStr(): String {
    val cal = Calendar.getInstance()
    return "%02d.%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

/** [min]: menit sejak tengah malam (hasil [parseJam]) — bukan epoch-minute absolut, jadi
 * bukan [absMinToTimeStr]. Dipakai saat mengoreksi jam di [EditAktSheet]. */
fun minOfDayToTimeStr(min: Int): String = "%02d.%02d".format(min / 60, min % 60)

fun formatDeltaMin(deltaMin: Long): String {
    val sign = if (deltaMin < 0) "−" else ""
    val mag = abs(deltaMin)
    return if (mag >= 60) "$sign${mag / 60}j${mag % 60}m" else "$sign${mag}m"
}

fun formatYard(y: Double): String =
    if (y == y.toLong().toDouble()) y.toLong().toString() else y.toString()

/** Fixed 3-shift schedule: Shift 1 06.00–14.00, Shift 2 14.00–22.00, Shift 3 22.00–06.00
 * (crosses midnight). Classified by the hour-of-day the shift started. [zone] hanya untuk unit
 * test — call site produksi memakai default zona perangkat, perilaku tidak berubah. */
fun shiftNumberForEpochMin(epochMin: Long, zone: TimeZone = TimeZone.getDefault()): Int {
    val hour = Calendar.getInstance(zone).apply { timeInMillis = epochMin * 60000L }.get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 6 until 14 -> 1
        hour in 14 until 22 -> 2
        else -> 3
    }
}

/** Epoch-minute the *current* shift period began (the most recent 06.00/14.00/22.00 boundary at
 * or before [epochMin]) — used to tell whether an already-recorded entry belongs to a shift that
 * hasn't been archived yet; MainScreen archives such data automatically at the next boundary. */
fun currentShiftStartAbsMin(epochMin: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMin * 60000L }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val boundaryHour = if (hour in 6 until 22) (if (hour < 14) 6 else 14) else 22
    if (hour < 6) cal.add(Calendar.DAY_OF_YEAR, -1)
    cal.set(Calendar.HOUR_OF_DAY, boundaryHour)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 60000L
}

/** One day / half a day in minutes — used by [jamKeShiftAbs] to snap an ambiguous wall-clock
 * reading (which carries no date) onto whichever calendar day puts it within 12 hours of now.
 * Assumes fixed-offset days of exactly 24h; fine for WIB (no DST), not portable to DST zones. */
private const val DAY_MIN = 1440L
private const val HALF_DAY_MIN = 720L

/** [nowEpochMin]/[zone] hanya untuk unit test — "hari ini" diturunkan dari [nowEpochMin] di
 * [zone], sehingga test bisa memilih momen tetap; default-nya identik dengan perilaku lama. */
fun jamKeShiftAbs(
    jamMin: Int,
    nowEpochMin: Long = nowAbsMin(),
    zone: TimeZone = TimeZone.getDefault(),
): Long {
    val startOfDay = Calendar.getInstance(zone).apply {
        timeInMillis = nowEpochMin * 60000L
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val epochMinToday = startOfDay / 60000L + jamMin
    val diff = epochMinToday - nowEpochMin
    return when {
        diff < -HALF_DAY_MIN -> epochMinToday + DAY_MIN
        diff > HALF_DAY_MIN -> epochMinToday - DAY_MIN
        else -> epochMinToday
    }
}

fun parseJam(str: String): Int? {
    val s = str.trim().replace(',', '.')
    val m1 = Regex("""^(\d{1,2})[.:](\d{2})$""").matchEntire(s)
    if (m1 != null) {
        val h = m1.groupValues[1].toInt()
        val mi = m1.groupValues[2].toInt()
        if (h in 0..23 && mi in 0..59) return h * 60 + mi
        return null
    }
    val m2 = Regex("""^(\d{2})(\d{2})$""").matchEntire(s)
    if (m2 != null) {
        val h = m2.groupValues[1].toInt()
        val mi = m2.groupValues[2].toInt()
        if (h in 0..23 && mi in 0..59) return h * 60 + mi
        return null
    }
    return null
}

/** Urutan entri aktual berdasarkan jam ASLI tiap entri (bisa dikoreksi manual lewat
 * EditAktSheet), bukan urutan input ke aplikasi. Dulu list Riwayat/Statistik/teks bagikan
 * cuma membalik daftar aktual (terbaru di-prepend di indeks 0), yang diam-diam mengasumsikan
 * operator selalu mencatat doff persis sesuai urutan kejadiannya — jebol begitu operator
 * mengecek riwayat potong di mesin lalu input belakangan sambil mengoreksi jam supaya sesuai
 * kronologi sebenarnya (mis. gantian rekan saat istirahat): entrinya tetap nyangkut di
 * posisi KAPAN DIKETIK, bukan pindah ke posisi jam yang sudah dikoreksi.
 *
 * [anchorEpochMin] menentukan hari mana yang dimaksud sebuah jam (karena "jam" cuma string
 * "14.30" tanpa info tanggal) — pakai waktu sekarang untuk shift yang sedang berjalan, atau
 * titik tetap seperti mulainya shift untuk shift yang sudah diarsipkan (supaya tidak salah
 * hari kalau dibuka berhari-hari kemudian). Entri dengan jam tidak valid didorong ke akhir. */
fun sortAktualChronological(
    aktual: List<AktualEntry>,
    anchorEpochMin: Long,
    zone: TimeZone = TimeZone.getDefault(),
): List<AktualEntry> =
    aktual.sortedBy { entry -> parseJam(entry.jam)?.let { jamKeShiftAbs(it, anchorEpochMin, zone) } ?: Long.MAX_VALUE }

fun parseDurasi(str: String): Int? {
    val s = str.trim().replace(',', '.')
    val m1 = Regex("""^(\d{1,2})\.(\d{2})$""").matchEntire(s)
    if (m1 != null) {
        val h = m1.groupValues[1].toInt()
        val mi = m1.groupValues[2].toInt()
        if (mi in 0..59) return h * 60 + mi
        return null
    }
    val m2 = Regex("""^(\d{1,4})m(?:enit)?$""", RegexOption.IGNORE_CASE).matchEntire(s)
    if (m2 != null) return m2.groupValues[1].toInt()
    val m3 = Regex("""^(\d{1,4})$""").matchEntire(s)
    if (m3 != null) return m3.groupValues[1].toInt()
    return null
}

/** Canonical keterangan codes, in the order offered as tap targets — single source for
 * ConsoleBar's Teks chips and GuidedDoffingSheet's Terpandu chips so the two input styles can
 * never drift to offer a different set. */
val KETERANGAN_CODES = listOf("HB", "P.LP", "P.SN", "P.OH", "P.EL", "P.Sel")

fun standarisasiKeterangan(raw: String): String {
    val t = raw.trim().lowercase()
    return when {
        t == "hb" -> "HB"
        t in listOf("lp", "p.lp", "p. lp", "p lp") -> "P.LP"
        t in listOf("sn", "p.sn", "p. sn", "p sn", "snarling") -> "P.SN"
        t in listOf("oh", "p.oh", "p. oh", "p oh", "overhaul") -> "P.OH"
        t in listOf("el", "p.el", "p. el", "p el", "elektrik") -> "P.EL"
        t in listOf("sel", "selvedge", "p.sel", "p. sel", "p sel") -> "P.Sel"
        t in listOf("matching", "match") -> "MATCHING"
        else -> raw.trim()
    }
}
