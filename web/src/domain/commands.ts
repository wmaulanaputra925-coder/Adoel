// Port 1:1 dari DoffViewModel.kt (prosesBarisKondisiMesin / prosesBarisUmum) —
// aturan parsing & rumus estimasi per tipe mesin harus tetap identik ke aplikasi
// Android supaya operator tidak perlu belajar ulang cara pakai.
import { absMinToTimeStr, jamKeShiftAbs, nowAbsMin, nowTimeStr } from "./format";
import { parseDurasi, parseJam, standarisasiKeterangan } from "./parse";
import type { AktualEntry, DoffState, Estimasi, ProsesResult } from "./types";

export interface CommandOutcome {
  result: ProsesResult;
  newState: DoffState;
  /** Id entri aktual yang baru dicatat (kalau ada) — dipakai pemanggil (store) untuk
   * membangun closure undo, karena fungsi ini murni (tidak boleh menyimpan closure
   * ke aksi store). */
  entryId?: number;
}

/** Mode ESTIMASI: hitung & simpan perkiraan waktu doff. Rumus beda per tipe mesin
 * (lihat masing-masing cabang `switch` di bawah). */
export function prosesBarisKondisiMesin(state: DoffState, ln: string, now: number): CommandOutcome {
  const parts = ln.trim().split(/\s+/);
  if (parts.length < 2) return { result: { ok: false, msg: "Kurang data" }, newState: state };
  const mcNo = parts[0];
  if (!/^\d{1,4}$/.test(mcNo)) return { result: { ok: false, msg: "Nomor mesin tidak valid" }, newState: state };
  const mesin = state.db[mcNo];
  if (!mesin) return { result: { ok: false, msg: `Mc ${mcNo} belum terdaftar, tambahkan dulu di Pengaturan` }, newState: state };
  if (!mesin.corak || mesin.corak.trim() === "" || mesin.corak.trim() === "-") {
    return { result: { ok: false, msg: `Mc ${mcNo} belum diatur, atur corak dulu di Pengaturan` }, newState: state };
  }

  let estAbs: number;
  switch (mesin.tipe) {
    case "TAPPET":
    case "CAM": {
      // nilai = durasi tersisa dari sekarang
      const sisaMin = parseDurasi(parts[1]);
      if (sisaMin === null) return { result: { ok: false, msg: "Durasi tidak valid" }, newState: state };
      estAbs = now + sisaMin;
      break;
    }
    case "D405": {
      // nilai = yard yang SUDAH BERJALAN saat ini (boleh diakhiri 'y')
      const yardStr = parts[1].replace(/[yY]+$/, "");
      const yardBerjalan = parseFloat(yardStr.replace(",", "."));
      if (Number.isNaN(yardBerjalan)) return { result: { ok: false, msg: "Yard tidak valid" }, newState: state };
      const existing = state.estimasi[mcNo];
      const target = existing?.yardOverride ?? mesin.targetYard;
      if (target === null || target === undefined)
        return { result: { ok: false, msg: "Data target kosong" }, newState: state };
      const speed = mesin.speed;
      if (speed === null || speed === undefined)
        return { result: { ok: false, msg: "Data speed kosong" }, newState: state };
      if (speed <= 0) return { result: { ok: false, msg: "Speed harus > 0" }, newState: state };
      const sisaMin = Math.round((target - yardBerjalan) / speed);
      estAbs = now + sisaMin;
      break;
    }
    case "D408": {
      // nilai = bacaan JAM PADA COUNTER MESIN (bukan jam sekarang). Boleh diawali
      // token "C" yang diabaikan: "31 C 1430" == "31 1430".
      let jamCounterStr = parts[1];
      if (jamCounterStr.toLowerCase() === "c" && parts.length >= 3) jamCounterStr = parts[2];
      const jamMin = parseJam(jamCounterStr);
      if (jamMin === null) return { result: { ok: false, msg: "Jam counter tidak valid" }, newState: state };
      const koreksi = mesin.koreksi;
      if (koreksi === null || koreksi === undefined)
        return { result: { ok: false, msg: "Menit koreksi kosong" }, newState: state };
      estAbs = jamKeShiftAbs(jamMin) + Math.round(koreksi);
      break;
    }
  }

  const existing = state.estimasi[mcNo];
  const newEst: Estimasi = {
    mcNo,
    estAbsMin: estAbs,
    startAbsMin: existing?.startAbsMin ?? now,
    corakOverride: existing?.corakOverride ?? null,
    yardOverride: existing?.yardOverride ?? null,
    pausedAtAbsMin: null,
  };
  const newState: DoffState = { ...state, estimasi: { ...state.estimasi, [mcNo]: newEst } };

  return {
    result: { ok: true, msg: `Mc ${mcNo} → ${absMinToTimeStr(estAbs)}`, mcNo, estAbs },
    newState,
  };
}

/** Mode DOFFING/AKTUAL: catat doff yang sudah selesai. */
export function prosesBarisUmum(state: DoffState, ln: string): CommandOutcome {
  const parts = ln.trim().split(/\s+/).filter((p) => p.length > 0);
  if (parts.length === 0) return { result: { ok: false, msg: "Kosong" }, newState: state };
  const mcNo = parts[0];
  if (!/^\d{1,4}$/.test(mcNo)) return { result: { ok: false, msg: "Nomor mesin tidak valid" }, newState: state };
  const mesin = state.db[mcNo];
  if (!mesin) return { result: { ok: false, msg: `Mc ${mcNo} belum terdaftar, tambahkan dulu di Pengaturan` }, newState: state };

  const jam = nowTimeStr();
  let customYard: number | null = null;
  const ketTokens: string[] = [];

  for (let i = 1; i < parts.length; i++) {
    const token = parts[i];
    const ydMatch = /^(\+?)([\d.,]+)y?$/i.exec(token);
    if (ydMatch) {
      const isDelta = ydMatch[1] === "+";
      const num = parseFloat(ydMatch[2].replace(",", "."));
      if (!Number.isNaN(num)) {
        const standard = state.estimasi[mcNo]?.yardOverride ?? mesin.targetYard;
        // '+' = delta dari target standar (mis. "+5" = 5 yard lewat target).
        // Tanpa '+' = nilai absolut.
        customYard = isDelta && standard !== null && standard !== undefined ? standard + num : num;
        continue;
      }
    }
    ketTokens.push(token);
  }

  const extra = standarisasiKeterangan(ketTokens.join(" ").trim());
  const ket = extra.length > 0 ? `${jam}(${extra})` : jam;

  const prevEst = state.estimasi[mcNo] ?? null;
  const effectiveCorak = prevEst?.corakOverride ?? mesin.corak;

  const entryId = state.nextId;
  const entry: AktualEntry = {
    id: entryId,
    mcNo,
    jam,
    ket,
    corakOverride: effectiveCorak !== mesin.corak ? effectiveCorak : null,
    customYard,
    tsEpochMin: nowAbsMin(),
  };
  const { [mcNo]: _removed, ...restEstimasi } = state.estimasi;
  const newState: DoffState = {
    ...state,
    nextId: entryId + 1,
    estimasi: restEstimasi,
    aktual: [entry, ...state.aktual],
  };

  return {
    result: { ok: true, msg: `Mc ${mcNo} ✓`, mcNo, prevEst, entry },
    newState,
    entryId,
  };
}
