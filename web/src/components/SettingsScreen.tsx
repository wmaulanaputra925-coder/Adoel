import { useRef, useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { DEFAULT_CORAK_POTONGAN_AWAL, DEFAULT_CORAK_SHORTCUTS, DEFAULT_KETERANGAN_SHORTCUTS } from "../domain/types";
import {
  AddIcon,
  BadgeIcon,
  BookOpenIcon,
  CloseIcon,
  DatabaseIcon,
  DeleteIcon,
  DownloadIcon,
  EditIcon,
  InfoIcon,
  MonitorIcon,
  MoonIcon,
  ResetIcon,
  ScissorsIcon,
  SunIcon,
  TagIcon,
  TextureIcon,
  UploadIcon,
  WarningIcon,
} from "./Icons";
import { AboutDialog } from "./AboutDialog";
import { OperatorDialog } from "./OperatorDialog";

export function SettingsScreen({ onClose, onOpenHelp }: { onClose: () => void; onOpenHelp: () => void }) {
  const {
    state,
    resetDb,
    setThemeMode,
    setOperator,
    exportJson,
    importJson,
    addKeteranganShortcut,
    removeKeteranganShortcut,
    resetKeteranganShortcuts,
    addCorakShortcut,
    removeCorakShortcut,
    resetCorakShortcuts,
    addCorakPotonganAwal,
    removeCorakPotonganAwal,
    resetCorakPotonganAwal,
  } = useDoffStore();
  const { showToast, showConfirm } = useUiStore();
  const [aboutOpen, setAboutOpen] = useState(false);
  const [operatorEditing, setOperatorEditing] = useState(false);
  const [newShortcut, setNewShortcut] = useState("");
  const [newCorakShortcut, setNewCorakShortcut] = useState("");
  const [newPotonganAwal, setNewPotonganAwal] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const shortcuts = state.keteranganShortcuts ?? DEFAULT_KETERANGAN_SHORTCUTS;
  const corakShortcuts = state.corakShortcuts ?? DEFAULT_CORAK_SHORTCUTS;
  const corakPotonganAwal = state.corakPotonganAwal ?? DEFAULT_CORAK_POTONGAN_AWAL;

  function handleAddShortcut() {
    const trimmed = newShortcut.trim().toUpperCase();
    if (!trimmed) return;
    if (shortcuts.includes(trimmed)) {
      showToast(`Shortcut "${trimmed}" sudah ada di daftar`);
      return;
    }
    addKeteranganShortcut(trimmed);
    setNewShortcut("");
    showToast(`Shortcut "${trimmed}" ditambahkan ✓`);
  }

  function handleAddCorakShortcut() {
    const trimmed = newCorakShortcut.trim().toUpperCase();
    if (!trimmed) return;
    if (corakShortcuts.includes(trimmed)) {
      showToast(`Shortcut corak "${trimmed}" sudah ada di daftar`);
      return;
    }
    addCorakShortcut(trimmed);
    setNewCorakShortcut("");
    showToast(`Shortcut corak "${trimmed}" ditambahkan ✓`);
  }

  function handleAddCorakPotonganAwal() {
    const trimmed = newPotonganAwal.trim().toUpperCase();
    if (!trimmed) return;
    if (corakPotonganAwal.includes(trimmed)) {
      showToast(`Corak "${trimmed}" sudah ada di daftar`);
      return;
    }
    addCorakPotonganAwal(trimmed);
    setNewPotonganAwal("");
    showToast(`Corak "${trimmed}" ditambahkan ke daftar potongan awal 70y ✓`);
  }

  function handleExport() {
    const json = exportJson();
    const stamp = new Date().toISOString().replace(/[-:]/g, "").replace("T", "-").slice(0, 13);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `adoel-backup-${stamp}.json`;
    a.click();
    URL.revokeObjectURL(url);
    showToast("Data dicadangkan ✓");
  }

  function handleImportFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result ?? "");
      showConfirm("Pulihkan dari file ini? Data yang ada sekarang akan ditimpa.", () => {
        const parsed = importJson(text);
        showToast(parsed ? "Data dipulihkan ✓" : "⚠ File bukan cadangan Adoel yang valid");
      });
    };
    reader.onerror = () => showToast("⚠ Gagal membaca file");
    reader.readAsText(file);
  }

  function handleResetAllDb() {
    showConfirm("Reset semua data ke default? Estimasi & riwayat akan hilang.", () => {
      resetDb();
      showToast("Data direset ke default");
    });
  }

  return (
    <div className="overlay">
      <div className="overlay-header">
        <h2>Pengaturan</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Tutup">
          <CloseIcon />
        </button>
      </div>

      <div className="overlay-body" style={{ paddingBottom: 32 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Card: Identitas Operator — didata sekali saat pertama buka (OperatorDialog), diubah
              dari sini kapan saja. Yang dibaca teks bagikan, bukan sekadar catatan: ditaruh
              paling atas supaya operator yang laporannya "tanpa nama" langsung menemukan tempat
              mengisinya. */}
          <div className="settings-section-card">
            <div className="settings-section-header">
              <BadgeIcon size={16} />
              <span>Identitas Operator</span>
            </div>
            <div className="settings-section-desc">Dicantumkan di kepala teks laporan yang dibagikan ke WhatsApp.</div>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10 }}>
              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    fontSize: 14,
                    fontWeight: 800,
                    color: state.operatorNama ? "var(--text-primary)" : "var(--text-faint)",
                  }}
                >
                  {state.operatorNama || "Belum diisi"}
                </div>
                <div style={{ fontSize: 12, color: "var(--text-faint)" }}>
                  {state.operatorGrup ? `Grup ${state.operatorGrup}` : "Grup belum diisi"}
                </div>
              </div>
              <button
                className="chip-btn"
                onClick={() => setOperatorEditing(true)}
                style={{ display: "inline-flex", alignItems: "center", gap: 5, flexShrink: 0 }}
              >
                <EditIcon size={13} />
                <span>Ubah</span>
              </button>
            </div>
          </div>

          {/* Card: Tema Aplikasi */}
          <div className="settings-section-card">
            <div className="settings-section-header">
              <SunIcon size={16} />
              <span>Tema Tampilan</span>
            </div>
            <div className="settings-section-desc">Pilih tema antarmuka yang nyaman untuk operasional kerja.</div>
            <div className="settings-theme-selector">
              <button
                className={`settings-theme-btn${state.themeMode === "SYSTEM" ? " active" : ""}`}
                onClick={() => setThemeMode("SYSTEM")}
              >
                <MonitorIcon size={15} />
                <span>Sistem</span>
              </button>
              <button
                className={`settings-theme-btn${state.themeMode === "DARK" ? " active" : ""}`}
                onClick={() => setThemeMode("DARK")}
              >
                <MoonIcon size={15} />
                <span>Gelap</span>
              </button>
              <button
                className={`settings-theme-btn${state.themeMode === "LIGHT" ? " active" : ""}`}
                onClick={() => setThemeMode("LIGHT")}
              >
                <SunIcon size={15} />
                <span>Terang</span>
              </button>
            </div>
          </div>

          {/* Card: Shortcut Keterangan Doffing */}
          <div className="settings-section-card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div className="settings-section-header" style={{ margin: 0, display: "flex", alignItems: "center", gap: 8 }}>
                <div style={{ padding: 6, borderRadius: 6, background: "rgba(6, 182, 212, 0.15)", color: "var(--cyan-400)", display: "flex" }}>
                  <TagIcon size={16} />
                </div>
                <span>Shortcut Keterangan</span>
                <span className="meta-tag" style={{ marginLeft: 4 }}>
                  {shortcuts.length} shortcut
                </span>
              </div>
              {shortcuts.length > 0 && (
                <button
                  type="button"
                  className="btn-link"
                  aria-label="Hapus semua shortcut keterangan"
                  title="Hapus semua shortcut keterangan"
                  style={{ fontSize: 12, color: "var(--red-400)", display: "inline-flex", alignItems: "center", gap: 3 }}
                  onClick={() => {
                    showConfirm("Hapus semua daftar shortcut keterangan?", () => {
                      resetKeteranganShortcuts();
                      showToast("Daftar shortcut keterangan dikosongkan ✓");
                    });
                  }}
                >
                  <DeleteIcon size={14} />
                </button>
              )}
            </div>
            <div className="settings-section-desc" style={{ marginTop: 8 }}>
              Tombol cepat keterangan untuk pencatatan Doffing (cth: HB, P.LP, P.SN, GANTI BEAM).
            </div>

            {shortcuts.length === 0 ? (
              <div
                style={{
                  border: "1px dashed var(--border-subtle)",
                  borderRadius: 8,
                  padding: "12px 14px",
                  margin: "8px 0",
                  textAlign: "center",
                  fontSize: 12,
                  color: "var(--text-faint)",
                  background: "var(--bg-elevated)",
                }}
              >
                Belum ada shortcut keterangan. Tambahkan teks di bawah untuk membuat tombol cepat.
              </div>
            ) : (
              <div className="chip-row-wrap" style={{ marginTop: 8, marginBottom: 4 }}>
                {shortcuts.map((code) => (
                  <div
                    key={code}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      background: "var(--bg-elevated-2)",
                      border: "1px solid var(--border-subtle)",
                      borderRadius: 6,
                      padding: "3px 6px 3px 10px",
                      gap: 6,
                      fontSize: 12,
                      fontWeight: 600,
                      color: "var(--text-primary)",
                    }}
                  >
                    <span>{code}</span>
                    <button
                      type="button"
                      aria-label={`Hapus shortcut ${code}`}
                      title={`Hapus shortcut ${code}`}
                      onClick={() => {
                        removeKeteranganShortcut(code);
                        showToast(`Shortcut "${code}" dihapus`);
                      }}
                      style={{
                        background: "transparent",
                        color: "var(--text-faint)",
                        border: "none",
                        borderRadius: "50%",
                        width: 18,
                        height: 18,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        cursor: "pointer",
                        padding: 0,
                        fontSize: 12,
                        lineHeight: 1,
                        transition: "color 0.15s, background 0.15s",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.color = "#ef4444";
                        e.currentTarget.style.background = "rgba(239, 68, 68, 0.15)";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.color = "var(--text-faint)";
                        e.currentTarget.style.background = "transparent";
                      }}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div style={{ display: "flex", gap: 6, marginTop: 10 }}>
              <input
                className="field-input"
                style={{ flex: 1, padding: "8px 12px", fontSize: 13 }}
                placeholder="Tambah keterangan baru (cth: GANTI BEAM)"
                value={newShortcut}
                onChange={(e) => setNewShortcut(e.target.value.toUpperCase())}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    handleAddShortcut();
                  }
                }}
              />
              <button
                className="btn primary"
                style={{ padding: "8px 16px", fontSize: 13, display: "inline-flex", alignItems: "center", gap: 4 }}
                disabled={!newShortcut.trim()}
                onClick={handleAddShortcut}
              >
                <AddIcon size={14} />
                <span>Tambah</span>
              </button>
            </div>
          </div>

          {/* Card: Shortcut Kode Corak */}
          <div className="settings-section-card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div className="settings-section-header" style={{ margin: 0, display: "flex", alignItems: "center", gap: 8 }}>
                <div style={{ padding: 6, borderRadius: 6, background: "rgba(16, 185, 129, 0.15)", color: "var(--emerald-400)", display: "flex" }}>
                  <TextureIcon size={16} />
                </div>
                <span>Shortcut Kode Corak</span>
                <span className="meta-tag" style={{ marginLeft: 4 }}>
                  {corakShortcuts.length} shortcut
                </span>
              </div>
              {corakShortcuts.length > 0 && (
                <button
                  type="button"
                  className="btn-link"
                  aria-label="Hapus semua shortcut corak"
                  title="Hapus semua shortcut corak"
                  style={{ fontSize: 12, color: "var(--red-400)", display: "inline-flex", alignItems: "center", gap: 3 }}
                  onClick={() => {
                    showConfirm("Hapus semua daftar shortcut kode corak?", () => {
                      resetCorakShortcuts();
                      showToast("Daftar shortcut corak dikosongkan ✓");
                    });
                  }}
                >
                  <DeleteIcon size={14} />
                </button>
              )}
            </div>
            <div className="settings-section-desc" style={{ marginTop: 8 }}>
              Tombol cepat kode corak/kain untuk formulir mesin dan penggantian corak (cth: 4500, 4505, 5000, RAYON-30).
            </div>

            {corakShortcuts.length === 0 ? (
              <div
                style={{
                  border: "1px dashed var(--border-subtle)",
                  borderRadius: 8,
                  padding: "12px 14px",
                  margin: "8px 0",
                  textAlign: "center",
                  fontSize: 12,
                  color: "var(--text-faint)",
                  background: "var(--bg-elevated)",
                }}
              >
                Belum ada shortcut kode corak. Tambahkan kode corak di bawah untuk membuat tombol cepat.
              </div>
            ) : (
              <div className="chip-row-wrap" style={{ marginTop: 8, marginBottom: 4 }}>
                {corakShortcuts.map((code) => (
                  <div
                    key={code}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      background: "var(--bg-elevated-2)",
                      border: "1px solid var(--border-subtle)",
                      borderRadius: 6,
                      padding: "3px 6px 3px 10px",
                      gap: 6,
                      fontSize: 12,
                      fontWeight: 600,
                      color: "var(--text-primary)",
                    }}
                  >
                    <span>{code}</span>
                    <button
                      type="button"
                      aria-label={`Hapus shortcut corak ${code}`}
                      title={`Hapus shortcut corak ${code}`}
                      onClick={() => {
                        removeCorakShortcut(code);
                        showToast(`Shortcut corak "${code}" dihapus`);
                      }}
                      style={{
                        background: "transparent",
                        color: "var(--text-faint)",
                        border: "none",
                        borderRadius: "50%",
                        width: 18,
                        height: 18,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        cursor: "pointer",
                        padding: 0,
                        fontSize: 12,
                        lineHeight: 1,
                        transition: "color 0.15s, background 0.15s",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.color = "#ef4444";
                        e.currentTarget.style.background = "rgba(239, 68, 68, 0.15)";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.color = "var(--text-faint)";
                        e.currentTarget.style.background = "transparent";
                      }}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div style={{ display: "flex", gap: 6, marginTop: 10 }}>
              <input
                className="field-input"
                style={{ flex: 1, padding: "8px 12px", fontSize: 13 }}
                placeholder="Tambah kode corak baru (cth: 4520 / RAYON)"
                value={newCorakShortcut}
                onChange={(e) => setNewCorakShortcut(e.target.value.toUpperCase())}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    handleAddCorakShortcut();
                  }
                }}
              />
              <button
                className="btn primary"
                style={{ padding: "8px 16px", fontSize: 13, display: "inline-flex", alignItems: "center", gap: 4 }}
                disabled={!newCorakShortcut.trim()}
                onClick={handleAddCorakShortcut}
              >
                <AddIcon size={14} />
                <span>Tambah</span>
              </button>
            </div>
          </div>

          {/* Card: Corak Potongan Awal 70y */}
          <div className="settings-section-card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div className="settings-section-header" style={{ margin: 0, display: "flex", alignItems: "center", gap: 8 }}>
                <div style={{ padding: 6, borderRadius: 6, background: "rgba(245, 158, 11, 0.15)", color: "var(--amber-400)", display: "flex" }}>
                  <ScissorsIcon size={16} />
                </div>
                <span>Corak Potongan Awal 70y</span>
                <span className="meta-tag" style={{ marginLeft: 4 }}>
                  {corakPotonganAwal.length} corak
                </span>
              </div>
              {corakPotonganAwal.length > 0 && (
                <button
                  type="button"
                  className="btn-link"
                  aria-label="Setel daftar corak potongan awal ke default"
                  title="Setel daftar corak potongan awal ke default"
                  style={{ fontSize: 12, color: "var(--text-faint)", display: "inline-flex", alignItems: "center", gap: 3 }}
                  onClick={() => {
                    showConfirm("Kembalikan daftar ke 3 corak standar (80125, 21242, 66335)?", () => {
                      resetCorakPotonganAwal();
                      showToast("Daftar dikembalikan ke default ✓");
                    });
                  }}
                >
                  <ResetIcon size={14} />
                </button>
              )}
            </div>
            <div className="settings-section-desc" style={{ marginTop: 8 }}>
              Untuk corak di daftar ini, sampel Doffing Matching (1 yard) baru diambil setelah beam
              jalan minimal 70 yard — bukan langsung dari 0 — supaya sampel tidak kena cacat LTK/lusi
              putus di awal jalan. Pengingat ini muncul saat memilih aksi Doffing Matching.
            </div>

            {corakPotonganAwal.length === 0 ? (
              <div
                style={{
                  border: "1px dashed var(--border-subtle)",
                  borderRadius: 8,
                  padding: "12px 14px",
                  margin: "8px 0",
                  textAlign: "center",
                  fontSize: 12,
                  color: "var(--text-faint)",
                  background: "var(--bg-elevated)",
                }}
              >
                Belum ada corak dengan aturan potongan awal. Tambahkan kode corak di bawah.
              </div>
            ) : (
              <div className="chip-row-wrap" style={{ marginTop: 8, marginBottom: 4 }}>
                {corakPotonganAwal.map((code) => (
                  <div
                    key={code}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      background: "var(--bg-elevated-2)",
                      border: "1px solid var(--border-subtle)",
                      borderRadius: 6,
                      padding: "3px 6px 3px 10px",
                      gap: 6,
                      fontSize: 12,
                      fontWeight: 600,
                      color: "var(--text-primary)",
                    }}
                  >
                    <span>{code}</span>
                    <button
                      type="button"
                      aria-label={`Hapus corak ${code} dari daftar potongan awal`}
                      title={`Hapus corak ${code} dari daftar potongan awal`}
                      onClick={() => {
                        removeCorakPotonganAwal(code);
                        showToast(`Corak "${code}" dihapus dari daftar potongan awal`);
                      }}
                      style={{
                        background: "transparent",
                        color: "var(--text-faint)",
                        border: "none",
                        borderRadius: "50%",
                        width: 18,
                        height: 18,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        cursor: "pointer",
                        padding: 0,
                        fontSize: 12,
                        lineHeight: 1,
                        transition: "color 0.15s, background 0.15s",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.color = "#ef4444";
                        e.currentTarget.style.background = "rgba(239, 68, 68, 0.15)";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.color = "var(--text-faint)";
                        e.currentTarget.style.background = "transparent";
                      }}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div style={{ display: "flex", gap: 6, marginTop: 10 }}>
              <input
                className="field-input"
                style={{ flex: 1, padding: "8px 12px", fontSize: 13 }}
                placeholder="Tambah kode corak (cth: 80125)"
                value={newPotonganAwal}
                onChange={(e) => setNewPotonganAwal(e.target.value.toUpperCase())}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    handleAddCorakPotonganAwal();
                  }
                }}
              />
              <button
                className="btn primary"
                style={{ padding: "8px 16px", fontSize: 13, display: "inline-flex", alignItems: "center", gap: 4 }}
                disabled={!newPotonganAwal.trim()}
                onClick={handleAddCorakPotonganAwal}
              >
                <AddIcon size={14} />
                <span>Tambah</span>
              </button>
            </div>
          </div>

          {/* Card: Cadangan Data */}
          <div className="settings-section-card">
            <div className="settings-section-header">
              <DatabaseIcon size={16} />
              <span>Cadangan &amp; Pemulihan</span>
            </div>
            <div className="settings-section-desc">
              Simpan seluruh data database mesin, estimasi aktif, dan riwayat shift ke file JSON cadangan.
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <button className="settings-action-btn primary" onClick={handleExport}>
                <DownloadIcon size={16} />
                <span>Cadangkan</span>
              </button>
              <button className="settings-action-btn" onClick={() => fileInputRef.current?.click()}>
                <UploadIcon size={16} />
                <span>Pulihkan</span>
              </button>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="application/json,.json"
              style={{ display: "none" }}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) handleImportFile(file);
                e.target.value = "";
              }}
            />
          </div>

          {/* Card: Zona Reset */}
          <div className="settings-section-card">
            <div className="settings-section-header danger">
              <WarningIcon size={16} />
              <span>Reset Data</span>
            </div>
            <div className="settings-section-desc">
              Mengembalikan pengaturan database mesin ke bawaan pabrik dan menghapus seluruh riwayat shift.
            </div>
            <button className="settings-action-btn danger" onClick={handleResetAllDb}>
              <ResetIcon size={16} />
              <span>Reset Semua ke Default</span>
            </button>
          </div>

          {/* Card: Bantuan & Info */}
          <div className="settings-section-card">
            <div className="settings-section-header">
              <InfoIcon size={16} />
              <span>Bantuan &amp; Informasi</span>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <button className="settings-action-btn" onClick={onOpenHelp}>
                <BookOpenIcon size={16} />
                <span>Panduan</span>
              </button>
              <button className="settings-action-btn" onClick={() => setAboutOpen(true)}>
                <InfoIcon size={16} />
                <span>Tentang</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {aboutOpen && <AboutDialog onClose={() => setAboutOpen(false)} />}

      {/* Dialog yang sama persis dengan yang muncul saat pertama kali aplikasi dibuka — satu
          form, satu tempat perbaikannya kalau bidangnya bertambah. */}
      {operatorEditing && (
        <OperatorDialog
          nama={state.operatorNama ?? ""}
          grup={state.operatorGrup ?? ""}
          onClose={() => setOperatorEditing(false)}
          onSave={(nama, grup) => {
            setOperator(nama, grup);
            setOperatorEditing(false);
            showToast("Identitas operator disimpan ✓");
          }}
        />
      )}
    </div>
  );
}
