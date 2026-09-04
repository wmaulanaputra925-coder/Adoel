import { useMemo, useRef, useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { formatDeltaMin, formatYard, getRepresentativeEpochMin, shiftNumberForEpochMin } from "../domain/format";
import { sortAktualChronological } from "../domain/aktualOrder";
import { shareOrCopy, shareShiftText } from "../domain/share";
import { TIPE_COLOR } from "../domain/mesinVisual";
import type { AktualEntry, MesinData, MesinTipe, ShiftRecord } from "../domain/types";
import { AddIcon, CircleIcon, CloseIcon, DeleteIcon, EditIcon, MesinTipeIcon, ShareIcon } from "./Icons";
import { WaveProgressBar } from "./WaveProgressBar";
import { EditAktualDialog } from "./EditAktualDialog";
import { TambahAktualDialog } from "./TambahAktualDialog";

function formatShiftDate(epochMin: number): string {
  const d = new Date(epochMin * 60000);
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`;
}

function formatShiftShortDate(epochMin: number): string {
  const d = new Date(epochMin * 60000);
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}`;
}

function formatShiftTime(epochMin: number): string {
  const d = new Date(epochMin * 60000);
  return `${pad2(d.getHours())}.${pad2(d.getMinutes())}`;
}

function pad2(n: number): string {
  return n.toString().padStart(2, "0");
}

function formatCleanKeterangan(ket: string, jam: string): string {
  if (ket.startsWith(jam)) {
    const rest = ket.slice(jam.length).trim();
    if (rest.startsWith("(") && rest.endsWith(")")) {
      return rest.slice(1, -1);
    }
    return rest;
  }
  return ket;
}

/** Port 1:1 dari StatistikScreen.kt (aplikasi Android Adoel) — panel layar penuh yang
 * menampilkan arsip shift yang sudah diselesaikan, lengkap dengan kartu agregat
 * produktivitas, diagram batang 10 shift terakhir bertekstur benang, dan rincian tiap shift. */
export function StatistikScreen({ onClose }: { onClose: () => void }) {
  const { state, hapusShift, updateHistoryEntry, deleteHistoryEntry, addHistoryEntry } = useDoffStore();
  const { showConfirm, showToast } = useUiStore();
  const [expandedShiftId, setExpandedShiftId] = useState<number | null>(null);
  const [editingEntry, setEditingEntry] = useState<{ shiftId: number; entry: AktualEntry } | null>(null);
  const [addingToShiftId, setAddingToShiftId] = useState<number | null>(null);

  const shiftCardRefs = useRef<Record<number, HTMLDivElement | null>>({});

  const totalDoff = useMemo(() => state.history.reduce((sum, h) => sum + h.aktual.length, 0), [state.history]);
  const avgPerShift = state.history.length > 0 ? totalDoff / state.history.length : 0;
  const maxDoffCount = useMemo(() => Math.max(1, ...state.history.map((h) => h.aktual.length)), [state.history]);

  function jumpToShift(shift: ShiftRecord) {
    setExpandedShiftId(shift.id);
    const el = shiftCardRefs.current[shift.id];
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
  }

  function handleDeleteShift(shift: ShiftRecord) {
    const representativeTime = getRepresentativeEpochMin(shift);
    const shiftNo = shiftNumberForEpochMin(representativeTime);
    const dateStr = formatShiftDate(shift.startedAtEpochMin);
    showConfirm(`Hapus arsip Shift ${shiftNo} · ${dateStr}? Data ini tidak bisa dikembalikan.`, () => {
      hapusShift(shift.id);
      showToast("Arsip shift dihapus");
    });
  }

  async function handleShareShift(shift: ShiftRecord) {
    if (shift.aktual.length === 0) return;
    const outcome = await shareOrCopy(shareShiftText(shift, state.db), "Riwayat Shift");
    if (outcome === "copied") showToast("Teks disalin ke clipboard ✓");
  }

  return (
    <div className="overlay">
      <div className="overlay-header">
        <h2>Statistik</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Tutup">
          <CloseIcon />
        </button>
      </div>

      <div className="overlay-body">
        {state.history.length === 0 ? (
          <div className="empty-state-card" style={{ marginTop: 24 }}>
            <div className="empty-state-title">Belum ada riwayat shift</div>
            <div className="empty-state-subtitle">
              Riwayat akan tersimpan otomatis setiap kali kamu tekan Selesai Shift
            </div>
          </div>
        ) : (
          <>
            <AggregateStatsCard
              history={state.history}
              db={state.db}
              totalDoff={totalDoff}
              avgPerShift={avgPerShift}
              selectedShiftId={expandedShiftId}
              onBarClick={jumpToShift}
            />

            {state.history.map((shift) => (
              <div
                key={shift.id}
                ref={(el) => {
                  shiftCardRefs.current[shift.id] = el;
                }}
              >
                <ShiftRow
                  shift={shift}
                  db={state.db}
                  maxDoffCount={maxDoffCount}
                  expanded={expandedShiftId === shift.id}
                  onToggle={() => setExpandedShiftId((prev) => (prev === shift.id ? null : shift.id))}
                  onDeleteShift={() => handleDeleteShift(shift)}
                  onShareShift={() => handleShareShift(shift)}
                  onEditEntry={(entry) => setEditingEntry({ shiftId: shift.id, entry })}
                  onAddEntry={() => setAddingToShiftId(shift.id)}
                />
              </div>
            ))}
          </>
        )}
      </div>

      {editingEntry && (
        <EditAktualDialog
          entry={editingEntry.entry}
          onClose={() => setEditingEntry(null)}
          onDelete={(id) => {
            deleteHistoryEntry(editingEntry.shiftId, id);
            showToast("Entri riwayat dihapus");
            setEditingEntry(null);
          }}
          onSaveCustom={(jam, ket, corakOverride, customYard) => {
            updateHistoryEntry(editingEntry.shiftId, editingEntry.entry.id, jam, ket, corakOverride, customYard);
            showToast("Riwayat shift diperbarui ✓");
            setEditingEntry(null);
          }}
        />
      )}

      {addingToShiftId != null && (
        <TambahAktualDialog
          onClose={() => setAddingToShiftId(null)}
          onSave={(mcNo, jam, ket, corakOverride, customYard) => {
            addHistoryEntry(addingToShiftId, mcNo, jam, ket, corakOverride, customYard);
            showToast("Potongan ditambahkan ke riwayat shift ✓");
            setAddingToShiftId(null);
          }}
        />
      )}
    </div>
  );
}

/** Kartu ringkasan agregat — mencakup 3 petak angka metrik dan grafik balok 10 shift terakhir. */
function AggregateStatsCard({
  history,
  db,
  totalDoff,
  avgPerShift,
  selectedShiftId,
  onBarClick,
}: {
  history: ShiftRecord[];
  db: Record<string, MesinData>;
  totalDoff: number;
  avgPerShift: number;
  selectedShiftId: number | null;
  onBarClick: (shift: ShiftRecord) => void;
}) {
  return (
    <div className="stat-summary-card">
      <div className="stat-grid-3">
        <div className="stat-tile">
          <div className="val highlight">{totalDoff}</div>
          <div className="lbl">Total Doff</div>
        </div>
        <div className="stat-tile">
          <div className="val">{history.length}</div>
          <div className="lbl">Total Shift</div>
        </div>
        <div className="stat-tile">
          <div className="val">{avgPerShift.toFixed(1)}</div>
          <div className="lbl">Rata-rata/Shift</div>
        </div>
      </div>

      <DoffCountChart history={history} selectedShiftId={selectedShiftId} onBarClick={onBarClick} />

      <TipeBreakdownBar history={history} db={db} />
    </div>
  );
}

/** Diagram batang jumlah doff 10 shift terbaru dengan tampilan balok web yang bersih. */
function DoffCountChart({
  history,
  selectedShiftId,
  onBarClick,
}: {
  history: ShiftRecord[];
  selectedShiftId: number | null;
  onBarClick: (shift: ShiftRecord) => void;
}) {
  const recent = useMemo(() => history.slice(0, 10).reverse(), [history]);
  if (recent.length === 0) return null;
  const maxCount = Math.max(1, ...recent.map((s) => s.aktual.length));

  return (
    <div className="stat-chart-container">
      <div className="stat-chart-header">
        <span>Tren 10 Shift Terakhir</span>
        <span style={{ fontSize: 11, fontWeight: 500, color: "var(--text-faint)" }}>
          Klik balok untuk lihat shift
        </span>
      </div>

      <div className="stat-chart-bars">
        {recent.map((shift) => {
          const selected = shift.id === selectedShiftId;
          const pct = Math.max(8, Math.round((shift.aktual.length / maxCount) * 100));
          return (
            <div
              key={shift.id}
              onClick={() => onBarClick(shift)}
              title={`Shift · ${shift.aktual.length} doff (${formatShiftDate(shift.startedAtEpochMin)})`}
              className={`stat-chart-col${selected ? " selected" : ""}`}
            >
              <span className="stat-chart-val">{shift.aktual.length}</span>
              <div className="stat-chart-bar" style={{ height: `${pct}%` }} />
            </div>
          );
        })}
      </div>

      <div className="stat-chart-labels">
        {recent.map((shift) => {
          const selected = shift.id === selectedShiftId;
          return (
            <div
              key={shift.id}
              onClick={() => onBarClick(shift)}
              className={`stat-chart-lbl${selected ? " selected" : ""}`}
            >
              {formatShiftShortDate(shift.startedAtEpochMin)}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/** Bar distribusi proporsi tipe mesin (TAPPET, CAM, D405, D408). */
function TipeBreakdownBar({
  history,
  db,
}: {
  history: ShiftRecord[];
  db: Record<string, MesinData>;
}) {
  const counts = useMemo(() => {
    const order: MesinTipe[] = ["TAPPET", "CAM", "D405", "D408"];
    const byTipe: Partial<Record<MesinTipe, number>> = {};
    for (const h of history) {
      for (const a of h.aktual) {
        const tipe = db[a.mcNo]?.tipe;
        if (tipe) byTipe[tipe] = (byTipe[tipe] ?? 0) + 1;
      }
    }
    return order.filter((t) => (byTipe[t] ?? 0) > 0).map((t) => ({ tipe: t, count: byTipe[t]! }));
  }, [history, db]);

  const total = useMemo(() => counts.reduce((acc, c) => acc + c.count, 0), [counts]);
  if (total === 0) return null;

  return (
    <div className="stat-tipe-breakdown">
      <div className="stat-tipe-bar">
        {counts.map(({ tipe, count }) => (
          <div
            key={tipe}
            style={{
              flexGrow: count,
              minWidth: 2,
              background: TIPE_COLOR[tipe],
              height: "100%",
            }}
          />
        ))}
      </div>
      <div className="stat-tipe-legend">
        {counts.map(({ tipe, count }) => (
          <div key={tipe} className="stat-tipe-item">
            <span
              className="stat-tipe-dot"
              style={{ background: TIPE_COLOR[tipe] }}
            />
            <span>
              {tipe} <strong style={{ color: "var(--text-primary)" }}>{count}</strong>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Baris kartu arsip shift — port 1:1 dari ShiftRow di Android. */
function ShiftRow({
  shift,
  db,
  maxDoffCount,
  expanded,
  onToggle,
  onDeleteShift,
  onShareShift,
  onEditEntry,
  onAddEntry,
}: {
  shift: ShiftRecord;
  db: Record<string, MesinData>;
  maxDoffCount: number;
  expanded: boolean;
  onToggle: () => void;
  onDeleteShift: () => void;
  onShareShift: () => void;
  onEditEntry: (entry: AktualEntry) => void;
  onAddEntry: () => void;
}) {
  const representativeTime = useMemo(() => getRepresentativeEpochMin(shift), [shift]);
  const shiftNo = useMemo(() => shiftNumberForEpochMin(representativeTime), [representativeTime]);
  const dateStr = useMemo(() => formatShiftDate(shift.startedAtEpochMin), [shift.startedAtEpochMin]);
  const timeRange = useMemo(
    () => `${formatShiftTime(shift.startedAtEpochMin)}–${formatShiftTime(shift.endedAtEpochMin)}`,
    [shift.startedAtEpochMin, shift.endedAtEpochMin],
  );

  const chronological = useMemo(
    () => sortAktualChronological(shift.aktual, shift.startedAtEpochMin + 240),
    [shift.aktual, shift.startedAtEpochMin],
  );

  const avgGapMin = useMemo(() => {
    const stamped = chronological.map((a) => a.tsEpochMin).filter((t): t is number => t !== null);
    if (stamped.length < 2) return null;
    let total = 0;
    for (let i = 1; i < stamped.length; i++) total += stamped[i] - stamped[i - 1];
    return total / (stamped.length - 1);
  }, [chronological]);

  return (
    <div className={`shift-card${expanded ? " expanded" : ""}`} style={{ marginBottom: 12 }}>
      <div className="top" onClick={onToggle} role="button">
        <div>
          <div className="title">
            Shift {shiftNo} · {dateStr}
          </div>
          <div className="sub">{timeRange}</div>
          <div style={{ width: 60, marginTop: 6 }}>
            <WaveProgressBar
              fraction={shift.aktual.length / maxDoffCount}
              trackColor="var(--bg-elevated-2)"
              fillColor="var(--cyan-500)"
              height={4}
            />
          </div>
        </div>
        <div className="stats">
          <div className="num">{shift.aktual.length} doff</div>
          {avgGapMin != null && <div className="gap">±{formatDeltaMin(Math.round(avgGapMin))}/doff</div>}
        </div>
      </div>

      <div className="btn-row" style={{ marginTop: 10 }}>
        <button className="btn" onClick={onShareShift} disabled={shift.aktual.length === 0}>
          <ShareIcon size={14} /> Bagikan
        </button>
        <button className="btn danger" onClick={onDeleteShift}>
          <DeleteIcon size={14} /> Hapus
        </button>
      </div>

      {expanded && (
        <div className="shift-detail" style={{ marginTop: 12 }}>
          {chronological.length === 0 ? (
            <div style={{ fontSize: 12, color: "var(--text-faint)", textAlign: "center", padding: "12px 0" }}>
              Tidak ada potongan dalam shift ini
            </div>
          ) : (
            <>
              <div className="shift-detail-header">
                <span className="shift-detail-title">
                  RINCIAN POTONGAN <span className="shift-detail-count">{chronological.length} DOFF</span>
                </span>
                <span className="shift-detail-hint">Ketuk baris untuk edit</span>
              </div>

              {chronological.map((entry, index) => {
                const mesin = db[entry.mcNo];
                const tipe = mesin?.tipe;
                const corak = entry.corakOverride ?? mesin?.corak ?? "—";
                const yard = entry.customYard ?? mesin?.targetYard;
                const ketCode = formatCleanKeterangan(entry.ket, entry.jam);

                return (
                  <div
                    className="shift-detail-row"
                    key={entry.id}
                    onClick={() => onEditEntry(entry)}
                    role="button"
                    title={`Edit riwayat Mc ${entry.mcNo}`}
                  >
                    <div className="row-left">
                      <span className="row-num">{index + 1}</span>
                      {tipe ? (
                        <span className="row-tipe-icon" style={{ color: TIPE_COLOR[tipe] }}>
                          <MesinTipeIcon tipe={tipe} size={13} />
                        </span>
                      ) : (
                        <span className="row-tipe-icon" style={{ color: "var(--text-faint)" }}>
                          <CircleIcon size={13} />
                        </span>
                      )}
                      <span className="row-mcno">Mc {entry.mcNo}</span>
                      <span className="row-corak">{corak}</span>
                      {yard != null && <span className="row-yard-badge">{formatYard(yard)}y</span>}
                      {ketCode.length > 0 && <span className="row-ket-badge">{ketCode}</span>}
                    </div>
                    <div className="row-time-badge">
                      <span>{entry.jam}</span>
                      <EditIcon size={11} />
                    </div>
                  </div>
                );
              })}
            </>
          )}

          <button className="add-entry-btn" onClick={onAddEntry}>
            <AddIcon size={16} />
            <span>Tambah Potongan</span>
          </button>
        </div>
      )}
    </div>
  );
}
