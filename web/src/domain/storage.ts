import {
  DEFAULT_CORAK_POTONGAN_AWAL,
  DEFAULT_CORAK_SHORTCUTS,
  DEFAULT_KETERANGAN_SHORTCUTS,
  type AktualEntry,
  type DoffState,
  type MesinTipe,
} from "./types";
import { buildDefaultDb } from "./defaultDb";

const STORAGE_KEY = "adoel_state_v2";

const VALID_TIPE: MesinTipe[] = ["TAPPET", "CAM", "D405", "D408"];

/** Skema JSON di bawah harus tetap identik dengan `SerialState`/`SerialMesin`/dst.
 * di `DoffRepository.kt` (aplikasi Android) — nama field APA ADANYA — supaya file
 * backup "Cadangkan" dari HP bisa langsung diimpor ke sini, dan sebaliknya. */

/** Parse JSON persisted/backup jadi DoffState, atau null kalau bukan backup Adoel
 * yang valid (JSON rusak, atau field `db` kosong/tidak ada). Tidak pernah menimpa
 * dengan state kosong kalau input tidak valid. */
export function parseBackupJson(json: string): DoffState | null {
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return null;
  }
  if (typeof raw !== "object" || raw === null) return null;
  const serial = raw as Record<string, unknown>;
  const rawDb = serial.db;
  if (typeof rawDb !== "object" || rawDb === null || Object.keys(rawDb).length === 0) return null;

  const db: DoffState["db"] = {};
  for (const [mcNo, v] of Object.entries(rawDb as Record<string, any>)) {
    const tipe: MesinTipe = VALID_TIPE.includes(v?.tipe) ? v.tipe : "TAPPET";
    const isActive = typeof v?.isActive === "boolean" ? v.isActive : undefined;
    db[mcNo] = {
      tipe,
      corak: typeof v?.corak === "string" ? v.corak : "-",
      targetYard: typeof v?.targetYard === "number" ? v.targetYard : null,
      speed: typeof v?.speed === "number" ? v.speed : null,
      koreksi: typeof v?.koreksi === "number" ? v.koreksi : null,
      ...(isActive !== undefined ? { isActive } : {}),
    };
  }

  const estimasi: DoffState["estimasi"] = {};
  const rawEst = (serial.estimasi as Record<string, any>) ?? {};
  for (const [mcNo, v] of Object.entries(rawEst)) {
    estimasi[mcNo] = {
      mcNo: v?.mcNo ?? mcNo,
      estAbsMin: Number(v?.estAbsMin) || 0,
      startAbsMin: Number(v?.startAbsMin) || 0,
      corakOverride: v?.corakOverride ?? null,
      yardOverride: typeof v?.yardOverride === "number" ? v.yardOverride : null,
      pausedAtAbsMin: typeof v?.pausedAtAbsMin === "number" ? v.pausedAtAbsMin : null,
    };
  }

  const rawAktual = Array.isArray(serial.aktual) ? (serial.aktual as any[]) : [];
  const aktual = dedupeIds(rawAktual);

  const rawHistory = Array.isArray(serial.history) ? (serial.history as any[]) : [];
  const history: DoffState["history"] = rawHistory.map((r) => ({
    id: Number(r?.id) || 0,
    startedAtEpochMin: Number(r?.startedAtEpochMin) || 0,
    endedAtEpochMin: Number(r?.endedAtEpochMin) || 0,
    aktual: dedupeIds(Array.isArray(r?.aktual) ? r.aktual : []),
    estimasiRemaining: Object.fromEntries(
      Object.entries((r?.estimasiRemaining as Record<string, any>) ?? {}).map(([mcNo, v]) => [
        mcNo,
        {
          mcNo: v?.mcNo ?? mcNo,
          estAbsMin: Number(v?.estAbsMin) || 0,
          startAbsMin: Number(v?.startAbsMin) || 0,
          corakOverride: v?.corakOverride ?? null,
          yardOverride: typeof v?.yardOverride === "number" ? v.yardOverride : null,
          pausedAtAbsMin: typeof v?.pausedAtAbsMin === "number" ? v.pausedAtAbsMin : null,
        },
      ]),
    ),
  }));

  const maxAktualId = aktual.reduce((m, a) => Math.max(m, a.id), 0);
  const nextId = Math.max(Number(serial.nextId) || 0, maxAktualId + 1);

  const rawShortcuts = Array.isArray(serial.keteranganShortcuts)
    ? (serial.keteranganShortcuts as string[]).map((s) => String(s).trim()).filter((s) => s.length > 0)
    : [];

  const rawCorakShortcuts = Array.isArray(serial.corakShortcuts)
    ? (serial.corakShortcuts as string[]).map((s) => String(s).trim().toUpperCase()).filter((s) => s.length > 0)
    : [];

  const rawCorakPotonganAwal = Array.isArray(serial.corakPotonganAwal)
    ? (serial.corakPotonganAwal as string[]).map((s) => String(s).trim().toUpperCase()).filter((s) => s.length > 0)
    : DEFAULT_CORAK_POTONGAN_AWAL;

  return {
    db,
    estimasi,
    aktual,
    nextId,
    themeMode: serial.themeMode === "LIGHT" || serial.themeMode === "DARK" ? serial.themeMode : "SYSTEM",
    history,
    nextShiftId: Number(serial.nextShiftId) || 1,
    onboardingSeen: typeof serial.onboardingSeen === "boolean" ? serial.onboardingSeen : true,
    keteranganShortcuts: rawShortcuts,
    corakShortcuts: rawCorakShortcuts,
    corakPotonganAwal: rawCorakPotonganAwal,
  };
}

/** Menjamin id unik di sebuah daftar aktual — data lama (dari race kondisi) bisa
 * saja punya id duplikat, dan React butuh key unik per baris. */
function dedupeIds(raw: any[]): AktualEntry[] {
  let nextFree = raw.reduce((m, a) => Math.max(m, Number(a?.id) || 0), 0) + 1;
  const used = new Set<number>();
  return raw.map((a) => {
    let id = Number(a?.id) || 0;
    if (used.has(id)) {
      id = nextFree++;
    }
    used.add(id);
    return {
      id,
      mcNo: String(a?.mcNo ?? ""),
      jam: String(a?.jam ?? ""),
      ket: String(a?.ket ?? ""),
      corakOverride: a?.corakOverride ?? null,
      customYard: typeof a?.customYard === "number" ? a.customYard : null,
      tsEpochMin: typeof a?.tsEpochMin === "number" ? a.tsEpochMin : null,
    };
  });
}

export function serializeState(state: DoffState): string {
  return JSON.stringify(state);
}

export function loadState(): DoffState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = parseBackupJson(raw);
      if (parsed) return parsed;
    }
  } catch {
    // localStorage tidak tersedia / rusak — jatuh ke default di bawah.
  }
  return {
    db: buildDefaultDb(),
    estimasi: {},
    aktual: [],
    nextId: 1,
    themeMode: "SYSTEM",
    history: [],
    nextShiftId: 1,
    onboardingSeen: false,
    keteranganShortcuts: DEFAULT_KETERANGAN_SHORTCUTS,
    corakShortcuts: DEFAULT_CORAK_SHORTCUTS,
    corakPotonganAwal: DEFAULT_CORAK_POTONGAN_AWAL,
  };
}

export function saveState(state: DoffState): void {
  try {
    localStorage.setItem(STORAGE_KEY, serializeState(state));
  } catch {
    // Kuota localStorage penuh atau diblokir — abaikan, state tetap ada di memori.
  }
}
