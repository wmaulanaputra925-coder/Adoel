package com.jekael.adoel.data

import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compose-free "which machine is next" logic, shared by MainScreen's RadarCard list and the
 * home-screen widget (which runs in its own Glance composition and can't reuse MainScreen's
 * inline Compose state/remember blocks). Juga rumah bagi rumus estimasi murni per tipe mesin,
 * dipisah dari tokenizing/mutasi state di DoffViewModel supaya bisa diuji numerik langsung.
 */
enum class UrgencyLevel { CALM, SOON, IMMINENT, OVERDUE }

/** More than this many minutes remaining reads as "tenang" — no visual urgency at all. */
const val URGENCY_CALM_OVER_MIN = 30L

/** Between this and [URGENCY_CALM_OVER_MIN] minutes remaining is "segera" (amber). Below it,
 * down to zero, is "mendesak" (orange); at/past zero the card is overdue (red, pulsing). */
const val URGENCY_SOON_OVER_MIN = 10L

/** Shared "this needs attention right now" threshold: the reminder notification fires this many
 * minutes before the estimate (see NotificationHelper), and it lines up with the physical warning
 * light on the machines, which also turns on at 5 minutes remaining — so the radar card grows to
 * full width at the same instant, not a threshold invented separately for layout. */
const val REMINDER_LEAD_MIN = 5L

fun urgencyLevel(remainingMin: Long): UrgencyLevel = when {
    remainingMin > URGENCY_CALM_OVER_MIN -> UrgencyLevel.CALM
    remainingMin > URGENCY_SOON_OVER_MIN -> UrgencyLevel.SOON
    remainingMin > 0 -> UrgencyLevel.IMMINENT
    else -> UrgencyLevel.OVERDUE
}

fun sortedByNearest(estimasi: Map<String, Estimasi>): List<Estimasi> =
    estimasi.values.sortedBy { it.estAbsMin }

fun findClashingMachines(
    targetMcNo: String,
    allEstimasi: Collection<Estimasi>,
    thresholdMin: Long = 5L,
): List<String> {
    val targetEstimasi = allEstimasi.firstOrNull { it.mcNo == targetMcNo } ?: return emptyList()
    return allEstimasi
        .asSequence()
        .filter { it.mcNo != targetMcNo }
        .filter { abs(it.estAbsMin - targetEstimasi.estAbsMin) <= thresholdMin }
        .map { it.mcNo }
        .toList()
}

/** How many machines a clash run starting at [index] covers in this time-sorted [list] — walks
 * forward chaining consecutive pairs within [thresholdMin] of each other (so A-B-C can all group
 * together even if A and C themselves are more than [thresholdMin] apart). Assumes [index] is
 * really where a run starts; see [isClashRunStart]. */
fun clashRunLength(list: List<Estimasi>, index: Int, thresholdMin: Long = 5L): Int {
    var end = index
    while (end + 1 < list.size && abs(list[end + 1].estAbsMin - list[end].estAbsMin) <= thresholdMin) {
        end++
    }
    return end - index + 1
}

/** Whether [list]\[index\] is the first machine of a clash run — i.e., it doesn't clash with
 * whatever's right before it (or there's nothing before it), but it does clash with what's right
 * after it. Used to draw one group marker above a run of adjacent clashing cards instead of
 * repeating a per-card "Bentrok Mc X, Y" badge on every one of them. */
fun isClashRunStart(list: List<Estimasi>, index: Int, thresholdMin: Long = 5L): Boolean {
    val clashesWithPrev = index > 0 && abs(list[index].estAbsMin - list[index - 1].estAbsMin) <= thresholdMin
    if (clashesWithPrev) return false
    return index + 1 < list.size && abs(list[index + 1].estAbsMin - list[index].estAbsMin) <= thresholdMin
}

/** Splits a time-sorted [list] into consecutive runs — every clash run (as [isClashRunStart]/
 * [clashRunLength] define one) becomes its own sublist, everything else stays a singleton sublist
 * of one — so a caller can render each clash run as one grouped unit (e.g. RadarSection's
 * ClashGroupCard) without re-deriving run boundaries itself. */
fun groupClashRuns(list: List<Estimasi>, thresholdMin: Long = 5L): List<List<Estimasi>> {
    val groups = mutableListOf<List<Estimasi>>()
    var i = 0
    while (i < list.size) {
        val runLength = if (isClashRunStart(list, i, thresholdMin)) clashRunLength(list, i, thresholdMin) else 1
        groups.add(list.subList(i, i + runLength))
        i += runLength
    }
    return groups
}

/** How far ahead of an upcoming clash run's due time the header's "bersiap" warning fires. */
const val GASPOL_PREPARE_LEAD_MIN = 10L

/** Machine count of the nearest clash run about to come due within [leadMin] minutes (but not yet
 * due), or null when no such run exists right now. [list] must already be sorted ascending by
 * estAbsMin (see [sortedByNearest]) — since it's sorted, the first qualifying run found scanning
 * forward is already the soonest one. Used to warn the operator a beat before a pile-up of
 * machines actually needs juggling, not only once it's already underway. */
fun upcomingClashMachineCount(
    list: List<Estimasi>,
    nowAbs: Long,
    leadMin: Long = GASPOL_PREPARE_LEAD_MIN,
    thresholdMin: Long = 5L,
): Int? {
    for (i in list.indices) {
        if (!isClashRunStart(list, i, thresholdMin)) continue
        val runLength = clashRunLength(list, i, thresholdMin)
        val soonEnough = (i until i + runLength).any { idx ->
            list[idx].effectiveRemaining(nowAbs) in 1..leadMin
        }
        if (soonEnough) return runLength
    }
    return null
}

/** Minutes remaining as the operator should actually see it — frozen at whatever it was the
 * moment Jeda was pressed (see [Estimasi.pausedAtAbsMin]) instead of continuing to count down
 * against wall-clock time while paused, so a long pause doesn't quietly push a card into Segera/
 * OVERDUE on its own. Live nowAbs-based countdown resumes the instant Lanjutkan shifts estAbsMin
 * forward and clears pausedAtAbsMin (DoffViewModel.resumeEstimasi). */
fun Estimasi.effectiveRemaining(nowAbs: Long): Long =
    if (pausedAtAbsMin != null) estAbsMin - pausedAtAbsMin else estAbsMin - nowAbs

fun partitionSegeraMenunggu(sorted: List<Estimasi>, nowAbs: Long): Pair<List<Estimasi>, List<Estimasi>> =
    sorted.partition { it.effectiveRemaining(nowAbs) <= 0 }

/** The single machine most in need of attention right now: earliest overdue, else soonest upcoming. */
fun nearestUpcoming(estimasi: Map<String, Estimasi>, nowAbs: Long): Estimasi? {
    val (segera, menunggu) = partitionSegeraMenunggu(sortedByNearest(estimasi), nowAbs)
    return segera.firstOrNull() ?: menunggu.firstOrNull()
}

/** D405: sisa menit sampai doff dari bacaan yard berjalan — mesin menggulung [speedYardPerMin]
 * yard tiap menit, jadi sisa (target − berjalan) dibagi kecepatan. Pemanggil wajib menjamin
 * speed > 0 (DoffViewModel menolak input sebelum sampai sini). */
fun sisaMenitD405(targetYard: Double, yardBerjalan: Double, speedYardPerMin: Double): Int =
    ((targetYard - yardBerjalan) / speedYardPerMin).roundToInt()

/** D408: estimasi absolut dari bacaan jam pada counter mesin (bukan jam dinding!) plus menit
 * koreksi tetap per mesin. [nowEpochMin]/[zone] hanya seam untuk unit test. */
fun estAbsD408(
    jamCounterMin: Int,
    koreksiMin: Double,
    nowEpochMin: Long = nowAbsMin(),
    zone: TimeZone = TimeZone.getDefault(),
): Long = jamKeShiftAbs(jamCounterMin, nowEpochMin, zone) + koreksiMin.roundToInt()

/** Pengaturan > Mesin "Hitung Koreksi" helper untuk D408: selisih menit antara jam dinding
 * sebenarnya dan bacaan jam counter mesin pada saat yang sama — dipakai supaya operator tidak
 * perlu menghitung manual tiap kali mesin D408 disetel ulang (mis. jam sungguhan 12.48 vs counter
 * baca 12.30 -> koreksi +18). Dinormalisasi ke rentang terdekat (±720 menit, sama seperti
 * [jamKeShiftAbs]) supaya pembacaan yang melewati tengah malam tidak menghasilkan selisih besar
 * yang salah arah. */
fun selisihKoreksiD408(waktuAktualMin: Int, bacaanCounterMin: Int): Int {
    val raw = waktuAktualMin - bacaanCounterMin
    return when {
        raw < -720 -> raw + 1440
        raw > 720 -> raw - 1440
        else -> raw
    }
}

/** Token yard pada perintah DOFFING: "+5" berarti delta dari target standar (5 yard melewati
 * target), "295"/"295y" berarti nilai absolut. Delta tanpa target standar jatuh ke absolut. */
fun resolveYardToken(isDelta: Boolean, value: Double, standardYard: Double?): Double =
    if (isDelta && standardYard != null) standardYard + value else value

/** What a mode-ESTIMASI command's single value field means for a given machine type — the source
 * for Terpandu's guided field label ([GuidedEstimasiSheet]'s `FieldLabel`). Only D405 still reads
 * [example] (its own field is still free-typed); TAPPET/CAM and D408 pick their value off a
 * jam:menit wheel now, not a typed number, so their [label]s must describe what the wheel shows —
 * "(menit)" describing a bare number of minutes would be wrong once the field turned into a
 * jam:menit wheel display. */
data class EstimasiFieldHint(val label: String, val example: String)

fun estimasiFieldHint(tipe: MesinTipe): EstimasiFieldHint = when (tipe) {
    MesinTipe.TAPPET, MesinTipe.CAM -> EstimasiFieldHint("Sisa waktu", "45")
    MesinTipe.D405 -> EstimasiFieldHint("Yard sudah berjalan", "280")
    MesinTipe.D408 -> EstimasiFieldHint("Bacaan jam counter", "12.30")
}
