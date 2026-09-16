import { useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { formatYard, minOfDayToTimeStr } from "../domain/format";
import { parseJam, standarisasiKeterangan } from "../domain/parse";
import { CloseIcon } from "./Icons";
import { CorakShortcutPicker, KeteranganShortcutPicker } from "./ShortcutPicker";

export function TambahAktualDialog({
  onClose,
  onSave,
}: {
  onClose: () => void;
  onSave: (mcNo: string, jam: string, ket: string, corakOverride: string | null, customYard: number | null) => void;
}) {
  const { state } = useDoffStore();
  const { showToast } = useUiStore();
  const [mcNo, setMcNo] = useState("");
  const [jam, setJam] = useState("");
  const [corak, setCorak] = useState("");
  const [yard, setYard] = useState("");
  const [extraKet, setExtraKet] = useState("");

  const mesin = state.db[mcNo.trim()];

  function handleMcNoChange(val: string) {
    const cleaned = val.replace(/\D/g, "").slice(0, 4);
    setMcNo(cleaned);
    const m = state.db[cleaned];
    if (m) {
      if (!corak) setCorak(m.corak !== "-" ? m.corak : "");
      if (!yard) setYard(m.targetYard != null ? formatYard(m.targetYard) : "");
    }
  }

  function handleSave() {
    const mcTrim = mcNo.trim();
    // No db membership check — mcNo here is only ever used for prefill convenience (mesin
    // above) and the AktualEntry record itself, neither of which needs the machine to already
    // exist in db. Requiring pre-registration would just be the same artificial cap the
    // console dropped for live entries.
    if (!mcTrim) {
      showToast("⚠ Nomor mesin wajib diisi");
      return;
    }
    const jamMin = parseJam(jam.trim());
    if (jamMin === null) {
      showToast("⚠ Jam tidak valid — format 14.30");
      return;
    }
    let yardVal: number | null = null;
    if (yard.trim() !== "") {
      const parsed = parseFloat(yard.trim().replace(",", "."));
      if (Number.isNaN(parsed)) {
        showToast("⚠ Yard tidak valid");
        return;
      }
      yardVal = parsed;
    }

    const jamStr = minOfDayToTimeStr(jamMin);
    const extra = standarisasiKeterangan(extraKet.trim());
    const newKet = extra.length > 0 ? `${jamStr}(${extra})` : jamStr;
    const corakTrim = corak.trim();
    const corakOverride = corakTrim.length > 0 && corakTrim !== mesin?.corak ? corakTrim : null;

    onSave(mcTrim, jamStr, newKet, corakOverride, yardVal);
    onClose();
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <div style={{ fontWeight: 800, fontSize: 18 }}>Tambah Potongan</div>
          <button className="icon-btn" onClick={onClose} aria-label="Tutup">
            <CloseIcon />
          </button>
        </div>

        <div className="field-label">Nomor Mesin</div>
        <input
          className="field-input"
          placeholder="contoh: 12"
          inputMode="numeric"
          autoFocus
          value={mcNo}
          onChange={(e) => handleMcNoChange(e.target.value)}
        />

        <div style={{ height: 12 }} />
        <div className="field-label">Jam Selesai</div>
        <input
          className="field-input"
          placeholder="14.30"
          inputMode="decimal"
          value={jam}
          onChange={(e) => setJam(e.target.value)}
        />

        <div style={{ height: 12 }} />
        <div className="field-label">Keterangan (opsional)</div>
        <input
          className="field-input"
          placeholder="contoh: putus pakan / ganti beam"
          value={extraKet}
          onChange={(e) => setExtraKet(e.target.value)}
        />
        <KeteranganShortcutPicker value={extraKet} onSelect={setExtraKet} />

        <div style={{ height: 12 }} />
        <div className="field-grid">
          <div>
            <div className="field-label">Corak</div>
            <input
              className="field-input"
              value={corak}
              placeholder={mesin?.corak ?? "-"}
              onChange={(e) => setCorak(e.target.value.toUpperCase())}
            />
            <CorakShortcutPicker value={corak} onSelect={setCorak} />
          </div>
          <div>
            <div className="field-label">Yard (opsional)</div>
            <input
              className="field-input"
              inputMode="decimal"
              placeholder={mesin?.targetYard ? formatYard(mesin.targetYard) : "opsional"}
              value={yard}
              onChange={(e) => setYard(e.target.value)}
            />
          </div>
        </div>

        <div className="actions" style={{ marginTop: 20 }}>
          <button className="cancel" onClick={onClose}>
            Batal
          </button>
          <button className="confirm" style={{ background: "var(--cyan-600)" }} onClick={handleSave}>
            Simpan
          </button>
        </div>
      </div>
    </div>
  );
}
