package com.jekael.adoel.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Golden-fixture test untuk teks Bagikan (WhatsApp) — format laporan, ditujukan ke rekan kerja di
 * lantai produksi. Fixture di bawah ditulis dari keluaran kode SEBELUM dipindah ke fungsi murni ini
 * (shareHistory di DoffingSection.kt lama, shareShift di StatistikScreen.kt lama) — supaya
 * pemindahan kode tidak diam-diam mengubah pesan yang dibaca orang lain.
 */
class ShareTextTest {

    private val wib = TimeZone.getTimeZone("Asia/Jakarta")
    private val divider = "─".repeat(16)

    private fun epochMin(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(wib).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 60000L

    @Test
    fun buildShareHistoryText_withRunningAndFinished() {
        val db = mapOf(
            "29" to MesinData(MesinTipe.TAPPET, "34758", targetYard = 303.0),
            "61" to MesinData(MesinTipe.D405, "60357", targetYard = 303.0, speed = 0.158),
            "76" to MesinData(MesinTipe.CAM, "21242", targetYard = 165.0),
        )
        val state = DoffState(
            db = db,
            aktual = listOf(
                AktualEntry(id = 2, mcNo = "61", jam = "11.00", ket = "11.00(HB)", customYard = 120.0, tsEpochMin = epochMin(2026, 1, 15, 11, 0)),
                AktualEntry(id = 1, mcNo = "29", jam = "10.00", ket = "10.00", tsEpochMin = epochMin(2026, 1, 15, 10, 0)),
            ),
            estimasi = mapOf(
                "76" to Estimasi("76", estAbsMin = epochMin(2026, 1, 15, 16, 20), startAbsMin = epochMin(2026, 1, 15, 15, 0)),
            ),
        )
        val nowMillis = epochMin(2026, 1, 15, 12, 0) * 60000L

        val text = buildShareHistoryText(state, nowMillis, wib)

        val expected = "*UPDATE DOFFING AKTIF*\n📅 15/01/2026\n$divider\n\n" +
            "✅ *Selesai (2 doff)*\n" +
            "1. *Mc 29* – 34758 (303y) · 10.00\n" +
            "2. *Mc 61* – 60357 (120y) · 11.00 (HB)\n\n" +
            "📤 *Operan Shift Berikutnya (1 mc)*\n" +
            "• *Mc 76* – 21242 (165y) · Est. 16.20\n" +
            "$divider\n" +
            "📊 *Total: 2 selesai + 1 berjalan = 3 mc*"
        assertEquals(expected, text)
    }

    @Test
    fun buildShareHistoryText_finishedOnlyOmitsRunningBlock() {
        val db = mapOf("29" to MesinData(MesinTipe.TAPPET, "34758"))
        val state = DoffState(
            db = db,
            aktual = listOf(AktualEntry(id = 1, mcNo = "29", jam = "10.00", ket = "10.00")),
        )
        val nowMillis = epochMin(2026, 1, 15, 12, 0) * 60000L

        val text = buildShareHistoryText(state, nowMillis, wib)

        val expected = "*UPDATE DOFFING AKTIF*\n📅 15/01/2026\n$divider\n\n" +
            "✅ *Selesai (1 doff)*\n1. *Mc 29* – 34758 · 10.00\n" +
            "$divider\n📊 *Total: 1 doff*"
        assertEquals(expected, text)
    }

    @Test
    fun buildShareHistoryText_shiftThreeAfterMidnightUsesShiftStartDate() {
        val state = DoffState()
        val nowMillis = epochMin(2026, 1, 16, 5, 0) * 60000L

        val text = buildShareHistoryText(state, nowMillis, wib)

        val expected = "*UPDATE DOFFING AKTIF*\n📅 15/01/2026\n$divider\n\n" +
            "✅ *Selesai (0 doff)*\n\n" +
            "$divider\n📊 *Total: 0 doff*"
        assertEquals(expected, text)
    }

    @Test
    fun buildShareShiftText_matchesArchivedShiftFormat() {
        val db = mapOf("61" to MesinData(MesinTipe.D405, "60357", targetYard = 303.0))
        val shift = ShiftRecord(
            id = 5,
            startedAtEpochMin = epochMin(2026, 1, 15, 6, 0),
            endedAtEpochMin = epochMin(2026, 1, 15, 14, 0),
            aktual = listOf(
                AktualEntry(id = 2, mcNo = "61", jam = "11.00", ket = "11.00(HB)", customYard = 120.0),
                AktualEntry(id = 1, mcNo = "61", jam = "07.00", ket = "07.00"),
            ),
        )

        val text = buildShareShiftText(shift, db, wib)

        val expected = "*LAPORAN SHIFT 1*\n📅 15/01/2026\n$divider\n\n" +
            "✅ *Selesai (2 doff)*\n" +
            "1. *Mc 61* – 60357 (303y) · 07.00\n" +
            "2. *Mc 61* – 60357 (120y) · 11.00 (HB)\n" +
            "$divider\n📊 *Total: 2 doff*"
        assertEquals(expected, text)
    }
}
