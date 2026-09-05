import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { prosesBarisKondisiMesin, prosesBarisUmum } from "./commands";
import type { DoffState, MesinData } from "./types";

// Port dari DoffViewModel.kt (prosesBarisKondisiMesin / prosesBarisUmum). Rumus estimasi per
// tipe mesin & aturan doff harus identik dengan Android.

function mesin(over: Partial<MesinData>): MesinData {
  return { tipe: "TAPPET", corak: "-", targetYard: null, speed: null, koreksi: null, ...over };
}

function baseState(): DoffState {
  return {
    db: {
      "29": mesin({ tipe: "TAPPET", corak: "34758" }),
      "61": mesin({ tipe: "D405", corak: "60357", targetYard: 303, speed: 0.158 }),
      "76": mesin({ tipe: "CAM", corak: "21242" }),
      "79": mesin({ tipe: "D408", corak: "60357", koreksi: 18 }),
    },
    estimasi: {},
    aktual: [],
    nextId: 1,
    themeMode: "SYSTEM",
    history: [],
    nextShiftId: 1,
    onboardingSeen: true,
  };
}

function epochMin(y: number, mo: number, d: number, h: number, mi: number): number {
  return Math.floor(new Date(y, mo - 1, d, h, mi, 0, 0).getTime() / 60000);
}

describe("prosesBarisKondisiMesin (estimasi)", () => {
  it("TAPPET/CAM: estAbs = now + durasi tersisa", () => {
    const r = prosesBarisKondisiMesin(baseState(), "29 45", 1000);
    expect(r.result.ok).toBe(true);
    expect(r.newState.estimasi["29"].estAbsMin).toBe(1045);
  });

  it("D405: estAbs = now + round((target - yardBerjalan)/speed)", () => {
    // (303 − 280) / 0.158 = 145.57 → 146
    const r = prosesBarisKondisiMesin(baseState(), "61 280", 1000);
    expect(r.result.ok).toBe(true);
    expect(r.newState.estimasi["61"].estAbsMin).toBe(1146);
  });

  it("D405: speed <= 0 ditolak", () => {
    const s = baseState();
    s.db["61"] = mesin({ tipe: "D405", corak: "60357", targetYard: 303, speed: 0 });
    expect(prosesBarisKondisiMesin(s, "61 280", 1000).result.ok).toBe(false);
  });

  it("nomor mesin tidak valid ditolak", () => {
    expect(prosesBarisKondisiMesin(baseState(), "abc 45", 1000).result.ok).toBe(false);
  });

  it("mesin dengan corak '-' minta diatur dulu", () => {
    const s = baseState();
    s.db["10"] = mesin({ tipe: "TAPPET", corak: "-" });
    const r = prosesBarisKondisiMesin(s, "10 45", 1000);
    expect(r.result.ok).toBe(false);
  });
});

describe("prosesBarisKondisiMesin — D408 (butuh waktu tetap)", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("estAbs = jamKeShiftAbs(bacaan counter) + koreksi", () => {
    vi.setSystemTime(new Date(2026, 0, 15, 13, 0));
    // param now (999) diabaikan untuk D408 — dipakai jamKeShiftAbs(now nyata) internal.
    const r = prosesBarisKondisiMesin(baseState(), "79 1230", 999);
    expect(r.result.ok).toBe(true);
    expect(r.newState.estimasi["79"].estAbsMin).toBe(epochMin(2026, 1, 15, 12, 48));
  });
});

describe("prosesBarisUmum (doff/aktual)", () => {
  it("mencatat aktual & menghapus estimasi mesin itu", () => {
    const s = baseState();
    s.estimasi["61"] = {
      mcNo: "61",
      estAbsMin: 5000,
      startAbsMin: 4000,
      corakOverride: null,
      yardOverride: null,
      pausedAtAbsMin: null,
    };
    const r = prosesBarisUmum(s, "61 120");
    expect(r.result.ok).toBe(true);
    expect(r.newState.aktual[0].mcNo).toBe("61");
    expect(r.newState.aktual[0].customYard).toBe(120);
    expect(r.newState.estimasi["61"]).toBeUndefined();
  });

  it("token '+N' = delta dari target standar", () => {
    // target 303 → 303 + 5 = 308
    const r = prosesBarisUmum(baseState(), "61 +5");
    expect(r.newState.aktual[0].customYard).toBe(308);
  });

  it("keterangan dinormalisasi ke bentuk baku", () => {
    const r = prosesBarisUmum(baseState(), "29 hb");
    expect(r.result.ok).toBe(true);
    expect(r.newState.aktual[0].ket).toContain("(HB)");
  });

  // Corak potongan awal: kainnya dipotong setelah 70 yard pertama, jadi Riwayat harus mencatat
  // 70y — bukan target standar mesin, yang bikin potongan 70y terbaca 165y di laporan.
  it("Matching pada corak potongan awal dicatat 70y, bukan target standar", () => {
    const s = baseState();
    s.db["76"] = mesin({ tipe: "CAM", corak: "21242", targetYard: 165 });
    const r = prosesBarisUmum(s, "76 matching");
    expect(r.result.ok).toBe(true);
    expect(r.newState.aktual[0].ket).toContain("(MATCHING)");
    expect(r.newState.aktual[0].customYard).toBe(70);
  });

  it("Matching pada corak di luar daftar potongan awal tidak dipaksa 70y", () => {
    const r = prosesBarisUmum(baseState(), "29 matching");
    expect(r.result.ok).toBe(true);
    expect(r.newState.aktual[0].customYard).toBeNull();
  });

  it("yard yang diketik operator menang atas aturan 70y", () => {
    const s = baseState();
    s.db["76"] = mesin({ tipe: "CAM", corak: "21242", targetYard: 165 });
    const r = prosesBarisUmum(s, "76 matching 40");
    expect(r.newState.aktual[0].customYard).toBe(40);
  });

  it("paritas Android: '<mc> c <bacaan>' D408 dicatat sebagai DOFF, bukan update estimasi", () => {
    const r = prosesBarisUmum(baseState(), "79 c 1430");
    expect(r.result.ok).toBe(true);
    expect(r.newState.aktual[0].mcNo).toBe("79");
    expect(r.newState.aktual.length).toBe(1);
    expect(r.newState.estimasi["79"]).toBeUndefined();
  });
});
