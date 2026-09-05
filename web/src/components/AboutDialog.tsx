import { CloseIcon } from "./Icons";

export function AboutDialog({ onClose }: { onClose: () => void }) {
  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 360, textAlign: "center" }}>
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <button className="icon-btn" onClick={onClose} aria-label="Tutup">
            <CloseIcon />
          </button>
        </div>
        <div style={{ fontWeight: 900, fontSize: 28, letterSpacing: "-0.03em", marginBottom: 4 }}>
          Adoel<span style={{ color: "var(--cyan-400)" }}>.</span>
        </div>
        <div style={{ fontSize: 13, color: "var(--text-faint)", marginBottom: 16 }}>
          Aplikasi Estimasi Doff & Manajemen Mesin Tenun
        </div>
        <div style={{ fontSize: 12, color: "var(--text-muted)", background: "var(--bg-elevated-2)", padding: 12, borderRadius: 10, marginBottom: 20 }}>
          Versi 10.3.0 · Web Edition
          <div style={{ fontSize: 11, color: "var(--text-faint)", marginTop: 4 }}>
            Mendukung sinkronisasi QR dua arah dengan versi Android
          </div>
        </div>
        <button
          className="confirm"
          style={{ width: "100%", background: "var(--cyan-600)" }}
          onClick={onClose}
        >
          Tutup
        </button>
      </div>
    </div>
  );
}
