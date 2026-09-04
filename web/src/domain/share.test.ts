import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { shareHistoryText, shareShiftText } from "./share";
import type { DoffState, MesinData, ShiftRecord } from "./types";

// Golden-fixture, port dari ShareTextTest.kt — pesan "Bagikan" dibaca rekan di lantai produksi,
// jadi perubahan format apa pun harus disengaja. Zona lokal dipakai konsisten (lihat format.test).

const DIVIDER = "─".repeat(16);

function epochMin(y: number, mo: number, d: number, h: number, mi: number): number {
  return Math.floor(new Date(y, mo - 1, d, h, mi, 0, 0).getTime() / 60000);
}

const db: Record<string, MesinData> = {
  "29": { tipe: "TAPPET", corak: "34758", targetYard: 303, speed: null, koreksi: null },
  "61": { tipe: "D405", corak: "60357", targetYard: 303, speed: 0.158, koreksi: null },
  "76": { tipe: "CAM", corak: "21242", targetYard: 165, speed: null, koreksi: null },
};

describe("shareHistoryText", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("selesai + operan shift berikutnya", () => {
    vi.setSystemTime(new Date(2026, 0, 15, 12, 0));
    const state: DoffState = {
      db,
      aktual: [
        { id: 2, mcNo: "61", jam: "11.00", ket: "11.00(HB)", corakOverride: null, customYard: 120, tsEpochMin: epochMin(2026, 1, 15, 11, 0) },
        { id: 1, mcNo: "29", jam: "10.00", ket: "10.00", corakOverride: null, customYard: null, tsEpochMin: epochMin(2026, 1, 15, 10, 0) },
      ],
      estimasi: {
        "76": { mcNo: "76", estAbsMin: epochMin(2026, 1, 15, 16, 20), startAbsMin: epochMin(2026, 1, 15, 15, 0), corakOverride: null, yardOverride: null, pausedAtAbsMin: null },
      },
      nextId: 3,
      themeMode: "SYSTEM",
      history: [],
      nextShiftId: 1,
      onboardingSeen: true,
    };

    const expected =
      `*UPDATE DOFFING AKTIF*\n📅 15/01/2026\n${DIVIDER}\n\n` +
      "✅ *Selesai (2 doff)*\n" +
      "1. *Mc 29* – 34758 (303y) · 10.00\n" +
      "2. *Mc 61* – 60357 (120y) · 11.00 (HB)\n\n" +
      "📤 *Operan Shift Berikutnya (1 mc)*\n" +
      "• *Mc 76* – 21242 (165y) · Est. 16.20\n" +
      `${DIVIDER}\n` +
      "📊 *Total: 2 doff*";
    expect(shareHistoryText(state)).toBe(expected);
  });
});

describe("shareShiftText", () => {
  it("format shift terarsip", () => {
    const shift: ShiftRecord = {
      id: 5,
      startedAtEpochMin: epochMin(2026, 1, 15, 6, 0),
      endedAtEpochMin: epochMin(2026, 1, 15, 14, 0),
      aktual: [
        { id: 2, mcNo: "61", jam: "11.00", ket: "11.00(HB)", corakOverride: null, customYard: 120, tsEpochMin: null },
        { id: 1, mcNo: "61", jam: "07.00", ket: "07.00", corakOverride: null, customYard: null, tsEpochMin: null },
      ],
      estimasiRemaining: {},
    };

    const expected =
      `*LAPORAN SHIFT 1*\n📅 15/01/2026\n${DIVIDER}\n\n` +
      "✅ *Selesai (2 doff)*\n" +
      "1. *Mc 61* – 60357 (303y) · 07.00\n" +
      "2. *Mc 61* – 60357 (120y) · 11.00 (HB)\n" +
      `${DIVIDER}\n` +
      "📊 *Total: 2 doff*";
    expect(shareShiftText(shift, db)).toBe(expected);
  });
});
