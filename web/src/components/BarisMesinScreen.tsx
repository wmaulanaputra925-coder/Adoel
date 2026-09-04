import { useMemo, useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { TIPE_COLOR } from "../domain/mesinVisual";
import { formatYard, nowTimeStr } from "../domain/format";
import { parseJam } from "../domain/parse";
import { selisihKoreksiD408 } from "../domain/estimasiUtils";
import { defaultMesinData, type MesinData, type MesinTipe } from "../domain/types";
import { CorakShortcutPicker } from "./CorakShortcutPicker";
import {
  AddIcon,
  CheckIcon,
  ChevronRightIcon,
  CloseIcon,
  DeleteIcon,
  EditIcon,
  MesinTipeIcon,
  SearchIcon,
  TextureIcon,
} from "./Icons";

const TIPE_LIST: MesinTipe[] = ["TAPPET", "CAM", "D405", "D408"];

function parseMesinNum(raw: string): number | null {
  const t = raw.trim().replace(",", ".");
  if (t === "") return null;
  const n = parseFloat(t);
  return Number.isNaN(n) ? null : n;
}

type StatusFilter = "ALL" | "ACTIVE" | "STOPPED";

export function BarisMesinScreen({ onClose }: { onClose: () => void }) {
  const { state, setMesin, resetMesin } = useDoffStore();
  const { showToast, showConfirm } = useUiStore();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [selectedCorak, setSelectedCorak] = useState<string | null>(null);
  const [activeMcNo, setActiveMcNo] = useState<string | null>(null);
  const [form, setForm] = useState<MesinData | null>(null);

  // Semua mesin yang sudah dikonfigurasi corak
  const configuredEntries = useMemo(() => {
    return Object.entries(state.db).filter(([, v]) => v.corak !== "" && v.corak !== "-");
  }, [state.db]);

  // Mesin yang aktif berproduksi (isActive !== false)
  const activeProduksiEntries = useMemo(() => {
    return configuredEntries.filter(([, v]) => v.isActive !== false);
  }, [configuredEntries]);

  // Mesin yang sedang stop produksi sementara (isActive === false)
  const stoppedProduksiEntries = useMemo(() => {
    return configuredEntries.filter(([, v]) => v.isActive === false);
  }, [configuredEntries]);

  // Ringkasan lengkap corak yang sedang AKTIF berproduksi di tiap mesin
  const activeCorakSummary = useMemo(() => {
    const map = new Map<string, { corak: string; machines: string[]; tipes: Set<MesinTipe> }>();
    for (const [mcNo, v] of activeProduksiEntries) {
      const c = v.corak.trim().toUpperCase();
      if (!map.has(c)) {
        map.set(c, { corak: c, machines: [], tipes: new Set() });
      }
      const item = map.get(c)!;
      item.machines.push(mcNo);
      item.tipes.add(v.tipe);
    }
    return Array.from(map.values())
      .map((item) => ({
        ...item,
        machines: item.machines.sort((a, b) => (parseInt(a, 10) || 0) - (parseInt(b, 10) || 0)),
      }))
      .sort((a, b) => b.machines.length - a.machines.length || a.corak.localeCompare(b.corak));
  }, [activeProduksiEntries]);

  // Daftar mesin yang difilter dan dikelompokkan per tipe
  const groupedEntries = useMemo(() => {
    const searchTrim = search.trim().toUpperCase();
    const filtered = configuredEntries.filter(([k, v]) => {
      // Filter status (Aktif / Stop Sementara)
      if (statusFilter === "ACTIVE" && v.isActive === false) return false;
      if (statusFilter === "STOPPED" && v.isActive !== false) return false;

      // Filter klik corak spesifik
      if (selectedCorak && v.corak.trim().toUpperCase() !== selectedCorak) return false;

      // Filter pencarian (nomor mesin atau nama corak)
      if (searchTrim) {
        const mcMatch = k.includes(searchTrim);
        const corakMatch = v.corak.toUpperCase().includes(searchTrim);
        if (!mcMatch && !corakMatch) return false;
      }

      return true;
    });

    return TIPE_LIST.map((tipe) => ({
      tipe,
      rows: filtered
        .filter(([, v]) => v.tipe === tipe)
        .sort((a, b) => (parseInt(a[0], 10) || 0) - (parseInt(b[0], 10) || 0)),
    })).filter((g) => g.rows.length > 0);
  }, [configuredEntries, statusFilter, selectedCorak, search]);

  const searchedTarget = useMemo(() => {
    const n = search.trim();
    if (!/^\d{1,4}$/.test(n)) return null;
    const existing = state.db[n];
    if (!existing) {
      return { mcNo: n, isNew: true, mesin: defaultMesinData() };
    }
    if (existing.corak === "" || existing.corak === "-") {
      return { mcNo: n, isNew: false, mesin: existing };
    }
    return null;
  }, [search, state.db]);

  function loadFrom(mcNo: string, mesin: MesinData) {
    setActiveMcNo(mcNo);
    setForm({ ...mesin, isActive: mesin.isActive !== false });
  }

  function jumpToSearch() {
    const n = search.trim();
    if (!/^\d{1,4}$/.test(n)) return;
    const mesin = state.db[n] ?? defaultMesinData();
    loadFrom(n, mesin);
  }

  function handleToggleStatus(mcNo: string, e: React.MouseEvent) {
    e.stopPropagation();
    const current = state.db[mcNo];
    if (!current) return;
    const nextActive = current.isActive === false;
    setMesin(mcNo, { ...current, isActive: nextActive });
    if (nextActive) {
      showToast(`Mc ${mcNo} diaktifkan (ON) ✓`);
    } else {
      showToast(`Mc ${mcNo} stop produksi sementara (OFF) ⏸`);
    }
  }

  function handleSave(savedForm: MesinData) {
    if (!activeMcNo) return;
    const corak = savedForm.corak.trim() || "-";
    setMesin(activeMcNo, { ...savedForm, corak, isActive: savedForm.isActive !== false });
    showToast(`Mc ${activeMcNo} (${savedForm.tipe}) disimpan ✓`);
    setActiveMcNo(null);
    setForm(null);
    setSearch("");
  }

  function handleReset() {
    if (!activeMcNo) return;
    showConfirm(`Reset Mc ${activeMcNo} ke default? Corak, target yard, dan pengaturan lain akan dihapus.`, () => {
      resetMesin(activeMcNo);
      showToast(`Mc ${activeMcNo} direset ke default`);
      setActiveMcNo(null);
      setForm(null);
    });
  }

  return (
    <div className="overlay">
      <div className="overlay-header">
        <h2>Daftar Mesin</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Tutup">
          <CloseIcon />
        </button>
      </div>

      <div className="overlay-body" style={{ paddingBottom: 92 }}>
        {/* Ringkasan Lengkap Corak yang Sedang Aktif Produksi */}
        <div className="corak-summary-card">
          <div className="corak-summary-header">
            <div className="corak-summary-title">
              <TextureIcon size={16} />
              <span>Corak Sedang Produksi</span>
            </div>
            <div className="corak-summary-badges">
              <span className="corak-summary-badge active">
                <span className="status-dot active" />
                {activeProduksiEntries.length} Mesin Aktif ({activeCorakSummary.length} Corak)
              </span>
              {stoppedProduksiEntries.length > 0 && (
                <span className="corak-summary-badge stopped">
                  <span className="status-dot stopped" />
                  {stoppedProduksiEntries.length} Stop Sementara
                </span>
              )}
            </div>
          </div>

          {activeCorakSummary.length === 0 ? (
            <div style={{ fontSize: 12, color: "var(--text-faint)", padding: "8px 0" }}>
              Tidak ada mesin yang aktif berproduksi saat ini.
            </div>
          ) : (
            <div className="corak-summary-grid">
              {activeCorakSummary.map((item) => {
                const isSelected = selectedCorak === item.corak;
                return (
                  <div
                    key={item.corak}
                    className={`corak-summary-item${isSelected ? " selected" : ""}`}
                    onClick={() => setSelectedCorak(isSelected ? null : item.corak)}
                    title={`Klik untuk memfilter daftar mesin corak ${item.corak}`}
                  >
                    <div className="corak-summary-item-top">
                      <span className="corak-summary-name">{item.corak}</span>
                      <span className="corak-summary-count">{item.machines.length} mc</span>
                    </div>
                    <div className="corak-mc-pills-row">
                      {item.machines.map((m) => (
                        <span
                          key={m}
                          className="corak-mc-pill"
                          onClick={(e) => {
                            e.stopPropagation();
                            loadFrom(m, state.db[m] ?? defaultMesinData());
                          }}
                          title={`Akses cepat: edit Mc ${m}`}
                        >
                          {m}
                        </span>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Filter Status Produksi & Tombol Reset Corak Filter */}
        <div className="machine-status-tabs">
          <button
            type="button"
            className={`machine-status-tab${statusFilter === "ALL" && !selectedCorak ? " active" : ""}`}
            onClick={() => {
              setStatusFilter("ALL");
              setSelectedCorak(null);
            }}
          >
            Semua ({configuredEntries.length})
          </button>
          <button
            type="button"
            className={`machine-status-tab${statusFilter === "ACTIVE" && !selectedCorak ? " active" : ""}`}
            onClick={() => {
              setStatusFilter("ACTIVE");
              setSelectedCorak(null);
            }}
          >
            <span className="status-dot active" />
            Aktif Produksi ({activeProduksiEntries.length})
          </button>
          {stoppedProduksiEntries.length > 0 && (
            <button
              type="button"
              className={`machine-status-tab${statusFilter === "STOPPED" && !selectedCorak ? " active" : ""}`}
              onClick={() => {
                setStatusFilter("STOPPED");
                setSelectedCorak(null);
              }}
            >
              <span className="status-dot stopped" />
              Stop Sementara ({stoppedProduksiEntries.length})
            </button>
          )}

          {selectedCorak && (
            <button
              type="button"
              className="machine-status-tab active"
              style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
              onClick={() => setSelectedCorak(null)}
              title="Hapus filter corak"
            >
              <TextureIcon size={12} />
              <span>Corak: {selectedCorak}</span>
              <CloseIcon size={14} />
            </button>
          )}
        </div>

        {searchedTarget && (
          <button
            className="settings-action-btn primary"
            style={{ width: "100%", marginBottom: 14 }}
            onClick={() => loadFrom(searchedTarget.mcNo, searchedTarget.mesin)}
          >
            <AddIcon size={16} />
            <span>
              {searchedTarget.isNew
                ? `+ Tambah Mesin Baru Mc ${searchedTarget.mcNo}`
                : `Konfigurasi Mc ${searchedTarget.mcNo} (belum diatur)`}
            </span>
          </button>
        )}

        {groupedEntries.length === 0 && (
          <div className="empty-state-card" style={{ margin: "24px 0" }}>
            <div className="empty-state-title">
              {search.trim() || selectedCorak || statusFilter !== "ALL"
                ? "Mesin Tidak Ditemukan"
                : "Belum Ada Mesin Terkonfigurasi"}
            </div>
            <div className="empty-state-subtitle">
              {search.trim() || selectedCorak || statusFilter !== "ALL"
                ? "Coba sesuaikan kata kunci pencarian atau bersihkan filter status"
                : "Masukkan nomor mesin pada kolom di bawah untuk mulai mengatur corak & tipe"}
            </div>
          </div>
        )}

        {groupedEntries.map(({ tipe, rows }) => (
          <div key={tipe} style={{ marginBottom: 18 }}>
            <div className="mesin-group-head" style={{ color: TIPE_COLOR[tipe] }}>
              <MesinTipeIcon tipe={tipe} size={15} />
              <span>{tipe}</span>
              <span className="count">{rows.length} mesin</span>
            </div>
            {rows.map(([k, v]) => {
              const isRunning = v.isActive !== false;
              return (
                <div
                  className={`machine-list-item${!isRunning ? " is-stopped" : ""}`}
                  key={k}
                  onClick={() => loadFrom(k, v)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      loadFrom(k, v);
                    }
                  }}
                  role="button"
                  tabIndex={0}
                  aria-label={`Edit pengaturan Mc ${k}`}
                >
                  <span className="mc-badge">{k}</span>
                  <div className="corak-info">
                    <TextureIcon size={13} />
                    <span className="corak-text">{v.corak || "-"}</span>
                    {!isRunning && (
                      <span
                        style={{
                          fontSize: 10,
                          fontWeight: 800,
                          color: "var(--amber-400)",
                          background: "rgba(245, 158, 11, 0.15)",
                          padding: "1px 5px",
                          borderRadius: 4,
                          marginLeft: 4,
                        }}
                      >
                        STOP
                      </span>
                    )}
                  </div>
                  <div className="meta-tags">
                    {v.targetYard != null && <span className="meta-tag">{v.targetYard}y</span>}
                    {v.speed != null && v.tipe === "D405" && <span className="meta-tag">{v.speed}y/m</span>}
                    {v.koreksi != null && v.tipe === "D408" && (
                      <span className="meta-tag">{v.koreksi > 0 ? `+${v.koreksi}` : v.koreksi}m</span>
                    )}
                  </div>

                  {/* Tombol ON / OFF Aksi Stop & Aktif Produksi Sementara */}
                  <button
                    type="button"
                    className={`machine-status-toggle-btn ${isRunning ? "on" : "off"}`}
                    onClick={(e) => handleToggleStatus(k, e)}
                    title={isRunning ? `Mc ${k} aktif. Klik untuk stop sementara (OFF)` : `Mc ${k} stop sementara. Klik untuk aktifkan (ON)`}
                    aria-label={isRunning ? `Matikan produksi Mc ${k}` : `Aktifkan produksi Mc ${k}`}
                  >
                    {isRunning ? (
                      <>
                        <span className="status-dot active" />
                        <span>ON</span>
                      </>
                    ) : (
                      <>
                        <span className="status-dot stopped" />
                        <span>OFF</span>
                      </>
                    )}
                  </button>

                  <span className="machine-item-chevron" aria-hidden="true">
                    <ChevronRightIcon size={16} />
                  </span>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <div className="mesin-console floating-card">
        <div className="console-row">
          <div style={{ position: "relative", flex: 1, display: "flex", alignItems: "center" }}>
            <span
              style={{
                position: "absolute",
                left: 12,
                color: "var(--text-faint)",
                display: "inline-flex",
                pointerEvents: "none",
              }}
            >
              <SearchIcon size={16} />
            </span>
            <input
              className="console-mcno-input"
              style={{ paddingLeft: 36 }}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") jumpToSearch();
              }}
              placeholder="Cari mesin / corak"
              autoComplete="off"
            />
          </div>
          <button
            className="console-icon-btn"
            style={{
              background: search.trim() !== "" ? "var(--cyan-600)" : "var(--bg-elevated-2)",
              color: search.trim() !== "" ? "#fff" : "var(--text-faint)",
            }}
            disabled={search.trim() === ""}
            aria-label={state.db[search.trim()] ? `Edit Mc ${search.trim()}` : `Tambah Mc ${search.trim()}`}
            title={state.db[search.trim()] ? `Edit Mc ${search.trim()}` : `Tambah Mc ${search.trim()}`}
            onClick={jumpToSearch}
          >
            {state.db[search.trim()] ? <EditIcon size={18} /> : <AddIcon size={18} />}
          </button>
        </div>
      </div>

      {activeMcNo && form && (
        <MesinEditDialog
          key={activeMcNo}
          mcNo={activeMcNo}
          form={form}
          onClose={() => {
            setActiveMcNo(null);
            setForm(null);
          }}
          onSave={handleSave}
          onReset={handleReset}
        />
      )}
    </div>
  );
}

function MesinEditDialog({
  mcNo,
  form: initialForm,
  onClose,
  onSave,
  onReset,
}: {
  mcNo: string;
  form: MesinData;
  onClose: () => void;
  onSave: (form: MesinData) => void;
  onReset: () => void;
}) {
  const { showToast } = useUiStore();
  const [form, setForm] = useState<MesinData>({
    ...initialForm,
    isActive: initialForm.isActive !== false,
  });
  const [targetYardText, setTargetYardText] = useState(
    initialForm.targetYard != null ? formatYard(initialForm.targetYard) : "",
  );
  const [speedText, setSpeedText] = useState(initialForm.speed != null ? formatYard(initialForm.speed) : "");
  const [koreksiText, setKoreksiText] = useState(initialForm.koreksi != null ? formatYard(initialForm.koreksi) : "");

  // Helper selisih D408
  const [waktuAktualText, setWaktuAktualText] = useState(() => nowTimeStr());
  const [counterText, setCounterText] = useState("");

  const handleApplyKoreksiHelper = () => {
    const wakMin = parseJam(waktuAktualText);
    const cntMin = parseJam(counterText);
    if (wakMin == null || cntMin == null) {
      showToast("⚠ Format jam aktual atau counter tidak valid");
      return;
    }
    const diff = selisihKoreksiD408(wakMin, cntMin);
    const formatted = String(diff);
    setKoreksiText(formatted);
    setForm((f) => ({ ...f, koreksi: diff }));
    showToast(`Koreksi diatur ke ${diff > 0 ? `+${diff}` : diff} menit ✓`);
  };

  const handleAdjustKoreksi = (delta: number) => {
    const current = parseMesinNum(koreksiText) ?? 0;
    const nextVal = current + delta;
    setKoreksiText(String(nextVal));
    setForm((f) => ({ ...f, koreksi: nextVal }));
  };

  const handleValidateAndSave = () => {
    if (targetYardText.trim() !== "" && parseMesinNum(targetYardText) === null) {
      showToast("⚠ Target Yard tidak valid, cek kembali");
      return;
    }
    if (form.tipe === "D405" && speedText.trim() !== "" && parseMesinNum(speedText) === null) {
      showToast("⚠ Speed tidak valid, cek kembali");
      return;
    }
    if (form.tipe === "D408" && koreksiText.trim() !== "" && parseMesinNum(koreksiText) === null) {
      showToast("⚠ Koreksi tidak valid, cek kembali");
      return;
    }

    const targetYard = parseMesinNum(targetYardText);
    const speed = form.tipe === "D405" ? parseMesinNum(speedText) : null;
    const koreksi = form.tipe === "D408" ? parseMesinNum(koreksiText) : null;

    onSave({
      ...form,
      targetYard,
      speed,
      koreksi,
      isActive: form.isActive !== false,
    });
  };

  const isRunning = form.isActive !== false;

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div
        className="dialog"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 420, maxHeight: "90vh", overflowY: "auto" }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ color: TIPE_COLOR[form.tipe] }}>
              <MesinTipeIcon tipe={form.tipe} size={18} />
            </span>
            <span style={{ fontWeight: 800, fontSize: 20 }}>Mc {mcNo}</span>
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Tutup">
            <CloseIcon />
          </button>
        </div>

        {/* Status Produksi ON / OFF */}
        <div className="field-label">Status Produksi</div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 16 }}>
          <button
            type="button"
            className={`settings-theme-btn${isRunning ? " active" : ""}`}
            style={{
              borderColor: isRunning ? "#10b981" : "var(--border)",
              color: isRunning ? "#10b981" : "var(--text-muted)",
              background: isRunning ? "rgba(16, 185, 129, 0.12)" : "var(--bg-elevated-2)",
            }}
            onClick={() => setForm({ ...form, isActive: true })}
          >
            <span className="status-dot active" />
            <span>Aktif Produksi (ON)</span>
          </button>
          <button
            type="button"
            className={`settings-theme-btn${!isRunning ? " active" : ""}`}
            style={{
              borderColor: !isRunning ? "#f59e0b" : "var(--border)",
              color: !isRunning ? "#f59e0b" : "var(--text-muted)",
              background: !isRunning ? "rgba(245, 158, 11, 0.12)" : "var(--bg-elevated-2)",
            }}
            onClick={() => setForm({ ...form, isActive: false })}
          >
            <span className="status-dot stopped" />
            <span>Stop Sementara (OFF)</span>
          </button>
        </div>

        <div className="field-label">Tipe Mesin</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
          {TIPE_LIST.map((t) => (
            <button
              key={t}
              type="button"
              className={`chip-btn${form.tipe === t ? " active" : ""}`}
              onClick={() => setForm({ ...form, tipe: t })}
              style={{ display: "flex", alignItems: "center", gap: 6 }}
            >
              <MesinTipeIcon tipe={t} size={13} />
              <span>{t}</span>
            </button>
          ))}
        </div>

        <div className="field-label">Corak</div>
        <input
          className="field-input"
          placeholder="contoh: 4500"
          value={form.corak}
          onChange={(e) => setForm({ ...form, corak: e.target.value.toUpperCase() })}
        />
        <CorakShortcutPicker value={form.corak} onSelect={(c) => setForm({ ...form, corak: c })} />

        <div style={{ height: 12 }} />
        <div className="field-label">Target Yard (opsional)</div>
        <input
          className="field-input"
          inputMode="decimal"
          placeholder="contoh: 300"
          value={targetYardText}
          onChange={(e) => setTargetYardText(e.target.value)}
        />

        {form.tipe === "D405" && (
          <div style={{ marginTop: 12 }}>
            <div className="field-label">Speed Mesin (yard/menit)</div>
            <input
              className="field-input"
              inputMode="decimal"
              placeholder="contoh: 0.158"
              value={speedText}
              onChange={(e) => setSpeedText(e.target.value)}
            />
            <div style={{ fontSize: 11, color: "var(--text-faint)", marginTop: 4 }}>
              Dipakai untuk menghitung estimasi doff dari sisa yard.
            </div>
          </div>
        )}

        {form.tipe === "D408" && (
          <div style={{ marginTop: 12 }}>
            <div className="field-label">Koreksi Counter (menit)</div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <button
                type="button"
                className="chip-btn"
                style={{ width: 44, height: 44, fontSize: 18, fontWeight: 700 }}
                onClick={() => handleAdjustKoreksi(-1)}
              >
                −
              </button>
              <input
                className="field-input"
                inputMode="decimal"
                placeholder="contoh: 0 atau -15"
                value={koreksiText}
                onChange={(e) => setKoreksiText(e.target.value)}
                style={{ flex: 1, textAlign: "center" }}
              />
              <button
                type="button"
                className="chip-btn"
                style={{ width: 44, height: 44, fontSize: 18, fontWeight: 700 }}
                onClick={() => handleAdjustKoreksi(1)}
              >
                +
              </button>
            </div>

            <div style={{ background: "var(--bg-elevated-2)", padding: 12, borderRadius: 10, marginTop: 12 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "var(--cyan-400)", marginBottom: 8 }}>
                Hitung Koreksi dari Jam
              </div>
              <div className="field-grid" style={{ marginBottom: 8 }}>
                <div>
                  <div style={{ fontSize: 10, color: "var(--text-faint)", marginBottom: 2 }}>Waktu Nyata</div>
                  <input
                    className="field-input"
                    value={waktuAktualText}
                    onChange={(e) => setWaktuAktualText(e.target.value)}
                    placeholder="14.00"
                    style={{ fontSize: 12, padding: "6px 8px" }}
                  />
                </div>
                <div>
                  <div style={{ fontSize: 10, color: "var(--text-faint)", marginBottom: 2 }}>Jam di Counter</div>
                  <input
                    className="field-input"
                    value={counterText}
                    onChange={(e) => setCounterText(e.target.value)}
                    placeholder="13.45"
                    style={{ fontSize: 12, padding: "6px 8px" }}
                  />
                </div>
              </div>
              <button
                type="button"
                className="confirm"
                style={{ width: "100%", padding: "6px 12px", fontSize: 12, background: "var(--cyan-600)" }}
                onClick={handleApplyKoreksiHelper}
              >
                Hitung & Terapkan Koreksi
              </button>
            </div>
          </div>
        )}

        <div className="actions" style={{ marginTop: 20, justifyContent: "space-between" }}>
          <button
            type="button"
            className="cancel"
            onClick={onReset}
            style={{ color: "var(--red-400)", display: "flex", alignItems: "center", gap: 6 }}
          >
            <DeleteIcon size={14} />
            <span>Reset</span>
          </button>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" className="cancel" onClick={onClose}>
              Batal
            </button>
            <button
              type="button"
              className="confirm"
              style={{ background: "var(--cyan-600)", display: "flex", alignItems: "center", gap: 6 }}
              onClick={handleValidateAndSave}
            >
              <CheckIcon size={14} />
              <span>Simpan</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

