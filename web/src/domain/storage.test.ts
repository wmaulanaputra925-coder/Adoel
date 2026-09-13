import { describe, expect, it } from "vitest";
import { parseBackupJson, serializeState } from "./storage";
import type { DoffState } from "./types";

// Port dari SerializationTest.kt — kontrak backup JSON yang interchangeable dengan aplikasi
// Android. Roundtrip harus lossless dan format lama harus terbaca dengan default yang benar.

function fullState(): DoffState {
  return {
    db: {
      "29": { tipe: "TAPPET", corak: "34758", targetYard: 303, speed: null, koreksi: null },
      "61": { tipe: "D405", corak: "60357", targetYard: 303, speed: 0.158, koreksi: null },
      "76": { tipe: "CAM", corak: "21242", targetYard: 165, speed: null, koreksi: null },
      "79": { tipe: "D408", corak: "60357", targetYard: 303, speed: null, koreksi: 18 },
    },
    estimasi: {
      "29": { mcNo: "29", estAbsMin: 29_726_400, startAbsMin: 29_726_300, corakOverride: null, yardOverride: null, pausedAtAbsMin: null },
      "61": { mcNo: "61", estAbsMin: 29_726_500, startAbsMin: 29_726_310, corakOverride: "99999", yardOverride: 250, pausedAtAbsMin: null },
    },
    aktual: [
      { id: 7, mcNo: "76", jam: "13.49", ket: "13.49(HB)", corakOverride: null, customYard: 116, tsEpochMin: 29_726_329 },
      { id: 6, mcNo: "29", jam: "12.04", ket: "12.04", corakOverride: null, customYard: null, tsEpochMin: null },
    ],
    nextId: 8,
    themeMode: "DARK",
    history: [
      {
        id: 3,
        startedAtEpochMin: 29_725_873,
        endedAtEpochMin: 29_726_441,
        aktual: [{ id: 5, mcNo: "61", jam: "10.00", ket: "10.00", corakOverride: null, customYard: null, tsEpochMin: null }],
        estimasiRemaining: {
          "79": { mcNo: "79", estAbsMin: 29_726_600, startAbsMin: 29_726_000, corakOverride: null, yardOverride: null, pausedAtAbsMin: null },
        },
      },
    ],
    nextShiftId: 4,
    onboardingSeen: false,
    keteranganShortcuts: ["HB", "P.LP", "P.SN", "P.OH", "P.EL", "P.Sel"],
    corakShortcuts: ["4500", "5000"],
    corakPotonganAwal: ["80125", "21242", "66335"],
  };
}

describe("parseBackupJson", () => {
  it("roundtrip export→parse lossless", () => {
    const s = fullState();
    expect(parseBackupJson(serializeState(s))).toEqual(s);
  });

  it("JSON lama tanpa field baru → default benar", () => {
    const legacy = '{"db":{"29":{"tipe":"TAPPET","corak":"34758","targetYard":303}},"estimasi":{},"aktual":[],"nextId":5}';
    const p = parseBackupJson(legacy)!;
    expect(p.themeMode).toBe("SYSTEM");
    expect(p.history).toEqual([]);
    expect(p.nextShiftId).toBe(1);
    expect(p.onboardingSeen).toBe(true);
    expect(p.db["29"].targetYard).toBe(303);
    expect(p.keteranganShortcuts).toEqual([]);
    expect(p.corakShortcuts).toEqual([]);
    // Beda dari corakShortcuts (default []): backup lama tanpa field ini masih dapat aturan
    // kualitas 3-corak standar, bukan daftar kosong yang mematikan pengingatnya diam-diam.
    expect(p.corakPotonganAwal).toEqual(["80125", "21242", "66335"]);
  });

  it("id aktual duplikat di-reassign, bukan dibuang", () => {
    const json =
      '{"db":{"29":{"tipe":"TAPPET","corak":"34758"}},"estimasi":{},"aktual":[{"id":5,"mcNo":"29","jam":"10.00","ket":"10.00"},{"id":5,"mcNo":"31","jam":"11.00","ket":"11.00"}],"nextId":6}';
    const p = parseBackupJson(json)!;
    expect(p.aktual.length).toBe(2);
    expect(p.aktual[0].id).not.toBe(p.aktual[1].id);
  });

  it("nextId basi dinaikkan di atas max id aktual", () => {
    const json =
      '{"db":{"29":{"tipe":"TAPPET","corak":"34758"}},"estimasi":{},"aktual":[{"id":7,"mcNo":"29","jam":"10.00","ket":"10.00"}],"nextId":1}';
    expect(parseBackupJson(json)!.nextId).toBe(8);
  });

  it("tipe mesin tak dikenal → fallback TAPPET", () => {
    const json = '{"db":{"29":{"tipe":"QUANTUM","corak":"34758"}},"estimasi":{},"aktual":[],"nextId":1}';
    expect(parseBackupJson(json)!.db["29"].tipe).toBe("TAPPET");
  });

  it("JSON tidak valid / db kosong → null (tidak menimpa dengan state kosong)", () => {
    expect(parseBackupJson("bukan json sama sekali")).toBeNull();
    expect(parseBackupJson("{}")).toBeNull();
    expect(parseBackupJson('{"db":{},"estimasi":{},"aktual":[],"nextId":1}')).toBeNull();
  });
});
