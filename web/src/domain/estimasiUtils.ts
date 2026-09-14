import { absMinToTimeStr, jamKeShiftAbs, nowAbsMin } from "./format";
import { parseDurasi, parseJam } from "./parse";
import type { Estimasi, MesinData, MesinTipe } from "./types";

export type UrgencyLevel = "CALM" | "SOON" | "IMMINENT" | "OVERDUE";

export function urgencyLevel(remainingMin: number): UrgencyLevel {
  if (remainingMin > 30) return "CALM";
  if (remainingMin > 10) return "SOON";
  if (remainingMin > 0) return "IMMINENT";
  return "OVERDUE";
}

export function sortedByNearest(estimasi: Record<string, Estimasi>): Estimasi[] {
  return Object.values(estimasi).sort((a, b) => a.estAbsMin - b.estAbsMin);
}

/** Mencari nomor mesin lain yang estimasi doff-nya berdekatan (selisih <= thresholdMin, default 5 menit)
 * dari mesin target — port 1:1 dari findClashingMachines di EstimasiUtils.kt Android. */
export function findClashingMachines(
  targetMcNo: string,
  allEstimasi: Estimasi[] | Record<string, Estimasi>,
  thresholdMin = 5,
): string[] {
  const list = Array.isArray(allEstimasi) ? allEstimasi : Object.values(allEstimasi);
  const target = list.find((e) => e.mcNo === targetMcNo);
  if (!target) return [];
  return list
    .filter((e) => e.mcNo !== targetMcNo && Math.abs(e.estAbsMin - target.estAbsMin) <= thresholdMin)
    .map((e) => e.mcNo);
}

/** Sisa menit efektif — kalau sedang dijeda, waktu dibekukan di titik jeda (tidak
 * terus berkurang mengikuti jam berjalan) sampai dilanjutkan lagi. */
export function effectiveRemaining(e: Estimasi, nowAbs: number): number {
  if (e.pausedAtAbsMin !== null && e.pausedAtAbsMin !== undefined) {
    return e.estAbsMin - e.pausedAtAbsMin;
  }
  return e.estAbsMin - nowAbs;
}

export function partitionSegeraMenunggu(sorted: Estimasi[], nowAbs: number): [Estimasi[], Estimasi[]] {
  const segera: Estimasi[] = [];
  const menunggu: Estimasi[] = [];
  for (const e of sorted) {
    if (effectiveRemaining(e, nowAbs) <= 0) segera.push(e);
    else menunggu.push(e);
  }
  return [segera, menunggu];
}

/** Mesin tunggal yang paling butuh perhatian sekarang: paling lama overdue, kalau
 * tidak ada yang overdue baru yang paling dekat waktunya. */
export function nearestUpcoming(estimasi: Record<string, Estimasi>, nowAbs: number): Estimasi | null {
  const [segera, menunggu] = partitionSegeraMenunggu(sortedByNearest(estimasi), nowAbs);
  return segera[0] ?? menunggu[0] ?? null;
}

/** Jeda minimum (menit) antar-dua estimasi berurutan di daftar "Menunggu" supaya
 * dianggap "boleh istirahat" — sama seperti BREAK_GAP_THRESHOLD_MIN di Android. */
export const BREAK_GAP_THRESHOLD_MIN = 30;

export interface EstimasiFieldHint {
  label: string;
  example: string;
}

/** Port 1:1 dari estimasiFieldHint di EstimasiUtils.kt — label & contoh field terpandu
 * beradaptasi dengan tipe mesin yang ditekan. */
export function estimasiFieldHint(tipe: MesinTipe): EstimasiFieldHint {
  switch (tipe) {
    case "TAPPET":
    case "CAM":
      return { label: "Sisa waktu (menit)", example: "45" };
    case "D405":
      return { label: "Yard sudah berjalan", example: "280" };
    case "D408":
      return { label: "Bacaan jam counter", example: "12.30" };
  }
}

export function selisihKoreksiD408(waktuAktualMin: number, bacaanCounterMin: number): number {
  const raw = waktuAktualMin - bacaanCounterMin;
  if (raw < -720) return raw + 1440;
  if (raw > 720) return raw - 1440;
  return raw;
}

/** Preview "≈ jam" langsung di dialog terpandu, memakai rumus murni yang identik dengan
 * yang dipakai commands.ts saat submit — supaya yang dipratinjau di sini dijamin sama
 * dengan yang benar-benar tersimpan. Port 1:1 dari previewEstimasi di GuidedEstimasiSheet.kt. */
export function previewEstimasi(tipe: MesinTipe, raw: string, mesin: MesinData | null): string | null {
  if (raw.trim() === "") return null;
  const now = nowAbsMin();
  switch (tipe) {
    case "TAPPET":
    case "CAM": {
      const sisa = parseDurasi(raw);
      return sisa !== null ? absMinToTimeStr(now + sisa) : null;
    }
    case "D405": {
      const target = mesin?.targetYard;
      const speed = mesin?.speed;
      const yardBerjalan = parseFloat(raw.trim().replace(/[yY]+$/, "").replace(",", "."));
      if (!Number.isNaN(yardBerjalan) && target != null && speed != null && speed > 0) {
        const sisaMin = Math.round((target - yardBerjalan) / speed);
        return absMinToTimeStr(now + sisaMin);
      }
      return null;
    }
    case "D408": {
      const koreksi = mesin?.koreksi;
      const jamMin = parseJam(raw);
      if (jamMin !== null && koreksi != null) {
        return absMinToTimeStr(jamKeShiftAbs(jamMin) + Math.round(koreksi));
      }
      return null;
    }
  }
}
