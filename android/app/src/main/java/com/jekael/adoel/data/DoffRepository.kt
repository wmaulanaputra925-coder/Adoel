package com.jekael.adoel.data

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jekael.adoel.notification.NotificationHelper
import com.jekael.adoel.widget.AdoelWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("adoel_v5")

private val STATE_KEY = stringPreferencesKey("state_v2")

private data class SerialState(
    val db: Map<String, SerialMesin>?,
    val estimasi: Map<String, SerialEstimasi>?,
    val aktual: List<SerialAktual>?,
    val nextId: Int?,
    val themeMode: String?,
    val history: List<SerialShiftRecord>?,
    val nextShiftId: Int?,
    val onboardingSeen: Boolean?,
    val keteranganShortcuts: List<String>? = null,
    val corakShortcuts: List<String>? = null,
)

private data class SerialMesin(
    val tipe: String?,
    val corak: String?,
    val targetYard: Double?,
    val speed: Double?,
    val koreksi: Double?,
    val isActive: Boolean? = true,
)

private data class SerialEstimasi(
    val mcNo: String?,
    val estAbsMin: Long?,
    val startAbsMin: Long?,
    val corakOverride: String?,
    val yardOverride: Double?,
    // Null on data written before Jeda existed (Gson leaves it null on old data) — same "never
    // paused" default as a fresh Estimasi.
    val pausedAtAbsMin: Long? = null,
)

private data class SerialAktual(
    val id: Int?,
    val mcNo: String?,
    val jam: String?,
    val ket: String?,
    val corakOverride: String?,
    val customYard: Double?,
    val tsEpochMin: Long?,
)

private data class SerialShiftRecord(
    val id: Int?,
    val startedAtEpochMin: Long?,
    val endedAtEpochMin: Long?,
    val aktual: List<SerialAktual>?,
    val estimasiRemaining: Map<String, SerialEstimasi>?,
)

data class SyncEnvelope(
    val type: String?,
    val payload: String?,
    val part: Int? = null,
    val total: Int? = null,
)

private data class SyncPayload(
    val cDb: List<List<Any?>>? = null,
    val db: Map<String, SerialMesin>? = null,
    val estimasi: Map<String, SerialEstimasi>? = null,
    val aktual: List<SerialAktual>? = null,
)

/**
 * Kontrak penyimpanan state yang dipakai [com.jekael.adoel.viewmodel.DoffViewModel] — dipisah
 * dari [DoffRepository] supaya unit test bisa menyuntikkan fake in-memory dan benar-benar
 * menjalankan jalur persist (update/importJson) tanpa DataStore/Android framework.
 */
interface DoffStateStore {
    fun observeState(): Flow<DoffState>
    suspend fun update(debounceWidgetRefresh: Boolean = false, transform: (DoffState) -> DoffState): DoffState
    fun exportJson(state: DoffState): String
    suspend fun importJson(json: String): DoffState?
}

class DoffRepository private constructor(private val context: Context) : DoffStateStore {

    companion object {
        @Volatile private var instance: DoffRepository? = null

        /** Satu instance per proses — Gson, scope debounce widget, dan konteks aplikasi dibagi
         * oleh ViewModel, ketiga BroadcastReceiver, dan widget, alih-alih tiap alarm/render
         * widget membangun repository (plus Gson) baru. DataStore-nya sendiri memang sudah
         * process-wide (property delegate top-level), jadi ini murni dedup objek pendukung. */
        fun getInstance(context: Context): DoffRepository =
            instance ?: synchronized(this) {
                // applicationContext bisa null untuk Application() polos di unit test — fallback
                // ke context yang diberikan; jalur test tidak pernah menyentuh DataStore.
                instance ?: DoffRepository(context.applicationContext ?: context).also { instance = it }
            }
    }

    private val gson: Gson = GsonBuilder().create()

    private fun parseState(prefs: Preferences): DoffState {
        val persisted = prefs[STATE_KEY]
        if (persisted == null) return DoffState(db = buildDefaultDb(), onboardingSeen = false)
        return parseJson(persisted) ?: run {
            Log.e("DoffRepository", "State DataStore rusak — memakai fallback tanpa menimpa data tersimpan")
            DoffState(db = buildDefaultDb(), onboardingSeen = true)
        }
    }

    /** Parse a persisted or backup JSON snapshot into a [DoffState], or null if it is not a valid
     * Adoel backup (malformed JSON, wrong shape, or missing the machine database). */
    fun parseJson(json: String): DoffState? {
        return try {
            val serial = gson.fromJson(json, SerialState::class.java) ?: return null
            val serialDb = serial.db ?: return null
            if (serialDb.isEmpty()) return null
            DoffState(
                db = serialDb.mapNotNull { (mcNo, v) ->
                    if (v == null) return@mapNotNull null
                    MesinData(
                        tipe = runCatching { MesinTipe.valueOf(v.tipe ?: "") }.getOrElse {
                            // Silently coercing would hide data corruption AND silently switch the
                            // machine to TAPPET's estimation formula — at least leave a trace.
                            Log.w("DoffRepository", "Tipe mesin tak dikenal '${v.tipe}' di Mc $mcNo, fallback TAPPET")
                            MesinTipe.TAPPET
                        },
                        corak = v.corak ?: "-",
                        targetYard = v.targetYard,
                        speed = v.speed,
                        koreksi = v.koreksi,
                        isActive = v.isActive ?: true,
                    ).let { mcNo to it }
                }.toMap(),
                estimasi = (serial.estimasi ?: emptyMap()).mapNotNull { (mcNo, v) ->
                    val safeMcNo = v.mcNo ?: mcNo
                    val estAbsMin = v.estAbsMin ?: return@mapNotNull null
                    val startAbsMin = v.startAbsMin ?: return@mapNotNull null
                    safeMcNo to Estimasi(safeMcNo, estAbsMin, startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
                }.toMap(),
                aktual = dedupeIds(serial.aktual),
                nextId = maxOf(serial.nextId ?: 1, ((serial.aktual?.filterNotNull()?.maxOfOrNull { it.id ?: 0 }) ?: 0) + 1),
                themeMode = serial.themeMode ?: "SYSTEM",
                history = (serial.history ?: emptyList()).filterNotNull().map { r ->
                    ShiftRecord(
                        id = r.id ?: 0,
                        startedAtEpochMin = r.startedAtEpochMin ?: 0,
                        endedAtEpochMin = r.endedAtEpochMin ?: 0,
                        aktual = dedupeIds(r.aktual),
                        estimasiRemaining = (r.estimasiRemaining ?: emptyMap()).mapNotNull { (mcNo, v) ->
                            if (v == null) return@mapNotNull null
                            val safeMcNo = v.mcNo ?: mcNo
                            val estAbsMin = v.estAbsMin ?: return@mapNotNull null
                            val startAbsMin = v.startAbsMin ?: return@mapNotNull null
                            safeMcNo to Estimasi(safeMcNo, estAbsMin, startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
                        }.toMap(),
                    )
                },
                nextShiftId = serial.nextShiftId ?: 1,
                onboardingSeen = serial.onboardingSeen ?: true,
                keteranganShortcuts = serial.keteranganShortcuts,
                corakShortcuts = serial.corakShortcuts,
            )
        } catch (e: Exception) {
            // Null is the correct contract for the caller (invalid backup / corrupt blob), but a
            // systematic serialization break in the field is undiagnosable without this trace.
            Log.w("DoffRepository", "parseJson gagal — bukan snapshot Adoel yang valid", e)
            null
        }
    }

    private fun toAktualEntry(a: SerialAktual): AktualEntry =
        AktualEntry(a.id ?: 0, a.mcNo ?: "", a.jam ?: "", a.ket ?: "", a.corakOverride, a.customYard, a.tsEpochMin)

    /** Guarantees unique entry ids. Data written before writes became atomic could contain
     * duplicate ids from a race; LazyColumn crashes on duplicate keys, so reassign collisions. */
    private fun dedupeIds(raw: List<SerialAktual>?): List<AktualEntry> {
        if (raw.isNullOrEmpty()) return emptyList()
        val entries = raw.filterNotNull()
        if (entries.isEmpty()) return emptyList()
        var nextFree = (entries.maxOfOrNull { it.id ?: 0 } ?: 0) + 1
        val used = HashSet<Int>()
        return entries.map { a ->
            var id = a.id ?: nextFree++
            if (!used.add(id)) {
                id = nextFree++
                used.add(id)
            }
            toAktualEntry(a.copy(id = id))
        }
    }

    // DataStore reads can surface transient IOExceptions; recover to a default snapshot instead of
    // letting the exception cancel the collector (which would silently freeze all state updates).
    // Logged loudly because downstream this emission is indistinguishable from a fresh install —
    // without the trace, "all my data vanished" reports would have nothing to correlate against.
    private fun DataStore<Preferences>.safeData(): Flow<Preferences> =
        data.catch { e ->
            if (e is IOException) {
                Log.e("DoffRepository", "Gagal baca DataStore — emit state default sementara", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    // Deserialisasi seluruh blob (db 174 mesin + riwayat 30 hari) bukan pekerjaan murah — kedua
    // jalur baca di bawah memindahkannya ke Dispatchers.Default supaya tidak pernah berjalan di
    // main thread (kolektor observeState di ViewModel berjalan di Main).
    suspend fun load(): DoffState = withContext(Dispatchers.Default) {
        parseState(context.dataStore.safeData().first())
    }

    /** Reactive state — reflects any write, including ones from outside this ViewModel/process
     * lifecycle (e.g. the notification action button). */
    override fun observeState(): Flow<DoffState> =
        context.dataStore.safeData().map(::parseState).flowOn(Dispatchers.Default)

    private fun toSerialAktual(a: AktualEntry): SerialAktual =
        SerialAktual(a.id, a.mcNo, a.jam, a.ket, a.corakOverride, a.customYard, a.tsEpochMin)

    private fun serialize(state: DoffState): String {
        val serial = SerialState(
            db = state.db.mapValues { (_, v) ->
                SerialMesin(v.tipe.name, v.corak, v.targetYard, v.speed, v.koreksi, v.isActive)
            },
            estimasi = state.estimasi.mapValues { (_, v) ->
                SerialEstimasi(v.mcNo, v.estAbsMin, v.startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
            },
            aktual = state.aktual.map(::toSerialAktual),
            nextId = state.nextId,
            themeMode = state.themeMode,
            history = state.history.map { r ->
                SerialShiftRecord(
                    id = r.id,
                    startedAtEpochMin = r.startedAtEpochMin,
                    endedAtEpochMin = r.endedAtEpochMin,
                    aktual = r.aktual.map(::toSerialAktual),
                    estimasiRemaining = r.estimasiRemaining.mapValues { (_, v) ->
                        SerialEstimasi(v.mcNo, v.estAbsMin, v.startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
                    },
                )
            },
            nextShiftId = state.nextShiftId,
            onboardingSeen = state.onboardingSeen,
            keteranganShortcuts = state.keteranganShortcuts,
            corakShortcuts = state.corakShortcuts,
        )
        return gson.toJson(serial)
    }

    // Lives as long as this DoffRepository instance (the ViewModel's, for the app's foreground
    // lifetime) rather than any single caller's coroutine, so a debounced refresh isn't cancelled
    // just because the mutation that scheduled it already returned.
    private val widgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var widgetRefreshJob: Job? = null

    /**
     * Atomically read-modify-write the persisted state inside a single DataStore transaction.
     * DataStore serializes transactions, so concurrent callers (the ViewModel and the
     * notification action's [quickDoff]) can never overwrite each other based on a stale snapshot.
     * [transform] must be pure — it is applied to whatever the latest persisted state is, not to
     * the caller's own in-memory copy.
     *
     * [debounceWidgetRefresh] coalesces rapid back-to-back mutations (e.g. an operator recording
     * several doffs in quick succession while catching up at the end of a shift) into a single
     * widget re-render shortly after the last one, instead of one full Glance recomposition per
     * mutation. Left off (the default) for [quickDoff]'s notification-action path — that runs
     * inside a BroadcastReceiver's goAsync() window, which finishes right after this call returns,
     * so a delayed refresh scheduled there could be killed before it ever runs.
     */
    override suspend fun update(debounceWidgetRefresh: Boolean, transform: (DoffState) -> DoffState): DoffState {
        lateinit var next: DoffState
        context.dataStore.edit { prefs ->
            next = transform(parseState(prefs))
            prefs[STATE_KEY] = serialize(next)
        }
        // Centralized here (the one funnel every mutator passes through) instead of at each call
        // site, so no future mutator can forget to refresh the widget — and any failure is at
        // least visible in logcat instead of leaving the widget silently stale.
        if (debounceWidgetRefresh) {
            widgetRefreshJob?.cancel()
            widgetRefreshJob = widgetRefreshScope.launch {
                delay(400)
                refreshWidget()
            }
        } else {
            refreshWidget()
        }
        return next
    }

    private suspend fun refreshWidget() {
        try {
            AdoelWidget().updateAll(context)
        } catch (e: Exception) {
            Log.e("DoffRepository", "widget refresh failed", e)
        }
    }

    /** Records a plain doff (no keterangan/yard) for [mcNo] — used by the notification action
     * button. Runs as one atomic transaction so it can't clobber a concurrent in-app write. */
    suspend fun quickDoff(mcNo: String): Boolean {
        var recorded = false
        update { state ->
            val mesin = state.db[mcNo] ?: return@update state
            val prevEst = state.estimasi[mcNo]
            val jam = nowTimeStr()
            val effectiveCorak = prevEst?.corakOverride ?: mesin.corak
            val entry = AktualEntry(
                id = state.nextId,
                mcNo = mcNo,
                jam = jam,
                ket = jam,
                corakOverride = if (effectiveCorak != mesin.corak) effectiveCorak else null,
                customYard = null,
                tsEpochMin = nowAbsMin(),
            )
            recorded = true
            state.copy(
                nextId = state.nextId + 1,
                estimasi = state.estimasi - mcNo,
                aktual = listOf(entry) + state.aktual,
            )
        }
        return recorded
    }

    /** Full-state backup as a JSON string (machine db + estimasi + doff history + theme). */
    override fun exportJson(state: DoffState): String = serialize(state)

    /** QR payload for handing the active shift to another device. History is deliberately absent. */
    suspend fun prepareHandoverData(): String {
        val state = load()
        val nowAbs = nowAbsMin()
        val shiftEndAbs = currentShiftStartAbsMin(nowAbs) + 480L
        val nextShiftEst = state.estimasi.filter { it.value.estAbsMin > shiftEndAbs }
        val targetEst = if (nextShiftEst.isNotEmpty()) nextShiftEst else state.estimasi
        val machineNos = targetEst.keys

        val cDbList = machineNos.mapNotNull { mcNo ->
            val m = state.db[mcNo] ?: return@mapNotNull null
            listOf<Any?>(mcNo, m.tipe.name, m.corak, m.targetYard, m.speed, m.koreksi, m.isActive)
        }
        val payload = SyncPayload(
            cDb = cDbList,
            estimasi = targetEst.mapValues { (_, v) ->
                SerialEstimasi(v.mcNo, v.estAbsMin, v.startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
            },
            aktual = emptyList(),
        )
        return encodeSyncEnvelope("HANDOVER", payload)
    }

    /** QR payload containing machine master database with scoping options. */
    suspend fun prepareMasterDbData(scope: String = "CUSTOMIZED_ONLY"): String {
        val state = load()
        val cDbList = mutableListOf<List<Any?>>()
        for ((mcNo, m) in state.db) {
            val num = mcNo.toIntOrNull()
            if (scope == "RANGE_1_30" && (num == null || num !in 1..30)) continue
            if (scope == "RANGE_31_60" && (num == null || num !in 31..60)) continue

            val isCustomized = (m.corak.trim().isNotEmpty() && m.corak != "-") ||
                    m.targetYard != null || m.speed != null || (m.koreksi != null && m.koreksi != 0.0)

            if (scope == "CUSTOMIZED_ONLY" && !isCustomized) continue

            cDbList.add(listOf<Any?>(mcNo, m.tipe.name, m.corak, m.targetYard, m.speed, m.koreksi, m.isActive))
        }

        if (cDbList.isEmpty() && scope == "CUSTOMIZED_ONLY") {
            for ((mcNo, m) in state.db.entries.take(30)) {
                cDbList.add(listOf<Any?>(mcNo, m.tipe.name, m.corak, m.targetYard, m.speed, m.koreksi, m.isActive))
            }
        }

        val part = when (scope) {
            "RANGE_1_30" -> 1
            "RANGE_31_60" -> 2
            else -> null
        }
        val total = if (part != null) 2 else null

        val payload = SyncPayload(cDb = cDbList)
        return encodeSyncEnvelope("MASTER_DB", payload, part, total)
    }

    /** Decompresses and merges a scanned QR payload, then restores every active notification. */
    suspend fun processScannedQr(data: String, context: Context): Pair<DoffState?, String> {
        return try {
            val envelope = gson.fromJson(data, SyncEnvelope::class.java)
                ?: return Pair(null, "Format QR tidak valid")
            val type = envelope.type ?: return Pair(null, "Format QR tidak valid")
            if (type != "HANDOVER" && type != "MASTER_DB") return Pair(null, "Format QR tidak dikenali")
            val encodedPayload = envelope.payload ?: return Pair(null, "Data QR kosong")
            val payload = gson.fromJson(decodeSyncPayload(encodedPayload), SyncPayload::class.java)
                ?: return Pair(null, "Gagal membaca isi QR")

            var message = "Sinkronisasi berhasil ✓"
            val nextState = update { current ->
                when (type) {
                    "HANDOVER" -> {
                        val incomingDb = parseMesinMap(payload)
                        val incomingAktual = (payload.aktual ?: emptyList()).filterNotNull().map(::toAktualEntry)
                        val mergedAktual = (current.aktual + incomingAktual).distinctBy {
                            listOf(it.id, it.mcNo, it.jam, it.ket, it.corakOverride, it.customYard, it.tsEpochMin)
                        }
                        val dedupedAktual = dedupeIds(mergedAktual.map(::toSerialAktual))
                        val incomingEst = (payload.estimasi ?: emptyMap()).mapNotNull { (mcNo, v) ->
                            if (v == null) return@mapNotNull null
                            val safeMcNo = v.mcNo ?: mcNo
                            val estAbsMin = v.estAbsMin ?: return@mapNotNull null
                            val startAbsMin = v.startAbsMin ?: return@mapNotNull null
                            safeMcNo to Estimasi(safeMcNo, estAbsMin, startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
                        }.toMap()

                        message = "Oper Shift berhasil diimpor (${incomingEst.size} estimasi) ✓"
                        current.copy(
                            db = current.db + incomingDb,
                            estimasi = current.estimasi + incomingEst,
                            aktual = dedupedAktual,
                            nextId = maxOf(current.nextId, (dedupedAktual.maxOfOrNull { it.id } ?: 0) + 1),
                        )
                    }
                    "MASTER_DB" -> {
                        val incomingDb = parseMesinMap(payload)
                        message = if (envelope.part != null && envelope.total != null) {
                            "Bagian ${envelope.part}/${envelope.total} (${incomingDb.size} mesin) berhasil diimpor ✓"
                        } else {
                            "Berhasil menyinkronkan ${incomingDb.size} data mesin ✓"
                        }
                        current.copy(db = current.db + incomingDb)
                    }
                    else -> current
                }
            }
            NotificationHelper.rescheduleAll(context, nextState.estimasi.values)
            Pair(nextState, message)
        } catch (e: Exception) {
            Log.w("DoffRepository", "processScannedQr gagal — QR tidak valid", e)
            Pair(null, "⚠ Format QR Sync tidak valid")
        }
    }

    private fun encodeSyncEnvelope(type: String, payload: SyncPayload, part: Int? = null, total: Int? = null): String {
        val raw = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(raw) }
            output.toByteArray()
        }
        return gson.toJson(SyncEnvelope(type, Base64.encodeToString(compressed, Base64.NO_WRAP), part, total))
    }

    private fun decodeSyncPayload(encoded: String): String {
        val compressed = Base64.decode(encoded, Base64.NO_WRAP)
        return GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun mergeHandover(current: DoffState, payload: SyncPayload): DoffState {
        val incomingAktual = (payload.aktual ?: emptyList()).filterNotNull().map(::toAktualEntry)
        val mergedAktual = (current.aktual + incomingAktual).distinctBy {
            listOf(it.id, it.mcNo, it.jam, it.ket, it.corakOverride, it.customYard, it.tsEpochMin)
        }
        val dedupedAktual = dedupeIds(mergedAktual.map(::toSerialAktual))
        return current.copy(
            db = current.db + parseMesinMap(payload),
            estimasi = current.estimasi + (payload.estimasi ?: emptyMap()).mapNotNull { (mcNo, v) ->
                if (v == null) return@mapNotNull null
                val safeMcNo = v.mcNo ?: mcNo
                val estAbsMin = v.estAbsMin ?: return@mapNotNull null
                val startAbsMin = v.startAbsMin ?: return@mapNotNull null
                safeMcNo to Estimasi(safeMcNo, estAbsMin, startAbsMin, v.corakOverride, v.yardOverride, v.pausedAtAbsMin)
            }.toMap(),
            aktual = dedupedAktual,
            nextId = maxOf(current.nextId, (dedupedAktual.maxOfOrNull { it.id } ?: 0) + 1),
        )
    }

    private fun parseMesinMap(payload: SyncPayload): Map<String, MesinData> {
        val result = mutableMapOf<String, MesinData>()
        if (!payload.cDb.isNullOrEmpty()) {
            for (item in payload.cDb) {
                if (item.isEmpty()) continue
                val mcNo = item.getOrNull(0)?.toString() ?: continue
                val tipeStr = item.getOrNull(1)?.toString() ?: "TAPPET"
                val corak = item.getOrNull(2)?.toString() ?: "-"
                val targetYard = (item.getOrNull(3) as? Number)?.toDouble()
                val speed = (item.getOrNull(4) as? Number)?.toDouble()
                val koreksi = (item.getOrNull(5) as? Number)?.toDouble()
                val isActive = when (val activeVal = item.getOrNull(6)) {
                    is Boolean -> activeVal
                    is Number -> activeVal.toInt() != 0
                    else -> true
                }
                val tipe = runCatching { MesinTipe.valueOf(tipeStr) }.getOrDefault(MesinTipe.TAPPET)
                result[mcNo] = MesinData(tipe, corak, targetYard, speed, koreksi, isActive)
            }
            return result
        }

        if (!payload.db.isNullOrEmpty()) {
            for ((mcNo, v) in payload.db) {
                if (v == null) continue
                val tipe = runCatching { MesinTipe.valueOf(v.tipe ?: "") }.getOrDefault(MesinTipe.TAPPET)
                result[mcNo] = MesinData(
                    tipe = tipe,
                    corak = v.corak ?: "-",
                    targetYard = v.targetYard,
                    speed = v.speed,
                    koreksi = v.koreksi,
                    isActive = v.isActive ?: true,
                )
            }
        }
        return result
    }

    /** Restore state from a backup produced by [exportJson]. Returns the imported state, or null
     * if the JSON is not a valid Adoel backup. Writes atomically like any other mutation. */
    override suspend fun importJson(json: String): DoffState? {
        val parsed = parseJson(json) ?: return null
        update { parsed }
        return parsed
    }
}
