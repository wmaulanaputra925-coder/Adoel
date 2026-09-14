package com.jekael.adoel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Logika radar (urutan/urgensi) dan rumus estimasi murni per tipe mesin — dipakai bersama oleh
 * MainScreen, widget, dan DoffViewModel, jadi diuji langsung di sini secara numerik. */
class EstimasiUtilsTest {

    private val wib = TimeZone.getTimeZone("Asia/Jakarta")

    private fun epochMin(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(wib).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 60000L

    private fun est(mcNo: String, estAbsMin: Long) = Estimasi(mcNo, estAbsMin, startAbsMin = 0)

    // ---- urgencyLevel: batas 30/10/0 menit ----

    @Test
    fun urgencyLevelBoundaries() {
        assertEquals(UrgencyLevel.CALM, urgencyLevel(31))
        assertEquals(UrgencyLevel.SOON, urgencyLevel(30))
        assertEquals(UrgencyLevel.SOON, urgencyLevel(11))
        assertEquals(UrgencyLevel.IMMINENT, urgencyLevel(10))
        assertEquals(UrgencyLevel.IMMINENT, urgencyLevel(1))
        assertEquals(UrgencyLevel.OVERDUE, urgencyLevel(0))
        assertEquals(UrgencyLevel.OVERDUE, urgencyLevel(-45))
    }

    // ---- urutan & partisi radar ----

    @Test
    fun sortedByNearestOrdersByEstimateAscending() {
        val sorted = sortedByNearest(
            mapOf("a" to est("a", 300), "b" to est("b", 100), "c" to est("c", 200)),
        )
        assertEquals(listOf("b", "c", "a"), sorted.map { it.mcNo })
    }

    @Test
    fun partitionSplitsOverdueFromUpcoming() {
        val sorted = listOf(est("late", 90), est("due", 100), est("soon", 110))
        val (segera, menunggu) = partitionSegeraMenunggu(sorted, nowAbs = 100)
        assertEquals(listOf("late", "due"), segera.map { it.mcNo })
        assertEquals(listOf("soon"), menunggu.map { it.mcNo })
    }

    @Test
    fun findClashingMachinesUsesAbsoluteFiveMinuteThresholdAndExcludesTarget() {
        val estimasi = listOf(
            est("target", 100),
            est("before", 95),
            est("after", 105),
            est("outside", 106),
        )

        assertEquals(listOf("before", "after"), findClashingMachines("target", estimasi))
        assertEquals(emptyList<String>(), findClashingMachines("missing", estimasi))
    }

    @Test
    fun nearestUpcomingPrefersEarliestOverdueThenSoonest() {
        val map = mapOf("late" to est("late", 90), "soon" to est("soon", 110))
        assertEquals("late", nearestUpcoming(map, nowAbs = 100)?.mcNo)
        assertEquals("soon", nearestUpcoming(mapOf("soon" to est("soon", 110)), nowAbs = 100)?.mcNo)
        assertNull(nearestUpcoming(emptyMap(), nowAbs = 100))
    }

    // ---- rumus per tipe mesin ----

    @Test
    fun sisaMenitD405RoundsRemainingYardOverSpeed() {
        // (303 − 280) / 0.158 = 145.57 → dibulatkan 146 menit.
        assertEquals(146, sisaMenitD405(303.0, 280.0, 0.158))
    }

    @Test
    fun estAbsD408AddsKoreksiToCounterReading() {
        // Sekarang 13.00; counter menunjukkan 12.30 (masa lalu < 12 jam, hari yang sama);
        // koreksi mesin 18 menit → estimasi = 12.48 hari itu juga.
        val now = epochMin(2026, 1, 15, 13, 0)
        assertEquals(
            epochMin(2026, 1, 15, 12, 48),
            estAbsD408(jamCounterMin = 12 * 60 + 30, koreksiMin = 18.0, nowEpochMin = now, zone = wib),
        )
    }

    @Test
    fun resolveYardTokenDeltaVsAbsolute() {
        assertEquals(308.0, resolveYardToken(isDelta = true, value = 5.0, standardYard = 303.0), 0.0)
        assertEquals(295.0, resolveYardToken(isDelta = false, value = 295.0, standardYard = 303.0), 0.0)
        // Delta tanpa target standar jatuh ke nilai apa adanya.
        assertEquals(5.0, resolveYardToken(isDelta = true, value = 5.0, standardYard = null), 0.0)
    }
}
