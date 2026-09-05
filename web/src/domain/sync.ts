import { gzip, ungzip } from "pako";
import { currentShiftStartAbsMin, nowAbsMin } from "./format";
import type { AktualEntry, DoffState, Estimasi, MesinData, MesinTipe } from "./types";

export interface SyncEnvelope {
  type?: string;
  payload?: string;
  part?: number;
  total?: number;
}

export interface SerialMesin {
  tipe?: string;
  corak?: string;
  targetYard?: number | null;
  speed?: number | null;
  koreksi?: number | null;
  isActive?: boolean;
}

export interface SerialEstimasi {
  mcNo?: string;
  estAbsMin?: number;
  startAbsMin?: number;
  corakOverride?: string | null;
  yardOverride?: number | null;
  pausedAtAbsMin?: number | null;
}

export interface SerialAktual {
  id?: number;
  mcNo?: string;
  jam?: string;
  ket?: string;
  corakOverride?: string | null;
  customYard?: number | null;
  tsEpochMin?: number | null;
}

export interface SyncPayload {
  // Ultra compact representation: [mcNo, tipe, corak, targetYard, speed, koreksi, isActive?]
  cDb?: [string, string, string, number | null, number | null, number | null, boolean?][];
  db?: Record<string, SerialMesin>;
  estimasi?: Record<string, SerialEstimasi>;
  aktual?: SerialAktual[];
}

/** Cek apakah database mesin masih kosong (belum pernah dikonfigurasi corak/yard/speed oleh pengguna) */
export function isMachineDataEmpty(db: Record<string, MesinData>): boolean {
  const entries = Object.values(db);
  if (entries.length === 0) return true;
  return entries.every(
    (m) => (!m.corak || m.corak.trim() === "" || m.corak === "-") && m.targetYard == null && m.speed == null,
  );
}

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = "";
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToUint8Array(base64: string): Uint8Array {
  const binary = atob(base64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function encodeSyncEnvelope(
  type: "HANDOVER" | "MASTER_DB",
  payload: SyncPayload,
  part?: number,
  total?: number,
): string {
  const jsonStr = JSON.stringify(payload);
  const rawBytes = new TextEncoder().encode(jsonStr);
  const compressed = gzip(rawBytes);
  const base64 = uint8ArrayToBase64(compressed);
  const envelope: SyncEnvelope = { type, payload: base64 };
  if (part != null && total != null) {
    envelope.part = part;
    envelope.total = total;
  }
  return JSON.stringify(envelope);
}

function decodeSyncPayload(encoded: string): SyncPayload | null {
  try {
    const bytes = base64ToUint8Array(encoded);
    const decompressedBytes = ungzip(bytes);
    const jsonStr = new TextDecoder().decode(decompressedBytes);
    return JSON.parse(jsonStr) as SyncPayload;
  } catch {
    return null;
  }
}

/** Buang seluruh whitespace — termasuk yang tersisip DI TENGAH string, bukan cuma di ujung —
 * plus karakter tak-terlihat (BOM, zero-width space/joiner) sebelum di-parse sebagai JSON.
 * Envelope-nya sendiri (dan payload base64 di dalamnya) TIDAK PERNAH mengandung whitespace sama
 * sekali di kedua platform — JSON.stringify/gson.toJson selalu compact, base64 tidak punya
 * spasi — jadi ini aman dibuang seluruhnya. Tanpa ini, satu newline yang tersisip saat teks
 * datanya diteruskan lewat aplikasi pesan pihak ketiga (di-reflow, disalin ulang dari tampilan
 * yang sudah word-wrap, dst.) sudah cukup membuat atob()/JSON.parse gagal total dan menampilkan
 * "Format QR Sync tidak valid" walau datanya sendiri sebenarnya utuh. Sama persis dengan
 * sanitizeSyncText di DoffRepository.kt (Android). */
function sanitizeSyncText(raw: string): string {
  return raw.replace(/[\s\u200B\u200C\u200D\uFEFF]/g, "");
}

export function getNextShiftEstimasiEntries(state: DoffState, nowAbs: number = nowAbsMin()): [string, Estimasi][] {
  const shiftEndAbs = currentShiftStartAbsMin(nowAbs) + 8 * 60;
  return Object.entries(state.estimasi).filter(([, e]) => e.estAbsMin > shiftEndAbs);
}

export function prepareHandoverData(state: DoffState, nowAbs: number = nowAbsMin()): string {
  const nextShiftEstEntries = getNextShiftEstimasiEntries(state, nowAbs);
  // Kalau tidak ada estimasi yang lolos ke shift berikutnya, bagikan semua estimasi yang ada
  // sekarang — sama seperti prepareHandoverData di DoffRepository.kt (Android). Tanpa fallback
  // ini, "Oper Shift" pada kondisi yang persis sama menghasilkan isi QR yang berbeda di kedua
  // platform: Android tetap mengirim sesuatu, web mengirim payload kosong.
  const targetEntries = nextShiftEstEntries.length > 0 ? nextShiftEstEntries : Object.entries(state.estimasi);

  const estMap: Record<string, SerialEstimasi> = {};
  const cDb: [string, string, string, number | null, number | null, number | null, boolean?][] = [];

  for (const [mcNo, e] of targetEntries) {
    estMap[mcNo] = {
      mcNo: e.mcNo,
      estAbsMin: e.estAbsMin,
      startAbsMin: e.startAbsMin,
      corakOverride: e.corakOverride,
      yardOverride: e.yardOverride,
      pausedAtAbsMin: e.pausedAtAbsMin,
    };

    const m = state.db[mcNo];
    if (m) {
      cDb.push([
        mcNo,
        m.tipe,
        m.corak ?? "-",
        m.targetYard ?? null,
        m.speed ?? null,
        m.koreksi ?? null,
        m.isActive !== false,
      ]);
    }
  }

  // Riwayat (aktual) dan estimasi yang sedang berjalan di shift saat ini tidak disertakan
  return encodeSyncEnvelope("HANDOVER", {
    cDb,
    estimasi: estMap,
    aktual: [],
  });
}

/** Hitung jumlah mesin yang memiliki konfigurasi bermakna */
export function getCustomizedMachinesCount(state: DoffState): number {
  return Object.values(state.db).filter(
    (m) => (m.corak && m.corak !== "-" && m.corak.trim() !== "") || m.targetYard != null || m.speed != null || (m.koreksi != null && m.koreksi !== 0),
  ).length;
}

/** Menyiapkan data master mesin dengan kompresi array ultra-padat.
 * Filter: bisa "ALL" (semua 60 mc), "CUSTOMIZED_ONLY" (hanya mesin yang dikonfigurasi),
 * "RANGE_1_30", atau "RANGE_31_60".
 */
export function prepareMasterDbData(
  state: DoffState,
  scope: "ALL" | "CUSTOMIZED_ONLY" | "RANGE_1_30" | "RANGE_31_60" = "CUSTOMIZED_ONLY",
): string {
  const cDb: [string, string, string, number | null, number | null, number | null, boolean?][] = [];

  const entries = Object.entries(state.db);
  for (const [mcNo, m] of entries) {
    const num = parseInt(mcNo, 10);
    if (scope === "RANGE_1_30" && (isNaN(num) || num < 1 || num > 30)) continue;
    if (scope === "RANGE_31_60" && (isNaN(num) || num < 31 || num > 60)) continue;

    const isCustomized =
      (m.corak && m.corak !== "-" && m.corak.trim() !== "") ||
      m.targetYard != null ||
      m.speed != null ||
      (m.koreksi != null && m.koreksi !== 0);

    if (scope === "CUSTOMIZED_ONLY" && !isCustomized) {
      continue;
    }

    cDb.push([
      mcNo,
      m.tipe,
      m.corak ?? "-",
      m.targetYard ?? null,
      m.speed ?? null,
      m.koreksi ?? null,
      m.isActive !== false,
    ]);
  }

  // Jika customized only tapi kosong, kirim minimal yang ada
  if (cDb.length === 0 && scope === "CUSTOMIZED_ONLY") {
    for (const [mcNo, m] of entries.slice(0, 30)) {
      cDb.push([mcNo, m.tipe, m.corak ?? "-", m.targetYard ?? null, m.speed ?? null, m.koreksi ?? null, m.isActive !== false]);
    }
  }

  let part: number | undefined;
  let total: number | undefined;
  if (scope === "RANGE_1_30") {
    part = 1;
    total = 2;
  } else if (scope === "RANGE_31_60") {
    part = 2;
    total = 2;
  }

  return encodeSyncEnvelope("MASTER_DB", { cDb }, part, total);
}

function parseMesinMap(
  cDb?: [string, string, string, number | null, number | null, number | null, boolean?][],
  serialDb?: Record<string, SerialMesin>,
): Record<string, MesinData> {
  const validTipes: Record<string, MesinTipe> = {
    TAPPET: "TAPPET",
    CAM: "CAM",
    D405: "D405",
    D408: "D408",
  };
  const result: Record<string, MesinData> = {};

  // Parse compact array format (cDb)
  if (Array.isArray(cDb)) {
    for (const [mcNo, tipe, corak, targetYard, speed, koreksi, isActive] of cDb) {
      if (!mcNo) continue;
      result[mcNo] = {
        tipe: validTipes[tipe ?? ""] ?? "TAPPET",
        corak: corak ?? "-",
        targetYard: targetYard ?? null,
        speed: speed ?? null,
        koreksi: koreksi ?? null,
        isActive: isActive !== false,
      };
    }
    return result;
  }

  // Backward-compatibility: parse legacy object format
  if (serialDb) {
    for (const [mcNo, v] of Object.entries(serialDb)) {
      if (!v) continue;
      result[mcNo] = {
        tipe: validTipes[v.tipe ?? ""] ?? "TAPPET",
        corak: v.corak ?? "-",
        targetYard: v.targetYard ?? null,
        speed: v.speed ?? null,
        koreksi: v.koreksi ?? null,
        isActive: v.isActive !== false,
      };
    }
  }

  return result;
}

function dedupeIds(entries: AktualEntry[], currentNextId: number): { deduped: AktualEntry[]; nextId: number } {
  if (entries.length === 0) return { deduped: [], nextId: currentNextId };
  let nextFree = Math.max(currentNextId, ...entries.map((e) => e.id || 0)) + 1;
  const used = new Set<number>();
  const deduped: AktualEntry[] = [];
  for (const a of entries) {
    let id = a.id ?? nextFree++;
    if (used.has(id)) {
      id = nextFree++;
    }
    used.add(id);
    deduped.push({ ...a, id });
  }
  return { deduped, nextId: Math.max(nextFree, ...deduped.map((e) => e.id + 1)) };
}

export function processScannedQr(data: string, current: DoffState): { state: DoffState; message?: string } | null {
  try {
    const envelope = JSON.parse(sanitizeSyncText(data)) as SyncEnvelope;
    if (!envelope || !envelope.type || !envelope.payload) return null;
    if (envelope.type !== "HANDOVER" && envelope.type !== "MASTER_DB") return null;

    const payload = decodeSyncPayload(envelope.payload);
    if (!payload) return null;

    if (envelope.type === "MASTER_DB") {
      const incomingDb = parseMesinMap(payload.cDb, payload.db);
      const count = Object.keys(incomingDb).length;
      if (count === 0) return null;

      let msg = `Berhasil menyinkronkan ${count} data mesin ✓`;
      if (envelope.part && envelope.total) {
        msg = `Bagian ${envelope.part}/${envelope.total} (${count} mesin) berhasil diimpor ✓`;
      }

      return {
        state: {
          ...current,
          db: { ...current.db, ...incomingDb },
        },
        message: msg,
      };
    }

    if (envelope.type === "HANDOVER") {
      const incomingDb = parseMesinMap(payload.cDb, payload.db);
      const incomingEst: Record<string, Estimasi> = {};
      if (payload.estimasi) {
        for (const [mcNo, v] of Object.entries(payload.estimasi)) {
          if (!v || v.estAbsMin == null || v.startAbsMin == null) continue;
          const safeMcNo = v.mcNo ?? mcNo;
          incomingEst[safeMcNo] = {
            mcNo: safeMcNo,
            estAbsMin: v.estAbsMin,
            startAbsMin: v.startAbsMin,
            corakOverride: v.corakOverride ?? null,
            yardOverride: v.yardOverride ?? null,
            pausedAtAbsMin: v.pausedAtAbsMin ?? null,
          };
        }
      }

      const incomingAkt: AktualEntry[] = (payload.aktual ?? []).map((a) => ({
        id: a.id ?? 0,
        mcNo: a.mcNo ?? "",
        jam: a.jam ?? "",
        ket: a.ket ?? "",
        corakOverride: a.corakOverride ?? null,
        customYard: a.customYard ?? null,
        tsEpochMin: a.tsEpochMin ?? null,
      }));

      // Merge and deduplicate
      const seen = new Set<string>();
      const combined: AktualEntry[] = [];
      for (const item of [...current.aktual, ...incomingAkt]) {
        const key = `${item.mcNo}_${item.jam}_${item.ket}_${item.corakOverride}_${item.customYard}_${item.tsEpochMin}`;
        if (!seen.has(key)) {
          seen.add(key);
          combined.push(item);
        }
      }

      const { deduped, nextId } = dedupeIds(combined, current.nextId);
      const estCount = Object.keys(incomingEst).length;

      return {
        state: {
          ...current,
          db: { ...current.db, ...incomingDb },
          estimasi: { ...current.estimasi, ...incomingEst },
          aktual: deduped,
          nextId,
        },
        message: `Oper Shift berhasil diimpor (${estCount} estimasi berikutnya) ✓`,
      };
    }

    return null;
  } catch {
    return null;
  }
}

