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

/** Baris "Operator: <nama> · Grup <grup>" untuk kepala pesan, atau null kalau belum didata.
 * Tidak pernah memakai penanda tebal: lihat catatan formatAktualLine. */
function formatOperatorLine(nama: string | undefined, grup: string | undefined): string | null {
  const n = (nama ?? "").trim();
  const g = (grup ?? "").trim();
  if (!n && !g) return null;
  if (!g) return `Operator: ${n}`;
  if (!n) return `Grup ${g}`;
  return `Operator: ${n} · Grup ${g}`;
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
function formatAktualLine(index: number, mcNo: string, corak: string, yard: number | null | undefined, ket: string): string {
  const yardSuffix = yard != null ? ` (${formatYard(yard)}y)` : "";
  return `${index + 1}. Mc ${mcNo} – ${corak}${yardSuffix} · ${formatKetDisplay(ket)}`;
}

function formatEstimasiLine(mcNo: string, corak: string, yard: number | null | undefined, estAbsMin: number): string {
  const yardSuffix = yard != null ? ` (${formatYard(yard)}y)` : "";
  return `• Mc ${mcNo} – ${corak}${yardSuffix} · Est. ${absMinToTimeStr(estAbsMin)}`;
}

/** Teks ringkasan siap-bagikan untuk daftar Doffing shift berjalan — termasuk daftar
 * mesin yang masih berjalan (estimasi aktif), karena rekan yang baca pesan ini di
 * lantai produksi juga perlu tahu mesin mana yang belum di-doff. */
export function shareHistoryText(state: DoffState): string {
  const dateStr = dateStrNow();
  const now = nowAbsMin();
  const shiftNo = shiftNumberForEpochMin(now);
  const aktualChrono = sortAktualChronological(state.aktual, now);
  const lines = aktualChrono.map((a, i) => {
    const mesin = state.db[a.mcNo];
    const corak = a.corakOverride ?? mesin?.corak ?? "—";
    const yard = a.customYard ?? mesin?.targetYard;
    return formatAktualLine(i, a.mcNo, corak, yard, a.ket);
  });
  // Batas blok sama persis dengan ShareText.kt: estimasi milik shift ini (mulai dari jam
  // mulainya) masuk "Sedang berjalan", yang jatuh setelah jam tutup masuk "Operan". Estimasi
  // yang sudah lewat dari shift sebelumnya tidak dicetak di mana pun — bukan pekerjaan shift ini.
  const shiftStart = currentShiftStartAbsMin(now);
  const shiftEnd = shiftStart + 8 * 60;
  const estimasiShiftIni = sortedByNearest(state.estimasi).filter((est) => est.estAbsMin >= shiftStart);
  const estimasiBerjalan = estimasiShiftIni.filter((est) => est.estAbsMin < shiftEnd);
  const estimasiOperan = estimasiShiftIni.filter((est) => est.estAbsMin >= shiftEnd);
  const formatEst = (est: (typeof estimasiBerjalan)[number]) => {
    const mesin = state.db[est.mcNo];
    const corak = est.corakOverride ?? mesin?.corak ?? "—";
    const yard = est.yardOverride ?? mesin?.targetYard;
    return formatEstimasiLine(est.mcNo, corak, yard, est.estAbsMin);
  };
  const berjalan = estimasiBerjalan.map(formatEst);
  const operan = estimasiOperan.map(formatEst);
  const selesaiCount = state.aktual.length;
  const berjalanCount = berjalan.length;
  const operanCount = operan.length;

  const head = [`*UPDATE DOFFING AKTIF*`, `${dateStr} · Shift ${shiftNo}`];
  const operatorLine = formatOperatorLine(state.operatorNama, state.operatorGrup);
  if (operatorLine) head.push(operatorLine);

  const blocks: string[] = [
    lines.length > 0 ? `*Selesai (${selesaiCount} doff)*\n${lines.join("\n")}` : `*Selesai (0 doff)*`,
  ];
  if (berjalanCount > 0) blocks.push(`*Sedang berjalan (${berjalanCount} mc)*\n${berjalan.join("\n")}`);
  if (operanCount > 0) blocks.push(`*Operan shift berikutnya (${operanCount} mc)*\n${operan.join("\n")}`);

  // Operan TIDAK ikut dijumlahkan: mesin itu baru akan di-doff setelah shift ini habis, jadi
  // memasukkannya ke total membuat shift ini terlihat mengerjakan pekerjaan shift berikutnya.
  // Jumlahnya tetap disebut di baris terpisah supaya rekan yang menerima operan tahu berapa.
  const totalShift = selesaiCount + berjalanCount;
  const totalLine =
    berjalanCount > 0
      ? `*Total shift ini: ${selesaiCount} selesai + ${berjalanCount} berjalan = ${totalShift} mc*`
      : `*Total shift ini: ${selesaiCount} doff*`;
  const foot = [totalLine];
  if (operanCount > 0) foot.push(`Operan ke shift berikutnya: ${operanCount} mc (di luar total)`);

  return `${head.join("\n")}\n${DIVIDER}\n\n${blocks.join("\n\n")}\n\n${DIVIDER}\n${foot.join("\n")}`;
}

/** Teks ringkasan siap-bagikan untuk satu shift yang sudah diarsipkan. */
export function shareShiftText(
  shift: ShiftRecord,
  db: Record<string, MesinData>,
  fallbackNama = "",
  fallbackGrup = "",
): string {
  const shiftNo = shiftNumberForEpochMin(shift.startedAtEpochMin);
  const dateStr = formatShiftDate(shift.startedAtEpochMin);
  const chrono = sortAktualChronological(shift.aktual, shift.startedAtEpochMin + 240);
  const lines = chrono.map((a, i) => {
    const mesin = db[a.mcNo];
    const corak = a.corakOverride ?? mesin?.corak ?? "—";
    const yard = a.customYard ?? mesin?.targetYard;
    return formatAktualLine(i, a.mcNo, corak, yard, a.ket);
  });
  const head = [`*LAPORAN SHIFT ${shiftNo}*`, dateStr];
  // Operator yang dicap saat shift ini diarsipkan. Arsip dari sebelum pendataan operator ada
  // tidak punya capnya — untuk itu saja identitas yang berlaku sekarang dipakai sebagai cadangan,
  // karena satu perangkat dipegang satu operator dan laporan tanpa nama sama sekali lebih
  // merugikan daripada nama yang mungkin sudah pindah grup. Shift yang diarsipkan versi ini dan
  // seterusnya selalu memakai capnya sendiri.
  const operatorLine = formatOperatorLine(
    shift.operatorNama || fallbackNama,
    shift.operatorGrup || fallbackGrup,
  );
  if (operatorLine) head.push(operatorLine);
  const body = lines.length > 0 ? `*Selesai (${shift.aktual.length} doff)*\n${lines.join("\n")}` : `*Selesai (0 doff)*`;
  return `${head.join("\n")}\n${DIVIDER}\n\n${body}\n\n${DIVIDER}\n*Total: ${shift.aktual.length} doff*`;
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
