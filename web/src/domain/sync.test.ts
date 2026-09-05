import { describe, expect, it } from "vitest";
import { buildDefaultDb } from "./defaultDb";
import { prepareHandoverData, prepareMasterDbData, processScannedQr } from "./sync";
import type { DoffState } from "./types";

describe("sync domain module", () => {
  const baseState: DoffState = {
    db: buildDefaultDb(),
    estimasi: {
      "12": {
        mcNo: "12",
        estAbsMin: 800,
        startAbsMin: 700,
        corakOverride: null,
        yardOverride: null,
        pausedAtAbsMin: null,
      },
    },
    aktual: [
      {
        id: 1,
        mcNo: "10",
        jam: "14.30",
        ket: "14.30",
        corakOverride: "4500",
        customYard: 120,
        tsEpochMin: 870,
      },
    ],
    nextId: 2,
    themeMode: "DARK",
    history: [],
    nextShiftId: 1,
    onboardingSeen: true,
  };

  it("encodes and decodes handover QR data correctly", () => {
    // estAbsMin 800 > shiftEndAbs (start 700 + 480 = 1180, or nowAbs 200 -> shiftStart 0 -> shiftEnd 480)
    const qrData = prepareHandoverData(baseState, 200);
    expect(qrData).toContain('"type":"HANDOVER"');

    const receiverState: DoffState = {
      db: buildDefaultDb(),
      estimasi: {},
      aktual: [],
      nextId: 1,
      themeMode: "SYSTEM",
      history: [],
      nextShiftId: 1,
      onboardingSeen: true,
    };

    const result = processScannedQr(qrData, receiverState);
    expect(result).not.toBeNull();
    const merged = result!.state;
    expect(merged?.estimasi["12"]).toBeDefined();
    expect(merged?.estimasi["12"].estAbsMin).toBe(800);
    expect(merged?.db["12"]).toBeDefined();
  });

  it("encodes and decodes master DB QR data correctly", () => {
    const qrData = prepareMasterDbData(baseState);
    expect(qrData).toContain('"type":"MASTER_DB"');

    const receiverState: DoffState = {
      db: {},
      estimasi: {},
      aktual: [],
      nextId: 1,
      themeMode: "SYSTEM",
      history: [],
      nextShiftId: 1,
      onboardingSeen: true,
    };

    const result = processScannedQr(qrData, receiverState);
    expect(result).not.toBeNull();
    const merged = result!.state;
    expect(merged?.db["1"]).toBeDefined();
  });

  it("rejects invalid QR payload gracefully", () => {
    expect(processScannedQr("invalid string", baseState)).toBeNull();
    expect(processScannedQr(JSON.stringify({ type: "UNKNOWN", payload: "abc" }), baseState)).toBeNull();
  });

  // Regresi untuk laporan "QR sync tidak valid saat membagikan lewat teks": aplikasi pesan
  // pihak ketiga (WhatsApp, Notes, dst.) kadang menyisipkan baris baru/karakter tak-terlihat
  // saat teksnya di-reflow atau disalin ulang dari tampilan yang sudah word-wrap. Envelope-nya
  // sendiri tidak pernah mengandung whitespace sama sekali, jadi semuanya aman dibuang.
  it("tetap terima QR data yang disisipi newline/whitespace di tengah (mis. dari copy-paste aplikasi pesan)", () => {
    const qrData = prepareHandoverData(baseState, 200);
    const mangled = qrData.slice(0, 20) + "\n \t" + qrData.slice(20);

    const receiverState: DoffState = {
      db: buildDefaultDb(),
      estimasi: {},
      aktual: [],
      nextId: 1,
      themeMode: "SYSTEM",
      history: [],
      nextShiftId: 1,
      onboardingSeen: true,
    };

    const result = processScannedQr(mangled, receiverState);
    expect(result).not.toBeNull();
    expect(result!.state.estimasi["12"]?.estAbsMin).toBe(800);
  });

  it("tetap terima QR data yang disisipi zero-width space/BOM", () => {
    const qrData = prepareHandoverData(baseState, 200);
    const mangled = "\uFEFF" + qrData.slice(0, 10) + "\u200B" + qrData.slice(10);

    const result = processScannedQr(mangled, baseState);
    expect(result).not.toBeNull();
  });

  // Paritas dengan prepareHandoverData di DoffRepository.kt (Android): kalau tidak ada estimasi
  // yang lolos ke shift berikutnya, "Oper Shift" tetap membagikan semua estimasi yang ada
  // sekarang — bukan payload kosong yang membuat kedua platform berbeda hasil pada kondisi sama.
  it("Oper Shift membagikan semua estimasi saat ini kalau tidak ada yang lolos ke shift berikutnya", () => {
    // nowAbs jauh di depan estAbsMin (800) manapun batas shift dihitung (offset zona waktu
    // paling ekstrem pun cuma bergeser ±840 menit) — menjamin baseState.estimasi["12"] TIDAK
    // pernah lolos sebagai "shift berikutnya", memaksa jalur fallback ini teruji, bukan cuma
    // kebetulan lolos di satu zona waktu tertentu.
    const qrData = prepareHandoverData(baseState, 100_000);
    const receiverState: DoffState = {
      db: buildDefaultDb(),
      estimasi: {},
      aktual: [],
      nextId: 1,
      themeMode: "SYSTEM",
      history: [],
      nextShiftId: 1,
      onboardingSeen: true,
    };

    const result = processScannedQr(qrData, receiverState);
    expect(result).not.toBeNull();
    expect(result!.state.estimasi["12"]?.estAbsMin).toBe(800);
  });
});
