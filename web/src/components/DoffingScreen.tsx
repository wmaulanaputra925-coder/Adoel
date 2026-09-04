import { useMemo, useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { TIPE_COLOR } from "../domain/mesinVisual";
import { useConsoleHandlers } from "../hooks/useConsoleHandlers";
import { formatYard, nowAbsMin } from "../domain/format";
import { sortAktualChronological } from "../domain/aktualOrder";
import type { AktualEntry } from "../domain/types";
import { EditAktualDialog } from "./EditAktualDialog";
import {
  CircleIcon,
  DeleteIcon,
  EditIcon,
  HistoryEmptyIllustration,
  HistoryIcon,
  MesinTipeIcon,
  ScissorsIcon,
  SearchIcon,
  SpoolIcon,
  TextureIcon,
} from "./Icons";

export function DoffingScreen() {
  const { state } = useDoffStore();
  const { handleHapusAktual } = useConsoleHandlers();
  const [filter, setFilter] = useState("");
  const [editing, setEditing] = useState<AktualEntry | null>(null);

  const chronological = useMemo(() => sortAktualChronological(state.aktual, nowAbsMin()), [state.aktual]);
  // Reverse order (newest first), matching Android `aktualReversed`
  const reversed = useMemo(() => [...chronological].reverse(), [chronological]);
  const indexed = useMemo(() => reversed.map((entry, idx) => ({ entry, num: idx + 1 })), [reversed]);

  // Total yards for current shift
  const totalYards = useMemo(() => {
    return state.aktual.reduce((acc, entry) => {
      const custom = entry.customYard;
      if (custom != null && !Number.isNaN(custom)) return acc + custom;
      const mYard = state.db[entry.mcNo]?.targetYard;
      if (mYard != null && !Number.isNaN(mYard)) return acc + mYard;
      return acc;
    }, 0);
  }, [state.aktual, state.db]);

  // Pencarian hanya nomor mesin (Master Blueprint v9.2 §4)
  const filtered = useMemo(() => {
    if (!filter.trim()) return indexed;
    const f = filter.trim().toLowerCase();
    return indexed.filter(({ entry }) => entry.mcNo.toLowerCase().includes(f));
  }, [indexed, filter]);

  function handleHapus(id: number) {
    handleHapusAktual(id, () => setEditing(null));
  }

  return (
    <div className="scroll-area">
      {state.aktual.length === 0 ? (
        <div className="empty-state-card">
          <HistoryEmptyIllustration />
          <div className="empty-state-title">
            <HistoryIcon size={18} />
            <span>Belum Ada Riwayat Doffing</span>
          </div>
          <div className="empty-state-subtitle">
            Geser kartu mesin di layar Radar untuk mencatat doff, atau ketuk ikon gunting{" "}
            <span className="inline-icon-pill doff-pill">
              <ScissorsIcon size={12} /> Doffing
            </span>{" "}
            di konsol bawah untuk mencatat doff langsung.
          </div>
        </div>
      ) : (
        <>
          {/* Summary bar for history */}
          <div className="history-summary-bar">
            <div className="history-summary-item">
              <HistoryIcon size={14} />
              <span>
                Total: <strong>{state.aktual.length}</strong> Doff
              </span>
            </div>
            {totalYards > 0 && (
              <div className="history-summary-item">
                <SpoolIcon size={14} />
                <span>
                  Hasil: <strong>{Math.round(totalYards).toLocaleString()}</strong> yds
                </span>
              </div>
            )}
          </div>

          {reversed.length > 4 && (
            <div className="search-field-wrapper">
              <span className="search-field-icon">
                <SearchIcon size={15} />
              </span>
              <input
                className="filter-field with-search-icon"
                placeholder="Cari nomor mesin..."
                inputMode="numeric"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
            </div>
          )}

          {filtered.length === 0 ? (
            <div className="empty-state-card">
              <div className="empty-state-title">
                <SearchIcon size={18} />
                <span>Riwayat Tidak Ditemukan</span>
              </div>
              <div className="empty-state-subtitle">Coba cari dengan nomor mesin lainnya</div>
            </div>
          ) : (
            filtered.map(({ entry, num }) => {
              const mesin = state.db[entry.mcNo];
              const corak = entry.corakOverride ?? mesin?.corak ?? "—";
              const sub =
                entry.customYard != null
                  ? `${corak} · ${formatYard(entry.customYard)}y`
                  : mesin?.targetYard != null
                    ? `${corak} · ${formatYard(mesin.targetYard)}y`
                    : corak;
              const tipeColor = mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)";

              return (
                <div className="doff-row" key={entry.id}>
                  <div className="num" title={`Urutan doff #${num}`}>
                    {num}
                  </div>
                  <div className="tipe-icon" style={{ color: tipeColor }} title={mesin ? `Tipe ${mesin.tipe}` : ""}>
                    {mesin ? <MesinTipeIcon tipe={mesin.tipe} size={15} /> : <CircleIcon size={14} />}
                  </div>
                  <div className="main">
                    <div className="mcno">Mc {entry.mcNo}</div>
                    <div className="sub">
                      <span className="sub-kain-icon">
                        <TextureIcon size={11} />
                      </span>
                      <span>{sub}</span>
                    </div>
                  </div>
                  <div className="ket">{entry.ket}</div>
                  <div className="actions">
                    <button
                      className="icon-btn"
                      onClick={() => setEditing(entry)}
                      aria-label={`Edit riwayat Mc ${entry.mcNo}`}
                      title={`Edit data riwayat Mc ${entry.mcNo}`}
                    >
                      <EditIcon size={16} />
                    </button>
                    <button
                      className="icon-btn danger-hover"
                      style={{ color: "var(--red-500)" }}
                      onClick={() => handleHapus(entry.id)}
                      aria-label={`Hapus riwayat Mc ${entry.mcNo}`}
                      title={`Hapus riwayat Mc ${entry.mcNo}`}
                    >
                      <DeleteIcon size={16} />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </>
      )}

      {editing && <EditAktualDialog entry={editing} onClose={() => setEditing(null)} onDelete={handleHapus} />}
    </div>
  );
}

