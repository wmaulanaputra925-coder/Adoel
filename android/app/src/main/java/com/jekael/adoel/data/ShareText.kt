package com.jekael.adoel.data

import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.TimeZone

/**
 * Shared "Bravo!!!"-toned WhatsApp share text — used both for the currently-running shift
 * (MainScreen/DoffingSection) and for re-sharing a single archived shift (StatistikScreen), kept
 * in one place so the two formats can't drift apart by editing only one call site. Covered by
 * golden-fixture tests (ShareTextTest) since this text is read by other people, not just exercised
 * internally.
 */

private val DIVIDER = "─".repeat(16)

/** "10.00(HB)" → "10.00 (HB)" — ket selalu "$jam" atau "$jam($extra)" tanpa spasi, jadi ini aman
 * dipakai apa adanya untuk kerapian tampilan di WhatsApp. */
private fun formatKetDisplay(ket: String): String = ket.replace("(", " (")

private fun formatAktualLine(index: Int, mcNo: String, corak: String, yard: Double?, ket: String): String {
    val yardSuffix = if (yard != null) " (${formatYard(yard)}y)" else ""
    return "${index + 1}. *Mc $mcNo* – $corak$yardSuffix · ${formatKetDisplay(ket)}"
}

private fun formatEstimasiLine(mcNo: String, corak: String, yard: Double?, estAbsMin: Long, zone: TimeZone): String {
    val yardSuffix = if (yard != null) " (${formatYard(yard)}y)" else ""
    return "• *Mc $mcNo* – $corak$yardSuffix · Est. ${absMinToTimeStr(estAbsMin, zone)}"
}

/** [nowMillis]/[zone] hanya untuk unit test — call site produksi memakai waktu & zona perangkat
 * saat ini, perilaku tidak berubah. */
fun buildShareHistoryText(
    state: DoffState,
    nowMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): String {
    val cal = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
    if (shiftNumberForEpochMin(nowMillis / 60000L, zone) == 3 && cal.get(Calendar.HOUR_OF_DAY) < 7) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    val dateStr = "%02d/%02d/%04d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR),
    )
    val lines = sortAktualChronological(state.aktual, nowMillis / 60000L, zone).mapIndexed { i, a ->
        val mesin = state.db[a.mcNo]
        val corak = a.corakOverride ?: mesin?.corak ?: "—"
        val yard = a.customYard ?: mesin?.targetYard
        formatAktualLine(i, a.mcNo, corak, yard, a.ket)
    }
    // Doff selesai saja tidak cukup buat rekan yang baca pesan ini di lantai produksi — mereka
    // juga perlu tahu mesin mana yang masih ditimer sekarang dan kapan harus di-doff, jadi bagian
    // ini ikut disertakan alih-alih cuma riwayat yang sudah selesai.
    val currentShiftStart = currentShiftStartAbsMin(nowMillis / 60000L, zone)
    val currentShiftEnd = currentShiftStart + 480L
    val (estimasiBerjalan, estimasiOperan) = state.estimasi.values
        .filter { it.estAbsMin >= currentShiftStart }
        .partition { it.estAbsMin < currentShiftEnd }
    fun formatEstimasi(est: Estimasi): String {
        val mesin = state.db[est.mcNo]
        val corak = est.corakOverride ?: mesin?.corak ?: "—"
        val yard = est.yardOverride ?: mesin?.targetYard
        return formatEstimasiLine(est.mcNo, corak, yard, est.estAbsMin, zone)
    }
    val berjalan = sortedByNearest(estimasiBerjalan.associateBy { it.mcNo }).map(::formatEstimasi)
    val operan = estimasiOperan.sortedBy { it.estAbsMin }.map(::formatEstimasi)
    val selesaiCount = state.aktual.size
    val berjalanCount = berjalan.size
    val operanCount = operan.size
    // Spelling the sum out explicitly (bukan cuma "Total: N doff") — tanpa ini, rekan yang baca
    // sering salah jumlah sendiri antara yang sudah selesai vs. yang masih berjalan (lihat contoh
    // nyata: seseorang nanya "jadi total 23 mc?" padahal "Total" di pesan cuma menghitung selesai).
    val totalEstimasiCount = berjalanCount + operanCount
    val totalLine = if (totalEstimasiCount > 0) {
        "📊 *Total: $selesaiCount selesai + $totalEstimasiCount berjalan = ${selesaiCount + totalEstimasiCount} mc*"
    } else {
        "📊 *Total: $selesaiCount doff*"
    }
    val berjalanBlock = if (berjalan.isNotEmpty()) {
        "\n\n⏳ *Sedang Berjalan ($berjalanCount mc)*\n${berjalan.joinToString("\n")}"
    } else {
        ""
    }
    val operanBlock = if (operan.isNotEmpty()) {
        "\n\n📤 *Operan Shift Berikutnya ($operanCount mc)*\n${operan.joinToString("\n")}"
    } else {
        ""
    }
    return "*UPDATE DOFFING AKTIF*\n📅 $dateStr\n$DIVIDER\n\n✅ *Selesai ($selesaiCount doff)*\n${lines.joinToString("\n")}$berjalanBlock$operanBlock\n$DIVIDER\n$totalLine"
}

/** Re-share a single archived shift — mirrors [buildShareHistoryText]'s format/tone exactly (same
 * casual "Bravo!!!" register, same audience: rekan kerja), for whenever an operator needs to
 * resend a specific day's record instead of the whole running total.
 *
 * [zone] hanya untuk unit test — call site produksi memakai default zona perangkat. */
fun buildShareShiftText(shift: ShiftRecord, db: Map<String, MesinData>, zone: TimeZone = TimeZone.getDefault()): String {
    val shiftNo = shiftNumberForEpochMin(shift.startedAtEpochMin, zone)
    val dateStr = formatShiftDate(shift.startedAtEpochMin, zone)
    // +240 (4 jam setelah mulai) dipakai sebagai titik tengah yang aman dari pembungkusan
    // tanggal untuk shift 8 jam manapun — shift ini sudah diarsipkan, jadi "sekarang" bukan
    // acuan yang masuk akal untuk menentukan hari mana yang dimaksud sebuah jam.
    val lines = sortAktualChronological(shift.aktual, shift.startedAtEpochMin + 240, zone).mapIndexed { i, a ->
        val mesin = db[a.mcNo]
        val corak = a.corakOverride ?: mesin?.corak ?: "—"
        val yard = a.customYard ?: mesin?.targetYard
        formatAktualLine(i, a.mcNo, corak, yard, a.ket)
    }
    return "*LAPORAN SHIFT $shiftNo*\n📅 $dateStr\n$DIVIDER\n\n✅ *Selesai (${shift.aktual.size} doff)*\n${lines.joinToString("\n")}\n$DIVIDER\n📊 *Total: ${shift.aktual.size} doff*"
}

/** Launches the system share sheet with [text] — shared by both callers above so a failure to
 * resolve a share-capable app is swallowed the same way in both places. */
fun shareIntent(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, chooserTitle)) }
}
