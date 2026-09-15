import { useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { formatYard } from "../domain/format";
import { CheckIcon, CloseIcon, EditIcon, RulerIcon, TextureIcon } from "./Icons";
import { CorakShortcutPicker } from "./CorakShortcutPicker";
import type { MesinTipe } from "../domain/types";

const TIPE_LIST: MesinTipe[] = ["TAPPET", "CAM", "D405", "D408"];

/** Jalur cepat untuk field yang paling sering berubah di lantai produksi — tipe mesin,
 * corak & target yard — dijangkau lewat tap kartu radar, tanpa perlu buka Pengaturan >
 * Mesin. Speed/koreksi cuma muncul kalau tipe yang dipilih memang butuh (D405/D408),
 * dan hanya diubah kalau memang disunting — beda tipe yang tidak sedang dipilih tetap
 * mempertahankan nilainya kalau operator berpindah tipe lalu kembali lagi. Mengubah data
 * mesin PERMANEN (sama seperti versi Android), bukan cuma override sekali pakai. */
export function QuickEditDialog({ mcNo, onClose }: { mcNo: string; onClose: () => void }) {
  const { state, setMesin } = useDoffStore();
  const { showToast } = useUiStore();
  const mesin = state.db[mcNo];
  const [tipe, setTipe] = useState<MesinTipe>(mesin?.tipe ?? "TAPPET");
  const [corak, setCorak] = useState(mesin?.corak === "-" ? "" : mesin?.corak ?? "");
  const [targetYard, setTargetYard] = useState(mesin?.targetYard != null ? formatYard(mesin.targetYard) : "");
  const [speedText, setSpeedText] = useState(mesin?.speed != null ? formatYard(mesin.speed) : "");
  const [koreksiText, setKoreksiText] = useState(mesin?.koreksi != null ? formatYard(mesin.koreksi) : "");

  if (!mesin) return null;

  function handleSave() {
    const trimmed = corak.trim() || "-";
    let yard: number | null;
    if (targetYard.trim() === "") {
      yard = null;
    } else {
      const parsed = parseFloat(targetYard.trim().replace(",", "."));
      yard = Number.isNaN(parsed) ? mesin.targetYard : parsed;
    }
    // Tipe yang tidak sedang dipilih tetap mempertahankan speed/koreksi lamanya — cuma
    // tipe yang aktif sekarang yang boleh diubah nilainya, supaya beralih tipe lalu
    // kembali lagi tidak diam-diam menghapus kalibrasi yang sudah ada.
    let speed = mesin.speed;
    if (tipe === "D405") {
      if (speedText.trim() === "") speed = null;
      else {
        const parsed = parseFloat(speedText.trim().replace(",", "."));
        speed = Number.isNaN(parsed) ? mesin.speed : parsed;
      }
    }
    let koreksi = mesin.koreksi;
    if (tipe === "D408") {
      if (koreksiText.trim() === "") koreksi = null;
      else {
        const parsed = parseFloat(koreksiText.trim().replace(",", "."));
        koreksi = Number.isNaN(parsed) ? mesin.koreksi : parsed;
      }
    }
    setMesin(mcNo, { ...mesin, tipe, corak: trimmed, targetYard: yard, speed, koreksi });
    showToast(`Mc ${mcNo} disimpan ✓`);
    onClose();
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div style={{ fontWeight: 800, fontSize: 16, marginBottom: 16, display: "flex", alignItems: "center", gap: 8 }}>
          <EditIcon size={18} />
          <span>Ganti Cepat — Mc {mcNo}</span>
        </div>
        <div className="field-label">Tipe Mesin</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
          {TIPE_LIST.map((t) => (
            <button
              key={t}
              type="button"
              className={`chip-btn${tipe === t ? " active" : ""}`}
              onClick={() => setTipe(t)}
            >
              {t}
            </button>
          ))}
        </div>
        <div className="field-label" style={{ display: "flex", alignItems: "center", gap: 4 }}>
          <TextureIcon size={13} />
          <span>Corak</span>
        </div>
        <input className="field-input" value={corak} onChange={(e) => setCorak(e.target.value.toUpperCase())} />
        <CorakShortcutPicker value={corak} onSelect={setCorak} />
        <div style={{ height: 12 }} />
        <div className="field-label" style={{ display: "flex", alignItems: "center", gap: 4 }}>
          <RulerIcon size={13} />
          <span>Target Yard</span>
        </div>
        <input
          className="field-input"
          placeholder="opsional"
          inputMode="decimal"
          value={targetYard}
          onChange={(e) => setTargetYard(e.target.value)}
        />
        {tipe === "D405" && (
          <>
            <div style={{ height: 12 }} />
            <div className="field-label">Speed (yard/menit)</div>
            <input
              className="field-input"
              placeholder="contoh: 0.158"
              inputMode="decimal"
              value={speedText}
              onChange={(e) => setSpeedText(e.target.value)}
            />
          </>
        )}
        {tipe === "D408" && (
          <>
            <div style={{ height: 12 }} />
            <div className="field-label">Koreksi Counter (menit)</div>
            <input
              className="field-input"
              placeholder="contoh: 0 atau -15"
              inputMode="decimal"
              value={koreksiText}
              onChange={(e) => setKoreksiText(e.target.value)}
            />
          </>
        )}
        <div className="actions" style={{ marginTop: 18 }}>
          <button className="cancel" onClick={onClose} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
            <CloseIcon size={14} />
            <span>Batal</span>
          </button>
          <button
            className="confirm"
            style={{ background: "var(--cyan-600)", display: "inline-flex", alignItems: "center", gap: 5 }}
            onClick={handleSave}
          >
            <CheckIcon size={14} />
            <span>Simpan</span>
          </button>
        </div>
      </div>
    </div>
  );
}

