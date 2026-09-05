import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { prosesBarisKondisiMesin, prosesBarisUmum } from "../domain/commands";
import { buildDefaultDb } from "../domain/defaultDb";
import { currentShiftStartAbsMin, getRepresentativeEpochMin, nowAbsMin } from "../domain/format";
import { parseJam } from "../domain/parse";
import { loadState, parseBackupJson, saveState, serializeState } from "../domain/storage";
import { processScannedQr } from "../domain/sync";
import type { AktualEntry, DoffState, Estimasi, MesinData, ProsesResult, ShiftRecord, ThemeMode } from "../domain/types";
import { DEFAULT_CORAK_POTONGAN_AWAL, DEFAULT_CORAK_SHORTCUTS, DEFAULT_KETERANGAN_SHORTCUTS } from "../domain/types";

// Retensi riwayat: 30 HARI KALENDER (bukan jumlah shift) — sama seperti
// HISTORY_RETENTION_DAYS di DoffViewModel.kt.
const HISTORY_RETENTION_DAYS = 30;
const HISTORY_RETENTION_MIN = HISTORY_RETENTION_DAYS * 24 * 60;

export interface UndoableAction {
  undo: () => void;
  redo: () => void;
}

const UNDO_STACK_CAP = 20;

interface DoffStore {
  state: DoffState;
  submitEstimasi: (line: string) => ProsesResult;
  submitAktual: (line: string) => ProsesResult;
  hapusEstimasi: (mcNo: string) => void;
  restoreEstimasi: (est: Estimasi) => void;
  pauseEstimasi: (mcNo: string) => void;
  resumeEstimasi: (mcNo: string) => void;
  hapusAktualById: (id: number) => void;
  restoreAktual: (entry: AktualEntry) => void;
  hapusShift: (id: number) => void;
  updateAktual: (id: number, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => void;
  updateHistoryEntry: (shiftId: number, id: number, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => void;
  deleteHistoryEntry: (shiftId: number, id: number) => void;
  addHistoryEntry: (shiftId: number, mcNo: string, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => void;
  finishShift: () => void;
  setMesin: (mcNo: string, data: MesinData) => void;
  resetMesin: (mcNo: string) => void;
  resetDb: () => void;
  setThemeMode: (mode: ThemeMode) => void;
  setOnboardingSeen: () => void;
  addKeteranganShortcut: (shortcut: string) => void;
  removeKeteranganShortcut: (shortcut: string) => void;
  resetKeteranganShortcuts: () => void;
  setKeteranganShortcuts: (list: string[]) => void;
  addCorakShortcut: (shortcut: string) => void;
  removeCorakShortcut: (shortcut: string) => void;
  resetCorakShortcuts: () => void;
  setCorakShortcuts: (list: string[]) => void;
  addCorakPotonganAwal: (corak: string) => void;
  removeCorakPotonganAwal: (corak: string) => void;
  resetCorakPotonganAwal: () => void;
  exportJson: () => string;
  importJson: (json: string) => DoffState | null;
  importQrSync: (qrData: string) => { success: boolean; message?: string };
  /** Riwayat undo/redo tingkat-konsol (Master Blueprint v9.2 §7) — menggantikan
   * closure undo per-toast lama. Tidak persisten lintas sesi. */
  pushUndo: (action: UndoableAction) => void;
  clearUndo: () => void;
  canUndo: boolean;
  canRedo: boolean;
  undo: () => void;
  redo: () => void;
}

const Ctx = createContext<DoffStore | null>(null);

export function DoffStoreProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<DoffState>(() => loadState());
  const isFirstRender = useRef(true);

  // Riwayat undo/redo tingkat-konsol — bukan bagian dari DoffState (tidak dipersist,
  // sama seperti UndoRedoState di Android: seumur sesi, bukan seumur proses/app).
  const [undoStack, setUndoStack] = useState<UndoableAction[]>([]);
  const [redoStack, setRedoStack] = useState<UndoableAction[]>([]);

  const pushUndo = useCallback((action: UndoableAction) => {
    setUndoStack((stack) => {
      const next = [...stack, action];
      return next.length > UNDO_STACK_CAP ? next.slice(next.length - UNDO_STACK_CAP) : next;
    });
    setRedoStack([]);
  }, []);

  const clearUndo = useCallback(() => {
    setUndoStack([]);
    setRedoStack([]);
  }, []);

  const undo = useCallback(() => {
    setUndoStack((stack) => {
      if (stack.length === 0) return stack;
      const action = stack[stack.length - 1];
      action.undo();
      setRedoStack((r) => [...r, action]);
      return stack.slice(0, -1);
    });
  }, []);

  const redo = useCallback(() => {
    setRedoStack((stack) => {
      if (stack.length === 0) return stack;
      const action = stack[stack.length - 1];
      action.redo();
      setUndoStack((u) => [...u, action]);
      return stack.slice(0, -1);
    });
  }, []);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    saveState(state);
  }, [state]);

  const submitEstimasi = useCallback((line: string): ProsesResult => {
    let result!: ProsesResult;
    setState((s) => {
      const outcome = prosesBarisKondisiMesin(s, line, nowAbsMin());
      result = outcome.result;
      return outcome.newState;
    });
    return result;
  }, []);

  const hapusEstimasi = useCallback((mcNo: string) => {
    setState((s) => {
      const { [mcNo]: _removed, ...rest } = s.estimasi;
      return { ...s, estimasi: rest };
    });
  }, []);

  const restoreEstimasi = useCallback((est: Estimasi) => {
    setState((s) => ({ ...s, estimasi: { ...s.estimasi, [est.mcNo]: est } }));
  }, []);

  // Membekukan hitung mundur Mc mcNo di titik sekarang (lihat Estimasi.pausedAtAbsMin /
  // effectiveRemaining) — no-op kalau sudah dijeda atau estimasinya sudah tidak ada lagi.
  const pauseEstimasi = useCallback((mcNo: string) => {
    setState((s) => {
      const est = s.estimasi[mcNo];
      if (!est || est.pausedAtAbsMin !== null) return s;
      return { ...s, estimasi: { ...s.estimasi, [mcNo]: { ...est, pausedAtAbsMin: nowAbsMin() } } };
    });
  }, []);

  // Mencairkan jeda Mc mcNo, menggeser estAbsMin maju persis selama ia dijeda supaya
  // sisa waktu yang terlihat operator sebelum menekan Lanjutkan tetap sama.
  const resumeEstimasi = useCallback((mcNo: string) => {
    setState((s) => {
      const est = s.estimasi[mcNo];
      if (!est || est.pausedAtAbsMin === null) return s;
      const pausedFor = nowAbsMin() - est.pausedAtAbsMin;
      return {
        ...s,
        estimasi: { ...s.estimasi, [mcNo]: { ...est, estAbsMin: est.estAbsMin + pausedFor, pausedAtAbsMin: null } },
      };
    });
  }, []);

  const hapusAktualById = useCallback((id: number) => {
    setState((s) => ({ ...s, aktual: s.aktual.filter((a) => a.id !== id) }));
  }, []);

  const restoreAktual = useCallback((entry: AktualEntry) => {
    setState((s) => (s.aktual.some((a) => a.id === entry.id) ? s : { ...s, aktual: [entry, ...s.aktual] }));
  }, []);

  // Dipakai baik oleh input konsol mode DOFFING maupun tombol "Doff" cepat di kartu
  // radar (mengetik "<mcNo>" saja setara dengan tap tombol doff) — identik dengan
  // handleDoff/handleCommand(Mode.AKTUAL) di MainScreen.kt Android. undo dibangun di
  // sini (bukan di commands.ts, yang murni) karena butuh akses ke aksi store lain
  // (hapusAktualById/restoreEstimasi), makanya didefinisikan setelah keduanya.
  const submitAktual = useCallback(
    (line: string): ProsesResult => {
      let result!: ProsesResult;
      setState((s) => {
        const outcome = prosesBarisUmum(s, line);
        result = outcome.result;
        if (result.ok && outcome.entryId !== undefined) {
          const entryId = outcome.entryId;
          const prevEst = result.prevEst;
          result = {
            ...result,
            undo: () => {
              hapusAktualById(entryId);
              if (prevEst) restoreEstimasi(prevEst);
            },
          };
        }
        return outcome.newState;
      });
      return result;
    },
    [hapusAktualById, restoreEstimasi],
  );

  const hapusShift = useCallback((id: number) => {
    setState((s) => ({ ...s, history: s.history.filter((h) => h.id !== id) }));
  }, []);

  const updateAktual = useCallback(
    (id: number, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => {
      setState((s) => ({
        ...s,
        aktual: s.aktual.map((a) => (a.id === id ? { ...a, jam, ket, corakOverride, customYard } : a)),
      }));
    },
    [],
  );

  const updateHistoryEntry = useCallback(
    (shiftId: number, id: number, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => {
      const jamMin = parseJam(jam);
      setState((s) => {
        const record = s.history.find((h) => h.id === shiftId);
        if (!record) return s;
        const target = record.aktual.find((a) => a.id === id);
        if (!target) return s;
        let newTs: number | null = target.tsEpochMin;
        if (jamMin !== null) {
          const ref = target.tsEpochMin ?? getRepresentativeEpochMin(record);
          const dayStart = Math.floor(ref / 1440) * 1440;
          newTs = dayStart + jamMin;
        }
        const updatedAktual = record.aktual.map((a) =>
          a.id === id ? { ...a, jam, ket, corakOverride, customYard, tsEpochMin: newTs } : a,
        );
        const updatedRecord = { ...record, aktual: updatedAktual };
        return {
          ...s,
          history: s.history.map((h) => (h.id === shiftId ? updatedRecord : h)),
        };
      });
    },
    [],
  );

  const deleteHistoryEntry = useCallback((shiftId: number, id: number) => {
    setState((s) => {
      const record = s.history.find((h) => h.id === shiftId);
      if (!record) return s;
      const updatedRecord = { ...record, aktual: record.aktual.filter((a) => a.id !== id) };
      return {
        ...s,
        history: s.history.map((h) => (h.id === shiftId ? updatedRecord : h)),
      };
    });
  }, []);

  const addHistoryEntry = useCallback(
    (shiftId: number, mcNo: string, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => {
      const jamMin = parseJam(jam);
      setState((s) => {
        const record = s.history.find((h) => h.id === shiftId);
        if (!record) return s;
        const ref = getRepresentativeEpochMin(record);
        const dayStart = Math.floor(ref / 1440) * 1440;
        const ts = jamMin !== null ? dayStart + jamMin : ref;
        const newEntry: AktualEntry = {
          id: s.nextId,
          mcNo,
          jam,
          ket,
          corakOverride,
          customYard,
          tsEpochMin: ts,
        };
        const updatedRecord = { ...record, aktual: [newEntry, ...record.aktual] };
        return {
          ...s,
          nextId: s.nextId + 1,
          history: s.history.map((h) => (h.id === shiftId ? updatedRecord : h)),
        };
      });
    },
    [],
  );

  const importQrSync = useCallback((qrData: string): { success: boolean; message?: string } => {
    let success = false;
    let resultMessage: string | undefined;
    setState((current) => {
      const res = processScannedQr(qrData, current);
      if (res) {
        success = true;
        resultMessage = res.message;
        clearUndo();
        return res.state;
      }
      return current;
    });
    return { success, message: resultMessage };
  }, [clearUndo]);

  // "Selesai Shift": arsipkan entri riwayat berjalan (aktual) ke history, lalu hapus
  // baris estimasi aktif (tanpa diarsipkan). Riwayat lebih tua dari 30 hari otomatis dibuang di sini.
  const finishShift = useCallback(() => {
    clearUndo();
    setState((s) => {
      if (s.aktual.length === 0 && Object.keys(s.estimasi).length === 0) return s;
      const now = nowAbsMin();
      const cutoff = now - HISTORY_RETENTION_MIN;

      // Hanya buat arsip shift baru jika terdapat entri di halaman riwayat
      if (s.aktual.length > 0) {
        // Arsip shift memakai jadwalnya (06/14/22 + 8 jam), bukan doff pertama dan detik operator
        // menekan Selesai Shift — dua hal itu bergeser tergantung kapan beam pertama habis dan
        // seberapa telat shift ditutup, sehingga shift yang sama tercatat dengan jam berbeda tiap
        // kali. Doff paling awal (atau now kalau tidak ada timestamp) hanya dipakai untuk memilih
        // shift MANA, jadi penutupan yang telat — bahkan melewati batas shift berikutnya — tetap
        // masuk ke shift tempat pekerjaan itu dilakukan.
        const tsList = s.aktual.map((a) => a.tsEpochMin).filter((t): t is number => t !== null);
        const anchor = Math.min(...(tsList.length > 0 ? tsList : [now]));
        const started = currentShiftStartAbsMin(anchor);
        const record: ShiftRecord = {
          id: s.nextShiftId,
          startedAtEpochMin: started,
          endedAtEpochMin: started + 8 * 60,
          aktual: s.aktual,
          estimasiRemaining: {},
        };
        return {
          ...s,
          estimasi: {},
          aktual: [],
          history: [record, ...s.history].filter((h) => h.endedAtEpochMin >= cutoff),
          nextShiftId: s.nextShiftId + 1,
        };
      }

      // Jika hanya ada baris estimasi tanpa riwayat doffing, cukup hapus estimasi
      return {
        ...s,
        estimasi: {},
        aktual: [],
      };
    });
  }, [clearUndo]);

  const setMesin = useCallback((mcNo: string, data: MesinData) => {
    setState((s) => ({ ...s, db: { ...s.db, [mcNo]: data } }));
  }, []);

  const resetMesin = useCallback((mcNo: string) => {
    setState((s) => ({ ...s, db: { ...s.db, [mcNo]: buildDefaultDb()[mcNo] } }));
  }, []);

  const resetDb = useCallback(() => {
    clearUndo();
    setState(() => ({
      db: buildDefaultDb(),
      estimasi: {},
      aktual: [],
      nextId: 1,
      themeMode: "SYSTEM",
      history: [],
      nextShiftId: 1,
      onboardingSeen: true,
      keteranganShortcuts: DEFAULT_KETERANGAN_SHORTCUTS,
      corakShortcuts: DEFAULT_CORAK_SHORTCUTS,
    }));
  }, [clearUndo]);

  const setThemeMode = useCallback((mode: ThemeMode) => {
    setState((s) => ({ ...s, themeMode: mode }));
  }, []);

  const setOnboardingSeen = useCallback(() => {
    setState((s) => ({ ...s, onboardingSeen: true }));
  }, []);

  const addKeteranganShortcut = useCallback((shortcut: string) => {
    const trimmed = shortcut.trim().toUpperCase();
    if (!trimmed) return;
    setState((s) => {
      const list = s.keteranganShortcuts ?? DEFAULT_KETERANGAN_SHORTCUTS;
      if (list.includes(trimmed)) return s;
      return { ...s, keteranganShortcuts: [...list, trimmed] };
    });
  }, []);

  const removeKeteranganShortcut = useCallback((shortcut: string) => {
    setState((s) => {
      const list = s.keteranganShortcuts ?? DEFAULT_KETERANGAN_SHORTCUTS;
      return { ...s, keteranganShortcuts: list.filter((item) => item !== shortcut) };
    });
  }, []);

  const resetKeteranganShortcuts = useCallback(() => {
    setState((s) => ({
      ...s,
      keteranganShortcuts: [],
    }));
  }, []);

  const setKeteranganShortcuts = useCallback((list: string[]) => {
    const cleaned = list.map((x) => x.trim().toUpperCase()).filter((x) => x.length > 0);
    setState((s) => ({
      ...s,
      keteranganShortcuts: cleaned,
    }));
  }, []);

  const addCorakShortcut = useCallback((shortcut: string) => {
    const trimmed = shortcut.trim().toUpperCase();
    if (!trimmed) return;
    setState((s) => {
      const list = s.corakShortcuts ?? [];
      if (list.includes(trimmed)) return s;
      return { ...s, corakShortcuts: [...list, trimmed] };
    });
  }, []);

  const removeCorakShortcut = useCallback((shortcut: string) => {
    setState((s) => {
      const list = s.corakShortcuts ?? [];
      return { ...s, corakShortcuts: list.filter((item) => item !== shortcut) };
    });
  }, []);

  const resetCorakShortcuts = useCallback(() => {
    setState((s) => ({
      ...s,
      corakShortcuts: [],
    }));
  }, []);

  const setCorakShortcuts = useCallback((list: string[]) => {
    const cleaned = list.map((x) => x.trim().toUpperCase()).filter((x) => x.length > 0);
    setState((s) => ({
      ...s,
      corakShortcuts: cleaned,
    }));
  }, []);

  const addCorakPotonganAwal = useCallback((corak: string) => {
    const trimmed = corak.trim().toUpperCase();
    if (!trimmed) return;
    setState((s) => {
      const list = s.corakPotonganAwal ?? DEFAULT_CORAK_POTONGAN_AWAL;
      if (list.includes(trimmed)) return s;
      return { ...s, corakPotonganAwal: [...list, trimmed] };
    });
  }, []);

  const removeCorakPotonganAwal = useCallback((corak: string) => {
    setState((s) => {
      const list = s.corakPotonganAwal ?? DEFAULT_CORAK_POTONGAN_AWAL;
      return { ...s, corakPotonganAwal: list.filter((item) => item !== corak) };
    });
  }, []);

  // Beda dari resetCorakShortcuts (yang kosongkan ke []): daftar ini adalah aturan kualitas
  // aktif, jadi "reset" mengembalikan ke 3 corak standar pabrik, bukan mengosongkannya.
  const resetCorakPotonganAwal = useCallback(() => {
    setState((s) => ({
      ...s,
      corakPotonganAwal: DEFAULT_CORAK_POTONGAN_AWAL,
    }));
  }, []);

  const exportJson = useCallback(() => serializeState(state), [state]);

  const importJson = useCallback((json: string): DoffState | null => {
    const parsed = parseBackupJson(json);
    if (parsed) {
      clearUndo();
      setState(parsed);
    }
    return parsed;
  }, [clearUndo]);

  const canUndo = undoStack.length > 0;
  const canRedo = redoStack.length > 0;

  // Di-memo supaya identitas value context hanya berubah saat ada yang benar-benar berubah
  // (state, ketersediaan undo/redo, atau exportJson yang bergantung state) — bukan objek literal
  // baru tiap render. Aksi lain sudah stabil lewat useCallback, jadi consumer tidak ikut
  // re-render hanya karena provider merender ulang (mis. dari tick 20 detik di App).
  const store = useMemo<DoffStore>(
    () => ({
      state,
      submitEstimasi,
      submitAktual,
      hapusEstimasi,
      restoreEstimasi,
      pauseEstimasi,
      resumeEstimasi,
      hapusAktualById,
      restoreAktual,
      hapusShift,
      updateAktual,
      updateHistoryEntry,
      deleteHistoryEntry,
      addHistoryEntry,
      finishShift,
      setMesin,
      resetMesin,
      resetDb,
      setThemeMode,
      setOnboardingSeen,
      addKeteranganShortcut,
      removeKeteranganShortcut,
      resetKeteranganShortcuts,
      setKeteranganShortcuts,
      addCorakShortcut,
      removeCorakShortcut,
      resetCorakShortcuts,
      setCorakShortcuts,
      addCorakPotonganAwal,
      removeCorakPotonganAwal,
      resetCorakPotonganAwal,
      exportJson,
      importJson,
      importQrSync,
      pushUndo,
      clearUndo,
      canUndo,
      canRedo,
      undo,
      redo,
    }),
    [
      state,
      submitEstimasi,
      submitAktual,
      hapusEstimasi,
      restoreEstimasi,
      pauseEstimasi,
      resumeEstimasi,
      hapusAktualById,
      restoreAktual,
      hapusShift,
      updateAktual,
      updateHistoryEntry,
      deleteHistoryEntry,
      addHistoryEntry,
      finishShift,
      setMesin,
      resetMesin,
      resetDb,
      setThemeMode,
      setOnboardingSeen,
      addKeteranganShortcut,
      removeKeteranganShortcut,
      resetKeteranganShortcuts,
      setKeteranganShortcuts,
      addCorakShortcut,
      removeCorakShortcut,
      resetCorakShortcuts,
      setCorakShortcuts,
      addCorakPotonganAwal,
      removeCorakPotonganAwal,
      resetCorakPotonganAwal,
      exportJson,
      importJson,
      importQrSync,
      pushUndo,
      clearUndo,
      canUndo,
      canRedo,
      undo,
      redo,
    ],
  );

  return <Ctx.Provider value={store}>{children}</Ctx.Provider>;
}

export function useDoffStore(): DoffStore {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useDoffStore must be used within DoffStoreProvider");
  return ctx;
}
