import { useState } from "react";
import {
  ArrowLeftIcon,
  ArrowRightIcon,
  BookOpenIcon,
  CheckIcon,
  CloseIcon,
  ForwardIcon,
  PauseIcon,
  ScheduleIcon,
  ScissorsIcon,
  SwipeIcon,
  TextureIcon,
  TouchAppIcon,
  UndoIcon,
} from "./Icons";

export function OnboardingDialog({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState<"FLOW" | "GESTURE">("FLOW");

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog guide-dialog" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="guide-dialog-header">
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 10,
                background: "color-mix(in srgb, var(--cyan-500) 15%, transparent)",
                color: "var(--cyan-400)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <BookOpenIcon size={18} />
            </div>
            <div>
              <div style={{ fontWeight: 800, fontSize: 16, color: "var(--text-primary)" }}>Panduan Penggunaan</div>
              <div style={{ fontSize: 11, color: "var(--text-faint)" }}>Pelajari cara operasional & gestur cepat Adoel</div>
            </div>
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Tutup Panduan">
            <CloseIcon size={20} />
          </button>
        </div>

        {/* Tab Switcher */}
        <div style={{ padding: "12px 18px 0" }}>
          <div className="guide-tab-bar">
            <button
              type="button"
              className={`guide-tab-btn${tab === "FLOW" ? " active" : ""}`}
              onClick={() => setTab("FLOW")}
            >
              <BookOpenIcon size={15} />
              <span>Alur Operasional</span>
            </button>
            <button
              type="button"
              className={`guide-tab-btn${tab === "GESTURE" ? " active" : ""}`}
              onClick={() => setTab("GESTURE")}
            >
              <SwipeIcon size={15} />
              <span>Gestur Radar</span>
            </button>
          </div>
        </div>

        {/* Body Content */}
        <div className="guide-dialog-body">
          {tab === "FLOW" ? (
            <>
              {/* Estimasi */}
              <div className="guide-row">
                <div className="guide-row-icon" style={{ background: "var(--cyan-600)" }}>
                  <ScheduleIcon size={18} />
                </div>
                <div className="guide-row-content">
                  <div className="guide-row-title-row">
                    <span className="guide-row-title" style={{ color: "var(--cyan-400)" }}>
                      1. Estimasi Waktu Doff
                    </span>
                  </div>
                  <div className="guide-row-desc">
                    Ketik nomor mesin di konsol bawah lalu ketuk tombol <strong>⏱ Estimasi</strong>. Isi sisa menit (<em>Tappet/Cam</em>), yard berjalan (<em>D405</em>), atau jam counter (<em>D408</em>).
                  </div>
                </div>
              </div>

              {/* Doffing */}
              <div className="guide-row">
                <div className="guide-row-icon" style={{ background: "var(--emerald-600)" }}>
                  <ScissorsIcon size={18} />
                </div>
                <div className="guide-row-content">
                  <div className="guide-row-title-row">
                    <span className="guide-row-title" style={{ color: "var(--emerald-400)" }}>
                      2. Potong Kain (Doffing)
                    </span>
                  </div>
                  <div className="guide-row-desc">
                    Ketik nomor mesin lalu tekan tombol <strong>✂ Doffing</strong>. Atau gunakan cara kilat dengan <strong>menggeser kartu mesin</strong> di layar Radar.
                  </div>
                </div>
              </div>

              {/* Jeda Mesin */}
              <div className="guide-row">
                <div className="guide-row-icon" style={{ background: "var(--amber-600)" }}>
                  <PauseIcon size={18} />
                </div>
                <div className="guide-row-content">
                  <div className="guide-row-title-row">
                    <span className="guide-row-title" style={{ color: "var(--amber-400)" }}>
                      3. Jeda Mesin & Macet
                    </span>
                  </div>
                  <div className="guide-row-desc">
                    Jika mesin berhenti atau ada kendala putus lusi, tekan lama kartu mesin lalu pilih <strong>Jeda</strong>. Perhitungan waktu istirahat tetap akurat dan data rol kain dibekukan.
                  </div>
                </div>
              </div>

              {/* Undo / Redo */}
              <div className="guide-row">
                <div className="guide-row-icon" style={{ background: "#d97706" }}>
                  <UndoIcon size={18} />
                </div>
                <div className="guide-row-content">
                  <div className="guide-row-title-row">
                    <span className="guide-row-title" style={{ color: "#fbbf24" }}>
                      4. Urungkan (Undo / Redo)
                    </span>
                  </div>
                  <div className="guide-row-desc">
                    Salah mencatat atau salah hapus? Tekan tombol panah <strong>↩ Urungkan</strong> atau <strong>↪ Ulangi</strong> di sisi kiri konsol bawah untuk mengembalikan data seketika.
                  </div>
                </div>
              </div>

              {/* Operan Shift */}
              <div className="guide-row">
                <div className="guide-row-icon" style={{ background: "#0284c7" }}>
                  <ForwardIcon size={18} />
                </div>
                <div className="guide-row-content">
                  <div className="guide-row-title-row">
                    <span className="guide-row-title" style={{ color: "#38bdf8" }}>
                      5. Operan Antar-Shift
                    </span>
                  </div>
                  <div className="guide-row-desc">
                    Mesin yang jadwal doffing-nya melebihi jam kerja shift saat ini (&gt;8 jam) secara otomatis ditandai sebagai <strong>Operan</strong> agar grafik progres kerja tetap rapi.
                  </div>
                </div>
              </div>
            </>
          ) : (
            <>
              {/* Mini Interactive Preview / Reference */}
              <div className="guide-demo-box">
                <div style={{ fontSize: 11, fontWeight: 700, color: "var(--cyan-400)", letterSpacing: "0.03em" }}>
                  CONTOH ANATOMI KARTU RADAR
                </div>
                <div className="guide-demo-card">
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <span style={{ fontWeight: 900, fontSize: 15, color: "var(--text-primary)" }}>MC 12</span>
                    <span style={{ fontSize: 12, fontWeight: 800, color: "var(--amber-400)" }}>⏱ 02j 40m</span>
                  </div>
                  <div style={{ height: 5, borderRadius: 3, background: "var(--cyan-500)", width: "65%" }} />
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: 11, color: "var(--text-faint)" }}>
                    <span>D408 • Corak 4500</span>
                    <span>300y</span>
                  </div>
                </div>
                <div style={{ fontSize: 11, color: "var(--text-muted)", textAlign: "center" }}>
                  Sentuh, geser, atau tahan kartu untuk aksi instan:
                </div>
              </div>

              {/* Gesture 1: Geser Kanan */}
              <div className="guide-gesture-card">
                <div className="guide-gesture-head">
                  <div className="guide-gesture-action">
                    <span style={{ color: "var(--emerald-400)", display: "flex", alignItems: "center" }}>
                      <ArrowRightIcon size={18} />
                    </span>
                    <span>Geser ke Kanan</span>
                  </div>
                  <span
                    className="guide-gesture-badge"
                    style={{ background: "color-mix(in srgb, var(--emerald-500) 15%, transparent)", color: "var(--emerald-400)" }}
                  >
                    Doffing Normal
                  </span>
                </div>
                <div className="guide-row-desc">
                  Usap kartu ke kanan untuk mencatat <strong>Doffing Normal</strong> saat kain selesai sesuai target yard standar.
                </div>
              </div>

              {/* Gesture 2: Geser Kiri */}
              <div className="guide-gesture-card">
                <div className="guide-gesture-head">
                  <div className="guide-gesture-action">
                    <span style={{ color: "var(--sky-400)", display: "flex", alignItems: "center" }}>
                      <ArrowLeftIcon size={18} />
                    </span>
                    <span>Geser ke Kiri</span>
                  </div>
                  <span
                    className="guide-gesture-badge"
                    style={{ background: "color-mix(in srgb, var(--sky-500) 15%, transparent)", color: "var(--sky-400)" }}
                  >
                    Doffing Matching
                  </span>
                </div>
                <div className="guide-row-desc">
                  Usap kartu ke kiri untuk mencatat <strong>Doffing Matching</strong> (doffing awal pada beam lusi baru untuk potong sampel &amp; cek kualitas kain).
                </div>
              </div>

              {/* Gesture 3: Ketuk Jam */}
              <div className="guide-gesture-card">
                <div className="guide-gesture-head">
                  <div className="guide-gesture-action">
                    <span style={{ color: "var(--cyan-400)", display: "flex", alignItems: "center" }}>
                      <ScheduleIcon size={16} />
                    </span>
                    <span>Ketuk Angka Jam</span>
                  </div>
                  <span
                    className="guide-gesture-badge"
                    style={{ background: "color-mix(in srgb, var(--cyan-500) 15%, transparent)", color: "var(--cyan-400)" }}
                  >
                    Edit Estimasi
                  </span>
                </div>
                <div className="guide-row-desc">
                  Ketuk langsung pada angka jam/sisa waktu untuk memperbarui estimasi doffing.
                </div>
              </div>

              {/* Gesture 4: Ketuk Nomor / Corak */}
              <div className="guide-gesture-card">
                <div className="guide-gesture-head">
                  <div className="guide-gesture-action">
                    <span style={{ color: "#c084fc", display: "flex", alignItems: "center" }}>
                      <TextureIcon size={16} />
                    </span>
                    <span>Ketuk Nomor / Corak</span>
                  </div>
                  <span
                    className="guide-gesture-badge"
                    style={{ background: "color-mix(in srgb, #a855f7 15%, transparent)", color: "#c084fc" }}
                  >
                    Edit Data Mesin
                  </span>
                </div>
                <div className="guide-row-desc">
                  Ketuk nomor mesin atau nama corak untuk mengedit spesifikasi, yard, atau tipe mesin.
                </div>
              </div>

              {/* Gesture 5: Tekan Lama */}
              <div className="guide-gesture-card">
                <div className="guide-gesture-head">
                  <div className="guide-gesture-action">
                    <span style={{ color: "var(--amber-400)", display: "flex", alignItems: "center" }}>
                      <TouchAppIcon size={17} />
                    </span>
                    <span>Tekan Lama (Long-Press)</span>
                  </div>
                  <span
                    className="guide-gesture-badge"
                    style={{ background: "color-mix(in srgb, var(--amber-500) 15%, transparent)", color: "var(--amber-400)" }}
                  >
                    Menu Jeda / Hapus
                  </span>
                </div>
                <div className="guide-row-desc">
                  Tahan sentuhan pada kartu untuk membuka menu cepat Jeda Mesin atau Hapus. Kartu yang dijeda akan dipisahkan ke baris khusus &quot;Dijeda&quot; dan dapat dilanjutkan seketika melalui tombol &quot;▶ Lanjutkan&quot;.
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer Action */}
        <div className="guide-dialog-footer">
          <button
            type="button"
            className="btn primary full"
            style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8, height: 46, borderRadius: 12 }}
            onClick={onClose}
          >
            <CheckIcon size={16} />
            <span>Mengerti & Tutup Panduan</span>
          </button>
        </div>
      </div>
    </div>
  );
}
