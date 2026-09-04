// Port 1:1 dari shareHistory (DoffingSection.kt) dan shareShift (StatistikScreen.kt).
import { absMinToTimeStr, currentShiftStartAbsMin, formatYard, nowAbsMin, shiftNumberForEpochMin } from "./format";
import { sortedByNearest } from "./estimasiUtils";
import { sortAktualChronological } from "./aktualOrder";
import type { DoffState, MesinData, ShiftRecord } from "./types";

const DIVIDER = "─".repeat(16);

function dateStrNow(): string {
  const d = new Date();
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`;
}

function formatShiftDate(epochMin: number): string {
  const d = new Date(epochMin * 60000);
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`;
}

function pad2(n: number): string {
  return n.toString().padStart(2, "0");
}

/** "10.00(HB)" → "10.00 (HB)" — ket selalu "$jam" atau "$jam($extra)" tanpa spasi, jadi ini aman
 * dipakai apa adanya untuk kerapian tampilan di WhatsApp. */
function formatKetDisplay(ket: string): string {
  return ket.replace("(", " (");
}

function formatAktualLine(index: number, mcNo: string, corak: string, yard: number | null | undefined, ket: string): string {
  const yardSuffix = yard != null ? ` (${formatYard(yard)}y)` : "";
  return `${index + 1}. *Mc ${mcNo}* – ${corak}${yardSuffix} · ${formatKetDisplay(ket)}`;
}

function formatEstimasiLine(mcNo: string, corak: string, yard: number | null | undefined, estAbsMin: number): string {
  const yardSuffix = yard != null ? ` (${formatYard(yard)}y)` : "";
  return `• *Mc ${mcNo}* – ${corak}${yardSuffix} · Est. ${absMinToTimeStr(estAbsMin)}`;
}

/** Teks ringkasan siap-bagikan untuk daftar Doffing shift berjalan — termasuk daftar
 * mesin yang masih berjalan (estimasi aktif), karena rekan yang baca pesan ini di
 * lantai produksi juga perlu tahu mesin mana yang belum di-doff. */
export function shareHistoryText(state: DoffState): string {
  const dateStr = dateStrNow();
  const aktualChrono = sortAktualChronological(state.aktual, nowAbsMin());
  const lines = aktualChrono.map((a, i) => {
    const mesin = state.db[a.mcNo];
    const corak = a.corakOverride ?? mesin?.corak ?? "—";
    const yard = a.customYard ?? mesin?.targetYard;
    return formatAktualLine(i, a.mcNo, corak, yard, a.ket);
  });
  const shiftEnd = currentShiftStartAbsMin(nowAbsMin()) + 8 * 60;
  const estimasiBerjalan = sortedByNearest(state.estimasi).filter((est) => est.estAbsMin <= shiftEnd);
  const estimasiOperan = sortedByNearest(state.estimasi).filter((est) => est.estAbsMin > shiftEnd);
  const berjalan = estimasiBerjalan.map((est) => {
    const mesin = state.db[est.mcNo];
    const corak = est.corakOverride ?? mesin?.corak ?? "—";
    const yard = est.yardOverride ?? mesin?.targetYard;
    return formatEstimasiLine(est.mcNo, corak, yard, est.estAbsMin);
  });
  const operan = estimasiOperan.map((est) => {
    const mesin = state.db[est.mcNo];
    const corak = est.corakOverride ?? mesin?.corak ?? "—";
    const yard = est.yardOverride ?? mesin?.targetYard;
    return formatEstimasiLine(est.mcNo, corak, yard, est.estAbsMin);
  });
  const selesaiCount = state.aktual.length;
  const berjalanCount = berjalan.length;
  const totalLine =
    berjalanCount > 0
      ? `📊 *Total: ${selesaiCount} selesai + ${berjalanCount} berjalan = ${selesaiCount + berjalanCount} mc*`
      : `📊 *Total: ${selesaiCount} doff*`;
  const berjalanBlock = berjalan.length > 0 ? `\n\n⏳ *Sedang Berjalan (${berjalanCount} mc)*\n${berjalan.join("\n")}` : "";
  const operanBlock = operan.length > 0 ? `\n\n📤 *Operan Shift Berikutnya (${operan.length} mc)*\n${operan.join("\n")}` : "";
  return `*UPDATE DOFFING AKTIF*\n📅 ${dateStr}\n${DIVIDER}\n\n✅ *Selesai (${selesaiCount} doff)*\n${lines.join(
    "\n",
  )}${berjalanBlock}${operanBlock}\n${DIVIDER}\n${totalLine}`;
}

/** Teks ringkasan siap-bagikan untuk satu shift yang sudah diarsipkan. */
export function shareShiftText(shift: ShiftRecord, db: Record<string, MesinData>): string {
  const shiftNo = shiftNumberForEpochMin(shift.startedAtEpochMin);
  const dateStr = formatShiftDate(shift.startedAtEpochMin);
  const chrono = sortAktualChronological(shift.aktual, shift.startedAtEpochMin + 240);
  const lines = chrono.map((a, i) => {
    const mesin = db[a.mcNo];
    const corak = a.corakOverride ?? mesin?.corak ?? "—";
    const yard = a.customYard ?? mesin?.targetYard;
    return formatAktualLine(i, a.mcNo, corak, yard, a.ket);
  });
  return `*LAPORAN SHIFT ${shiftNo}*\n📅 ${dateStr}\n${DIVIDER}\n\n✅ *Selesai (${shift.aktual.length} doff)*\n${lines.join(
    "\n",
  )}\n${DIVIDER}\n📊 *Total: ${shift.aktual.length} doff*`;
}

/** Pakai Web Share API kalau tersedia (umumnya di HP), fallback salin ke clipboard. */
export async function shareOrCopy(text: string, title: string): Promise<"shared" | "copied" | "failed"> {
  if (navigator.share) {
    try {
      await navigator.share({ text, title });
      return "shared";
    } catch {
      // Pengguna membatalkan share sheet — bukan kegagalan nyata, jangan fallback ke clipboard.
      return "failed";
    }
  }
  try {
    await navigator.clipboard.writeText(text);
    return "copied";
  } catch {
    return "failed";
  }
}
