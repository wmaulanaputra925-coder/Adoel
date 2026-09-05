import { useState } from "react";
import { BadgeIcon, CheckIcon, CloseIcon } from "./Icons";

/**
 * Nama operator & grupnya. Ditanyakan sekali saat aplikasi pertama kali dibuka dan bisa dibuka
 * lagi kapan saja dari Pengaturan — datanya dipakai sebagai kepala teks bagikan, supaya rekan
 * yang menerima laporan di WhatsApp langsung tahu itu laporan siapa.
 *
 * Boleh dilewati: aplikasinya tetap jalan penuh tanpa identitas, teks bagikannya saja yang tidak
 * mencantumkan baris operator. Karena itu tidak ada validasi yang memblokir — memaksa isi di
 * layar pertama hanya jadi penghalang buat operator yang cuma ingin cepat mencatat doffing.
 *
 * Port 1:1 dari OperatorDialog.kt (aplikasi Android).
 */
export function OperatorDialog({
  nama,
  grup,
  onClose,
  onSave,
  isFirstLaunch = false,
}: {
  nama: string;
  grup: string;
  onClose: () => void;
  onSave: (nama: string, grup: string) => void;
  isFirstLaunch?: boolean;
}) {
  const [namaInput, setNamaInput] = useState(nama);
  const [grupInput, setGrupInput] = useState(grup);

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div style={{ fontWeight: 800, fontSize: 16, display: "flex", alignItems: "center", gap: 8 }}>
          <BadgeIcon size={18} />
          <span>{isFirstLaunch ? "Selamat Datang di Adoel" : "Identitas Operator"}</span>
        </div>
        <div style={{ fontSize: 12, color: "var(--text-faint)", margin: "8px 0 16px", lineHeight: 1.5 }}>
          Nama dan grup kamu dicantumkan di kepala teks laporan yang dibagikan ke WhatsApp. Bisa diubah kapan saja lewat
          Pengaturan.
        </div>

        <div className="field-label">Nama Operator</div>
        <input
          className="field-input"
          placeholder="mis. Wahyu Maulana"
          value={namaInput}
          onChange={(e) => setNamaInput(e.target.value)}
          autoComplete="off"
        />

        <div style={{ height: 12 }} />

        <div className="field-label">Grup</div>
        <input
          className="field-input"
          placeholder="mis. B"
          value={grupInput}
          onChange={(e) => setGrupInput(e.target.value.toUpperCase())}
          autoComplete="off"
        />

        <div className="actions" style={{ marginTop: 18 }}>
          <button className="cancel" onClick={onClose} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
            <CloseIcon size={14} />
            <span>{isFirstLaunch ? "Lewati" : "Batal"}</span>
          </button>
          <button
            className="confirm"
            style={{ background: "var(--cyan-600)", display: "inline-flex", alignItems: "center", gap: 5 }}
            onClick={() => onSave(namaInput.trim(), grupInput.trim())}
          >
            <CheckIcon size={14} />
            <span>Simpan</span>
          </button>
        </div>
      </div>
    </div>
  );
}
