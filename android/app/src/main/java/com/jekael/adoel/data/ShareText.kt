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

/** Baris "Operator: <nama> · Grup <grup>" untuk kepala pesan, atau null kalau belum didata.
 * Tidak pernah memakai penanda tebal: lihat catatan [formatAktualLine]. */
private fun formatOperatorLine(nama: String?, grup: String?): String? {
    val n = nama?.trim().orEmpty()
    val g = grup?.trim().orEmpty()
    return when {
        n.isEmpty() && g.isEmpty() -> null
        g.isEmpty() -> "Operator: $n"
        n.isEmpty() -> "Grup $g"
        else -> "Operator: $n · Grup $g"
    }
}

/**
 * Satu baris doff yang sudah selesai.
 *
 * Tanpa penanda tebal di sekitar nomor mesin. WhatsApp menolak memformat `*Mc 72*` di tengah
 * baris seperti ini — yang sampai ke rekan kerja justru bintangnya ikut terbaca — sementara
 * penanda tebal pada baris yang isinya HANYA teks tebal (judul bagian di bawah) tetap bekerja.
 * Jadi keterbacaan baris ini dibangun dari strukturnya: nomor mesin di depan, corak dan yard di
 * tengah, jam di belakang, dipisah pemisah yang berbeda supaya tiap bagian gampang dipindai.
 */
private fun formatAktualLine(index: Int, mcNo: String, corak: String, yard: Double?, ket: String): String {
    val yardSuffix = if (yard != null) " (${formatYard(yard)}y)" else ""
    return "${index + 1}. Mc $mcNo – $corak$yardSuffix · ${formatKetDisplay(ket)}"
}

private fun formatEstimasiLine(mcNo: String, corak: String, yard: Double?, estAbsMin: Long, zone: TimeZone): String {
    val yardSuffix = if (yard != null) " (${formatYard(yard)}y)" else ""
    return "• Mc $mcNo – $corak$yardSuffix · Est. ${absMinToTimeStr(estAbsMin, zone)}"
}

/** [nowMillis]/[zone] hanya untuk unit test — call site produksi memakai waktu & zona perangkat
 * saat ini, perilaku tidak berubah. */
fun buildShareHistoryText(
    state: DoffState,
    nowMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): String {
    val nowAbs = nowMillis / 60000L
    val shiftNo = shiftNumberForEpochMin(nowAbs, zone)
    val cal = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
    if (shiftNo == 3 && cal.get(Calendar.HOUR_OF_DAY) < 7) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    val dateStr = "%02d/%02d/%04d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR),
    )
    val lines = sortAktualChronological(state.aktual, nowAbs, zone).mapIndexed { i, a ->
        val mesin = state.db[a.mcNo]
        val corak = a.corakOverride ?: mesin?.corak ?: "—"
        val yard = a.customYard ?: mesin?.targetYard
        formatAktualLine(i, a.mcNo, corak, yard, a.ket)
    }
    // Doff selesai saja tidak cukup buat rekan yang baca pesan ini di lantai produksi — mereka
    // juga perlu tahu mesin mana yang masih ditimer sekarang dan kapan harus di-doff, jadi bagian
    // ini ikut disertakan alih-alih cuma riwayat yang sudah selesai.
    val currentShiftStart = currentShiftStartAbsMin(nowAbs, zone)
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

    val head = mutableListOf("*UPDATE DOFFING AKTIF*", "$dateStr · Shift $shiftNo")
    formatOperatorLine(state.operatorNama, state.operatorGrup)?.let { head += it }

    val blocks = mutableListOf(
        if (lines.isNotEmpty()) "*Selesai ($selesaiCount doff)*\n${lines.joinToString("\n")}" else "*Selesai (0 doff)*",
    )
    if (berjalanCount > 0) blocks += "*Sedang berjalan ($berjalanCount mc)*\n${berjalan.joinToString("\n")}"
    if (operanCount > 0) blocks += "*Operan shift berikutnya ($operanCount mc)*\n${operan.joinToString("\n")}"

    // Operan TIDAK ikut dijumlahkan: mesin itu baru akan di-doff setelah shift ini habis, jadi
    // memasukkannya ke total membuat shift ini terlihat mengerjakan pekerjaan shift berikutnya.
    // Jumlahnya tetap disebut di baris terpisah supaya rekan yang menerima operan tahu berapa.
    // Penjumlahannya dieja ("N selesai + N berjalan = N mc") karena tanpa itu rekan yang baca
    // sering salah hitung sendiri antara yang sudah selesai vs. yang masih berjalan.
    val totalLine = if (berjalanCount > 0) {
        "*Total shift ini: $selesaiCount selesai + $berjalanCount berjalan = ${selesaiCount + berjalanCount} mc*"
    } else {
        "*Total shift ini: $selesaiCount doff*"
    }
    val foot = mutableListOf(totalLine)
    if (operanCount > 0) foot += "Operan ke shift berikutnya: $operanCount mc (di luar total)"

    return "${head.joinToString("\n")}\n$DIVIDER\n\n${blocks.joinToString("\n\n")}\n\n$DIVIDER\n${foot.joinToString("\n")}"
}

/** Re-share a single archived shift — mirrors [buildShareHistoryText]'s format/tone exactly (same
 * casual "Bravo!!!" register, same audience: rekan kerja), for whenever an operator needs to
 * resend a specific day's record instead of the whole running total.
 *
 * [zone] hanya untuk unit test — call site produksi memakai default zona perangkat. */
fun buildShareShiftText(
    shift: ShiftRecord,
    db: Map<String, MesinData>,
    fallbackNama: String = "",
    fallbackGrup: String = "",
    zone: TimeZone = TimeZone.getDefault(),
): String {
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
    val head = mutableListOf("*LAPORAN SHIFT $shiftNo*", dateStr)
    // Operator yang dicap saat shift ini diarsipkan. Arsip dari sebelum pendataan operator ada
    // tidak punya capnya — untuk itu saja identitas yang berlaku sekarang dipakai sebagai
    // cadangan, karena satu ponsel dipegang satu operator dan laporan tanpa nama sama sekali
    // lebih merugikan daripada nama yang mungkin sudah pindah grup. Shift yang diarsipkan versi
    // ini dan seterusnya selalu memakai capnya sendiri.
    formatOperatorLine(
        shift.operatorNama.ifBlank { fallbackNama },
        shift.operatorGrup.ifBlank { fallbackGrup },
    )?.let { head += it }
    val body = if (lines.isNotEmpty()) {
        "*Selesai (${shift.aktual.size} doff)*\n${lines.joinToString("\n")}"
    } else {
        "*Selesai (0 doff)*"
    }
    return "${head.joinToString("\n")}\n$DIVIDER\n\n$body\n\n$DIVIDER\n*Total: ${shift.aktual.size} doff*"
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
