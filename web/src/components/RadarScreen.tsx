import { useEffect, useMemo, useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { useConsoleHandlers } from "../hooks/useConsoleHandlers";
import { isPotonganAwalCorak, potonganAwalReminderMessage } from "../domain/matchingRules";
import {
  BREAK_GAP_THRESHOLD_MIN,
  effectiveRemaining,
  findClashingMachines,
  partitionSegeraMenunggu,
  sortedByNearest,
  urgencyLevel,
  type UrgencyLevel,
} from "../domain/estimasiUtils";
import { absMinToTimeStr, currentShiftStartAbsMin, formatDeltaMin, nowAbsMin } from "../domain/format";
import type { Estimasi } from "../domain/types";
import { RadarCard } from "./RadarCard";
import { QuickEditDialog } from "./QuickEditDialog";
import {
  CalendarIcon,
  ClockIcon,
  FlameIcon,
  HourglassEmptyIcon,
  PauseIcon,
  RadarEmptyIllustration,
  RadarIcon,
  ScheduleIcon,
  SearchIcon,
  ShiftExchangeIcon,
} from "./Icons";

export function RadarScreen({ onEditWaktu }: { onEditWaktu: (mcNo: string) => void }) {
  const { state } = useDoffStore();
  const { showConfirm } = useUiStore();
  const { handleDoff, handleHapusEst, handleJeda, handleLanjutkan } = useConsoleHandlers();
  const [filter, setFilter] = useState("");
  const [quickEditMcNo, setQuickEditMcNo] = useState<string | null>(null);
  const [, forceTick] = useState(0);

  // Runs before RadarCard's swipe-left slide-out animation starts (not after, like handleDoff's
  // own gate would be too late for) — see RadarCard's guardDoffMatching doc for why.
  function guardDoffMatching(mcNo: string, proceed: () => void) {
    const corak = state.db[mcNo]?.corak;
    if (isPotonganAwalCorak(state, corak)) {
      showConfirm(potonganAwalReminderMessage(corak!), proceed);
    } else {
      proceed();
    }
  }

  useEffect(() => {
    const id = setInterval(() => forceTick((n) => n + 1), 20000);
    return () => clearInterval(id);
  }, []);

  const nowAbs = nowAbsMin();
  const all = useMemo(() => sortedByNearest(state.estimasi), [state.estimasi]);
  const filtered = useMemo(() => {
    if (!filter.trim()) return all;
    const f = filter.trim().toLowerCase();
    return all.filter((e) => e.mcNo.toLowerCase().includes(f));
  }, [all, filter]);

  // Pisahkan kartu yang sedang dijeda dari antrean kartu estimasi yang sedang berjalan
  const dijeda = useMemo(() => filtered.filter((est) => est.pausedAtAbsMin != null), [filtered]);
  const activeFiltered = useMemo(() => filtered.filter((est) => est.pausedAtAbsMin == null), [filtered]);

  const [segera, menunggu] = useMemo(() => partitionSegeraMenunggu(activeFiltered, nowAbs), [activeFiltered, nowAbs]);
  const shiftEndAbs = currentShiftStartAbsMin(nowAbs) + 8 * 60;
  const activeMenunggu = menunggu;

  if (all.length === 0) {
    return (
      <div className="scroll-area">
        <div className="empty-state-card animated-empty-card">
          <RadarEmptyIllustration />
          <div className="empty-state-title">
            <RadarIcon size={18} />
            <span>Radar Siap Memantau</span>
          </div>
          <div className="empty-state-subtitle">
            Belum ada estimasi aktif. Ketik nomor mesin di konsol bawah, lalu ketuk ikon jam{" "}
            <span className="inline-icon-pill">
              <ScheduleIcon size={12} /> Estimasi
            </span>{" "}
            untuk mulai memantau waktu doffing.
          </div>
        </div>
      </div>
    );
  }

  const nearestActive = activeFiltered[0] ?? null;

  return (
    <div className="scroll-area">
      {/* Live Monitoring Status Header */}
      <div className="radar-status-bar">
        <div className="radar-status-left">
          <div className="radar-live-blip">
            <span className="blip-ring" />
            <span className="blip-core" />
          </div>
          <span className="radar-status-text">
            Radar Aktif: <strong>{all.length}</strong> Mesin
          </span>
        </div>
        {nearestActive && (
          <div className="radar-status-nearest">
            <ClockIcon size={12} />
            <span>
              Terdekat: Mc {nearestActive.mcNo} ({formatDeltaMin(nearestActive.estAbsMin - nowAbs)})
            </span>
          </div>
        )}
      </div>

      {all.length > 4 && (
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

      {filtered.length === 0 && (
        <div className="empty-state-card">
          <div className="empty-state-title">
            <SearchIcon size={18} />
            <span>Mesin Tidak Ditemukan</span>
          </div>
          <div className="empty-state-subtitle">Coba kata kunci lain — masukkan nomor mesin yang terdaftar</div>
        </div>
      )}

      {/* Bagian Khusus: Kartu Estimasi yang Sedang Dijeda */}
      {dijeda.length > 0 && (
        <div className="urgency-band paused-section-band">
          <div className="urgency-band-header" style={{ color: "var(--amber-400, #fbbf24)" }}>
            <div className="urgency-band-title-group">
              <PauseIcon size={15} />
              <span className="urgency-band-title">Dijeda</span>
            </div>
            <span
              className="urgency-band-count"
              style={{ background: "rgba(245, 158, 11, 0.18)", color: "var(--amber-400, #fbbf24)" }}
            >
              {dijeda.length} Mesin
            </span>
          </div>
          {dijeda.map((est) => (
            <RadarCard
              key={est.mcNo}
              est={est}
              mesin={state.db[est.mcNo] ?? null}
              nowAbs={nowAbs}
              clashingMcNos={[]}
              onDoff={() => handleDoff(est.mcNo)}
              onDoffMatching={() => handleDoff(est.mcNo, "MATCHING")}
              guardDoffMatching={(proceed) => guardDoffMatching(est.mcNo, proceed)}
              onHapus={() => handleHapusEst(est.mcNo)}
              onJeda={() => handleJeda(est.mcNo)}
              onLanjutkan={() => handleLanjutkan(est.mcNo)}
              onQuickEdit={() => setQuickEditMcNo(est.mcNo)}
              onEditWaktu={() => onEditWaktu(est.mcNo)}
              shiftHandover={est.estAbsMin > shiftEndAbs}
            />
          ))}
        </div>
      )}

      {segera.length > 0 && (
        <div className="urgency-band">
          <div className="urgency-band-header" style={{ color: "var(--red-400, #f87171)" }}>
            <div className="urgency-band-title-group">
              <FlameIcon size={15} />
              <span className="urgency-band-title">Segera</span>
            </div>
            <span
              className="urgency-band-count"
              style={{ background: "rgba(239, 68, 68, 0.18)", color: "var(--red-400, #f87171)" }}
            >
              {segera.length} Mesin
            </span>
          </div>
          {segera.map((est) => (
            <RadarCard
              key={est.mcNo}
              est={est}
              mesin={state.db[est.mcNo] ?? null}
              nowAbs={nowAbs}
              clashingMcNos={findClashingMachines(est.mcNo, all)}
              onDoff={() => handleDoff(est.mcNo)}
              onDoffMatching={() => handleDoff(est.mcNo, "MATCHING")}
              guardDoffMatching={(proceed) => guardDoffMatching(est.mcNo, proceed)}
              onHapus={() => handleHapusEst(est.mcNo)}
              onJeda={() => handleJeda(est.mcNo)}
              onLanjutkan={() => handleLanjutkan(est.mcNo)}
              onQuickEdit={() => setQuickEditMcNo(est.mcNo)}
              onEditWaktu={() => onEditWaktu(est.mcNo)}
              shiftHandover={est.estAbsMin > shiftEndAbs}
            />
          ))}
        </div>
      )}

      {menunggu.length > 0 && (
        <div className="urgency-band">
          <div className="urgency-band-header" style={{ color: menungguAccent(menunggu, nowAbs) }}>
            <div className="urgency-band-title-group">
              <ClockIcon size={15} />
              <span className="urgency-band-title">Menunggu</span>
            </div>
            <span
              className="urgency-band-count"
              style={{
                background: "rgba(6, 182, 212, 0.18)",
                color: menungguAccent(menunggu, nowAbs),
              }}
            >
              {menunggu.length} Mesin
            </span>
          </div>

          {/* Leading break gap (jeda waktu aktif sebelum mesin pertama) */}
          {segera.length === 0 && activeMenunggu[0] && activeMenunggu[0].estAbsMin - nowAbs >= BREAK_GAP_THRESHOLD_MIN && (
            <BreakGapCard
              gapMin={activeMenunggu[0].estAbsMin - nowAbs}
              nextMcNo={activeMenunggu[0].mcNo}
              nextAbsMin={activeMenunggu[0].estAbsMin}
              nowAbs={nowAbs}
              isActive={true}
            />
          )}

          {menunggu.map((est, i) => {
            const activeIndex = activeMenunggu.findIndex((candidate) => candidate.mcNo === est.mcNo);
            const next = activeIndex >= 0 ? activeMenunggu[activeIndex + 1] : undefined;
            const gap = next ? next.estAbsMin - est.estAbsMin : 0;
            const prevEst = menunggu[i - 1];
            const isCrossingShift = est.estAbsMin > shiftEndAbs && (!prevEst || prevEst.estAbsMin <= shiftEndAbs);

            return (
              <div key={est.mcNo} className="radar-row-wrapper">
                {isCrossingShift && (
                  <div className="shift-boundary-divider">
                    <div className="divider-line" />
                    <div className="divider-content">
                      <ShiftExchangeIcon size={14} />
                      <span className="divider-label">OPERAN SHIFT</span>
                    </div>
                    <div className="divider-line" />
                  </div>
                )}
                <RadarCard
                  est={est}
                  mesin={state.db[est.mcNo] ?? null}
                  nowAbs={nowAbs}
                  clashingMcNos={findClashingMachines(est.mcNo, all)}
                  onDoff={() => handleDoff(est.mcNo)}
                  onDoffMatching={() => handleDoff(est.mcNo, "MATCHING")}
                  guardDoffMatching={(proceed) => guardDoffMatching(est.mcNo, proceed)}
                  onHapus={() => handleHapusEst(est.mcNo)}
                  onJeda={() => handleJeda(est.mcNo)}
                  onLanjutkan={() => handleLanjutkan(est.mcNo)}
                  onQuickEdit={() => setQuickEditMcNo(est.mcNo)}
                  onEditWaktu={() => onEditWaktu(est.mcNo)}
                  shiftHandover={est.estAbsMin > shiftEndAbs}
                />
                {next && gap >= BREAK_GAP_THRESHOLD_MIN && (
                  <BreakGapCard
                    gapMin={gap}
                    nextMcNo={next.mcNo}
                    nextAbsMin={next.estAbsMin}
                    nowAbs={nowAbs}
                    isActive={false}
                  />
                )}
              </div>
            );
          })}
        </div>
      )}

      {quickEditMcNo && <QuickEditDialog mcNo={quickEditMcNo} onClose={() => setQuickEditMcNo(null)} />}
    </div>
  );
}

function BreakGapCard({
  gapMin,
  nextMcNo,
  nextAbsMin,
  nowAbs,
  isActive,
}: {
  gapMin: number;
  nextMcNo: string;
  nextAbsMin: number;
  nowAbs: number;
  isActive: boolean;
}) {
  const remainingMin = Math.max(0, nextAbsMin - nowAbs);
  const displayMin = isActive ? remainingMin : gapMin;
  const elapsedFraction = gapMin > 0 ? Math.min(1, Math.max(0, 1 - remainingMin / gapMin)) : 0;

  return (
    <div className="break-gap-card">
      <div className="break-gap-card-header">
        <HourglassEmptyIcon size={14} />
        <span>Selang Waktu {displayMin} Menit</span>
      </div>
      <div className="break-gap-card-big">{formatDeltaMin(displayMin)}</div>
      <div className="break-gap-card-sub">
        <CalendarIcon size={11} />
        <span>
          Sampai {absMinToTimeStr(nextAbsMin)} — sebelum Mc {nextMcNo}
        </span>
      </div>
      {isActive && (
        <div className="break-gap-card-progress">
          <div className="break-gap-card-bar" style={{ width: `${Math.round(elapsedFraction * 100)}%` }} />
        </div>
      )}
    </div>
  );
}

function menungguAccent(menunggu: Estimasi[], nowAbs: number): string {
  let worst: UrgencyLevel = "CALM";
  for (const e of menunggu) {
    const level = urgencyLevel(effectiveRemaining(e, nowAbs));
    if (rank(level) > rank(worst)) worst = level;
  }
  if (worst === "IMMINENT") return "var(--orange-400, #fb923c)";
  if (worst === "SOON") return "var(--amber-400, #fbbf24)";
  return "var(--cyan-400, #22d3ee)";
}
function rank(l: UrgencyLevel): number {
  return { CALM: 0, SOON: 1, IMMINENT: 2, OVERDUE: 3 }[l];
}

