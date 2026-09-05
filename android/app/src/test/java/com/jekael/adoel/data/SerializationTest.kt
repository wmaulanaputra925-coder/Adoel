package com.jekael.adoel.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak serialisasi DoffRepository: roundtrip exportJson→parseJson harus lossless, dan format
 * lama (field yang belum ada di versi sebelumnya) harus tetap terbaca dengan default yang benar.
 * Fixture golden di bawah adalah jaring pengaman untuk mirror Serial* — kalau ada field baru yang
 * lupa dipetakan di salah satu dari 5 titik edit (model/serial/serialize/parseJson/proguard),
 * salah satu test ini yang gagal, bukan backup pengguna di lapangan yang rusak diam-diam.
 *
 * parseJson/exportJson tidak menyentuh Context/DataStore, jadi cukup plain JUnit
 * (returnDefaultValues menangani Log.w di jalur fallback).
 */
class SerializationTest {

    private val repo = DoffRepository.getInstance(Application())

    private fun fullState(): DoffState = DoffState(
        db = mapOf(
            "29" to MesinData(MesinTipe.TAPPET, "34758", targetYard = 303.0),
            "61" to MesinData(MesinTipe.D405, "60357", targetYard = 303.0, speed = 0.158),
            "76" to MesinData(MesinTipe.CAM, "21242", targetYard = 165.0),
            "79" to MesinData(MesinTipe.D408, "60357", targetYard = 303.0, koreksi = 18.0),
        ),
        estimasi = mapOf(
            "29" to Estimasi("29", estAbsMin = 29_726_400, startAbsMin = 29_726_300),
            "61" to Estimasi("61", 29_726_500, 29_726_310, corakOverride = "99999", yardOverride = 250.0),
        ),
        aktual = listOf(
            AktualEntry(id = 7, mcNo = "76", jam = "13.49", ket = "13.49(HB)", customYard = 116.0, tsEpochMin = 29_726_329),
            AktualEntry(id = 6, mcNo = "29", jam = "12.04", ket = "12.04", tsEpochMin = null),
        ),
        nextId = 8,
        themeMode = "DARK",
        history = listOf(
            ShiftRecord(
                id = 3,
                startedAtEpochMin = 29_725_873,
                endedAtEpochMin = 29_726_441,
                aktual = listOf(AktualEntry(id = 5, mcNo = "61", jam = "10.00", ket = "10.00")),
                estimasiRemaining = mapOf("79" to Estimasi("79", 29_726_600, 29_726_000)),
                operatorNama = "Wahyu",
                operatorGrup = "B",
            ),
        ),
        nextShiftId = 4,
        onboardingSeen = false,
        operatorNama = "Wahyu",
        operatorGrup = "B",
    )

    @Test
    fun roundtripIsLossless() {
        val state = fullState()
        assertEquals(state, repo.parseJson(repo.exportJson(state)))
    }

    @Test
    fun legacyJsonWithoutNewerFieldsGetsDefaults() {
        // Bentuk blob dari versi sebelum themeMode/history/nextShiftId/onboardingSeen ada.
        val legacy = """{"db":{"29":{"tipe":"TAPPET","corak":"34758","targetYard":303.0}},
            "estimasi":{},"aktual":[],"nextId":5}"""

        val parsed = repo.parseJson(legacy)!!

        assertEquals("SYSTEM", parsed.themeMode)
        assertTrue(parsed.history.isEmpty())
        assertEquals(1, parsed.nextShiftId)
        assertTrue(parsed.onboardingSeen)
        assertEquals(303.0, parsed.db["29"]?.targetYard)
        // Backup sebelum ada pendataan operator: kosong, bukan null — teks bagikannya sekadar
        // tidak mencetak baris operator.
        assertEquals("", parsed.operatorNama)
        assertEquals("", parsed.operatorGrup)
    }

    @Test
    fun duplicateAktualIdsAreReassignedNotDropped() {
        // Data lama dari race pra-atomic-write bisa punya id ganda — LazyColumn crash pada key
        // duplikat, jadi parseJson wajib menjamin id unik TANPA membuang entri.
        val json = """{"db":{"29":{"tipe":"TAPPET","corak":"34758"}},"estimasi":{},
            "aktual":[
              {"id":5,"mcNo":"29","jam":"10.00","ket":"10.00"},
              {"id":5,"mcNo":"31","jam":"11.00","ket":"11.00"}
            ],"nextId":6}"""

        val parsed = repo.parseJson(json)!!

        assertEquals(2, parsed.aktual.size)
        assertNotEquals(parsed.aktual[0].id, parsed.aktual[1].id)
    }

    @Test
    fun staleNextIdIsRecomputedAboveMaxAktualId() {
        val json = """{"db":{"29":{"tipe":"TAPPET","corak":"34758"}},"estimasi":{},
            "aktual":[{"id":7,"mcNo":"29","jam":"10.00","ket":"10.00"}],"nextId":1}"""

        assertEquals(8, repo.parseJson(json)!!.nextId)
    }

    @Test
    fun unknownMesinTipeFallsBackToTappet() {
        val json = """{"db":{"29":{"tipe":"QUANTUM","corak":"34758"}},"estimasi":{},"aktual":[],"nextId":1}"""

        assertEquals(MesinTipe.TAPPET, repo.parseJson(json)!!.db["29"]?.tipe)
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(repo.parseJson("bukan json sama sekali"))
        assertNull(repo.parseJson("{}"))
        assertNull(repo.parseJson("""{"db":{},"estimasi":{},"aktual":[],"nextId":1}"""))
    }
}
