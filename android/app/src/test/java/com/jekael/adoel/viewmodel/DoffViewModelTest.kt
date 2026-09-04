package com.jekael.adoel.viewmodel

import android.app.Application
import com.jekael.adoel.data.DoffState
import com.jekael.adoel.data.DoffStateStore
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.MesinTipe
import com.jekael.adoel.data.ProsesResult
import com.jekael.adoel.data.nowAbsMin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Covers DoffViewModel end to end terhadap fake store in-memory: jalur optimistic-apply yang
 * sinkron (asersi tanpa memompa dispatcher) DAN jalur persist — transform yang sama diterapkan
 * ulang secara atomik lewat [DoffStateStore.update] begitu dispatcher dipompa
 * (advanceUntilIdle), lalu observeState merekonsiliasi state ViewModel ke hasil tersimpan.
 */
class DoffViewModelTest {

    /** Pengganti in-memory untuk DoffRepository — update menerapkan transform ke MutableStateFlow
     * sehingga jalur "tulis → emisi → rekonsiliasi" berjalan nyata tanpa DataStore/Android. */
    private class FakeStore : DoffStateStore {
        val persisted = MutableStateFlow(DoffState())
        override fun observeState(): Flow<DoffState> = persisted
        override suspend fun update(
            debounceWidgetRefresh: Boolean,
            transform: (DoffState) -> DoffState,
        ): DoffState {
            val next = transform(persisted.value)
            persisted.value = next
            return next
        }
        override fun exportJson(state: DoffState): String = ""
        override suspend fun importJson(json: String): DoffState? = null
    }

    private lateinit var dispatcher: TestDispatcher
    private lateinit var store: FakeStore
    private lateinit var viewModel: DoffViewModel

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        store = FakeStore()
        viewModel = DoffViewModel(Application(), store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun tappetEstimasiUsesRemainingMinutes() {
        viewModel.setMesin("29", MesinData(MesinTipe.TAPPET, "34758", targetYard = 303.0))
        val now = 1_000L
        val result = viewModel.prosesBarisKondisiMesin("29 45", now)

        assertTrue(result is ProsesResult.Ok)
        assertEquals(now + 45, (result as ProsesResult.Ok).estAbs)
        assertEquals(now + 45, viewModel.state.value.estimasi["29"]?.estAbsMin)
    }

    @Test
    fun unconfiguredMachineIsRejected() {
        // Mc 1 defaults to corak "-" (unconfigured) in buildDefaultDb.
        val result = viewModel.prosesBarisKondisiMesin("1 45", 1_000L)

        assertTrue(result is ProsesResult.Err)
    }

    @Test
    fun invalidMachineNumberIsRejected() {
        val result = viewModel.prosesBarisKondisiMesin("abc 45", 1_000L)

        assertTrue(result is ProsesResult.Err)
    }

    @Test
    fun d405EstimasiComputedFromRemainingYardOverSpeed() {
        // Mc 61 is D405/60357, targetYard 303.0, speed 0.158 in the default DB.
        val now = 1_000L
        val result = viewModel.prosesBarisKondisiMesin("61 280y", now)

        assertTrue(result is ProsesResult.Ok)
        val expectedSisaMin = ((303.0 - 280.0) / 0.158).roundToInt()
        assertEquals(now + expectedSisaMin, (result as ProsesResult.Ok).estAbs)
    }

    @Test
    fun d408EstimasiIsAccepted() {
        // Mc 79 is D408/60357, koreksi 18.0 — nilai numeriknya diuji di EstimasiUtilsTest
        // (estAbsD408 dengan clock suntikan); di sini cukup jalur perintahnya.
        val result = viewModel.prosesBarisKondisiMesin("79 12.30", nowAbsMin())

        assertTrue(result is ProsesResult.Ok)
    }

    @Test
    fun prosesBarisUmumRecordsDoffAndClearsEstimasi() {
        viewModel.prosesBarisKondisiMesin("29 45", 1_000L)
        assertNotNull(viewModel.state.value.estimasi["29"])

        val result = viewModel.prosesBarisUmum("29 HB")

        assertTrue(result is ProsesResult.Ok)
        assertNull(viewModel.state.value.estimasi["29"])
        assertEquals(1, viewModel.state.value.aktual.size)
        assertEquals("29", viewModel.state.value.aktual.first().mcNo)
    }

    @Test
    fun finishShiftArchivesAktualAndDeletesEstimasiWithoutArchivingIt() {
        // Mc 61's estimasi (D405) is unrelated to mc 29's doff below — it represents a machine
        // still mid-run when the operator taps "Selesai Shift". It should be deleted like the
        // rest of the shift log, but never show up inside the archived record itself — the
        // record is doffing history only, estimasiRemaining stays empty.
        viewModel.prosesBarisKondisiMesin("61 280y", 1_000L)
        viewModel.prosesBarisUmum("29 HB")
        assertEquals(1, viewModel.state.value.aktual.size)

        viewModel.finishShift()

        assertTrue(viewModel.state.value.aktual.isEmpty())
        assertTrue(viewModel.state.value.estimasi.isEmpty())
        assertEquals(1, viewModel.state.value.history.size)
        assertEquals(1, viewModel.state.value.history.first().aktual.size)
        assertTrue(viewModel.state.value.history.first().estimasiRemaining.isEmpty())
    }

    @Test
    fun finishShiftDeletesEstimasiWithoutArchivingWhenNoAktual() {
        // No doffing history at all — nothing to archive, so finishShift should just clear the
        // active estimasi without creating an empty-aktual shift record for it.
        viewModel.prosesBarisKondisiMesin("61 280y", 1_000L)

        viewModel.finishShift()

        assertTrue(viewModel.state.value.estimasi.isEmpty())
        assertTrue(viewModel.state.value.history.isEmpty())
    }

    @Test
    fun persistedWriteAppliesSameTransformAsOptimisticApply() {
        viewModel.prosesBarisKondisiMesin("29 45", 1_000L)
        // Sebelum dipompa, tulis-persist belum berjalan sama sekali.
        assertNull(store.persisted.value.estimasi["29"])

        dispatcher.scheduler.advanceUntilIdle()

        // Transform yang sama diterapkan ke store, dan hasil rekonsiliasi observeState di
        // ViewModel identik dengan yang tersimpan — tidak ada divergensi optimistic vs persist.
        assertEquals(1_045L, store.persisted.value.estimasi["29"]?.estAbsMin)
        assertEquals(store.persisted.value, viewModel.state.value)
    }

    @Test
    fun persistedPathSurvivesChainedMutations() {
        viewModel.prosesBarisKondisiMesin("29 45", 1_000L)
        viewModel.prosesBarisUmum("29 HB")
        viewModel.finishShift()

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(store.persisted.value.aktual.isEmpty())
        assertTrue(store.persisted.value.estimasi.isEmpty())
        assertEquals(1, store.persisted.value.history.size)
        assertEquals(store.persisted.value, viewModel.state.value)
    }

    @Test
    fun resetDbShowsOnboardingAgain() {
        store.persisted.value = DoffState(onboardingSeen = true)

        viewModel.resetDb()

        assertTrue(!viewModel.state.value.onboardingSeen)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!store.persisted.value.onboardingSeen)
        assertEquals(store.persisted.value, viewModel.state.value)
    }
}
