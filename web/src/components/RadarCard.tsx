import { useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { absMinToTimeStr, formatDeltaMin, formatYard } from "../domain/format";
import { effectiveRemaining, urgencyLevel, type UrgencyLevel } from "../domain/estimasiUtils";
import { TIPE_COLOR } from "../domain/mesinVisual";
import type { Estimasi, MesinData } from "../domain/types";
import { WaveProgressBar } from "./WaveProgressBar";
import {
  MesinTipeIcon,
  PauseIcon,
  PlayIcon,
  DeleteIcon,
  ScissorsIcon,
  SparklesIcon,
  ScheduleIcon,
  WarningIcon,
  TextureIcon,
  CloseIcon,
  ShiftExchangeIcon,
  TagIcon,
} from "./Icons";

const REMINDER_LEAD_MIN = 5;
const SWIPE_THRESHOLD_PX = 88;
const SWIPE_MAX_PX = 140;
const LONG_PRESS_MS = 450;
const DRAG_INTENT_PX = 8;

const URGENCY_STYLE: Record<UrgencyLevel, { accent: string; bar: string; text: string; pulse: boolean }> = {
  CALM: { accent: "var(--cyan-500)", bar: "var(--cyan-500)", text: "var(--cyan-400)", pulse: false },
  SOON: { accent: "var(--amber-500)", bar: "var(--amber-400)", text: "var(--amber-400)", pulse: false },
  IMMINENT: { accent: "var(--amber-600, #d97706)", bar: "var(--amber-600, #d97706)", text: "var(--orange-400)", pulse: false },
  OVERDUE: { accent: "var(--red-500)", bar: "var(--red-500)", text: "var(--red-400, #f87171)", pulse: true },
};

/** Kartu radar — sentuh & tahan memunculkan menu aksi Jeda/Hapus di atas kartu,
 * kartu dijeda tampil langsung di bagian depan dengan tombol Lanjutkan instan,
 * swipe kanan = doff normal, swipe kiri = doff matching, tap zona nomor = ubah corak+yard,
 * tap zona waktu = ubah estimasi. */
export function RadarCard({
  est,
  mesin,
  nowAbs,
  clashingMcNos = [],
  onDoff,
  onDoffMatching,
  guardDoffMatching,
  onHapus,
  onJeda,
  onLanjutkan,
  onQuickEdit,
  onEditWaktu,
  shiftHandover = false,
}: {
  est: Estimasi;
  mesin: MesinData | null;
  nowAbs: number;
  clashingMcNos?: string[];
  onDoff: () => void;
  onDoffMatching: () => void;
  // Called instead of animating straight into onDoffMatching when set — lets the caller show a
  // confirm dialog first (e.g. the "potongan awal 70y" reminder) and only invoke [proceed] to
  // actually start the slide-out once the operator confirms. Swipe-right (Normal) has no such
  // gate, only Matching does.
  guardDoffMatching?: (proceed: () => void) => void;
  onHapus: () => void;
  onJeda: () => void;
  onLanjutkan: () => void;
  onQuickEdit: () => void;
  onEditWaktu: () => void;
  shiftHandover?: boolean;
}) {
  const remaining = effectiveRemaining(est, nowAbs);
  const isPaused = est.pausedAtAbsMin !== null && est.pausedAtAbsMin !== undefined;
  const level = urgencyLevel(remaining);
  const style = isPaused
    ? { accent: "var(--amber-500)", bar: "var(--amber-500)", text: "var(--amber-400)", pulse: false }
    : URGENCY_STYLE[level];
  const totalDur = est.estAbsMin - est.startAbsMin;
  const elapsed = isPaused ? est.pausedAtAbsMin! - est.startAbsMin : nowAbs - est.startAbsMin;
  const progress = totalDur > 0 ? Math.min(1, Math.max(0, elapsed / totalDur)) : 0;
  const corak = est.corakOverride ?? mesin?.corak ?? "—";
  const standardYard = est.yardOverride ?? mesin?.targetYard ?? null;
  const corakLine = standardYard != null ? `${corak} · ${formatYard(standardYard)}y` : corak;
  const showDot = !isPaused && remaining <= 5;
  const swipeEnabled = !isPaused && remaining <= REMINDER_LEAD_MIN;

  const [showActionsOverlay, setShowActionsOverlay] = useState(false);
  const [offsetX, setOffsetX] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [charging, setCharging] = useState(false);
  const [completing, setCompleting] = useState<"NORMAL" | "MATCHING" | null>(null);
  const dragStartX = useRef<number | null>(null);
  const dragStartY = useRef<number | null>(null);
  const pointerIdRef = useRef<number | null>(null);
  const cardElementRef = useRef<HTMLDivElement | null>(null);
  const wasDrag = useRef(false);
  const isScrollingY = useRef(false);
  const longPressTimer = useRef<number | null>(null);

  function clearLongPress() {
    if (longPressTimer.current != null) {
      window.clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
  }

  function handlePointerDown(e: ReactPointerEvent<HTMLDivElement>) {
    if (completing || showActionsOverlay) return;
    if (e.button !== 0 && e.pointerType === "mouse") return;

    dragStartX.current = e.clientX;
    dragStartY.current = e.clientY;
    pointerIdRef.current = e.pointerId;
    cardElementRef.current = e.currentTarget;
    wasDrag.current = false;
    isScrollingY.current = false;
    setCharging(true);

    clearLongPress();
    longPressTimer.current = window.setTimeout(() => {
      longPressTimer.current = null;
      if (!wasDrag.current && !isScrollingY.current) {
        setCharging(false);
        try {
          if (typeof navigator !== "undefined" && navigator.vibrate) {
            navigator.vibrate(35);
          }
        } catch {
          // ignore
        }
        setShowActionsOverlay(true);
        setOffsetX(0);
        setDragging(false);
        dragStartX.current = null;
        dragStartY.current = null;
      }
    }, LONG_PRESS_MS);
  }

  function handlePointerMove(e: ReactPointerEvent<HTMLDivElement>) {
    if (dragStartX.current === null || dragStartY.current === null || completing || showActionsOverlay) return;
    const dx = e.clientX - dragStartX.current;
    const dy = e.clientY - dragStartY.current;
    const absX = Math.abs(dx);
    const absY = Math.abs(dy);

    // Micro-wobble: gerakan mikro (< 10px) saat menahan jari jangan membatalkan tekan-tahan
    if (absX <= DRAG_INTENT_PX && absY <= DRAG_INTENT_PX) {
      return;
    }

    // Jika gerakan vertikal lebih besar, anggap pengguna sedang scroll daftar
    if (absY > absX && absY > DRAG_INTENT_PX) {
      isScrollingY.current = true;
      clearLongPress();
      setCharging(false);
      setDragging(false);
      setOffsetX(0);
      return;
    }

    // Jika gerakan horizontal lebih dominan: ini adalah swipe kartu
    if (absX > absY && absX > DRAG_INTENT_PX) {
      wasDrag.current = true;
      clearLongPress();
      setCharging(false);

      if (swipeEnabled) {
        // Tangkap pointer agar Chrome di ponsel tidak memutus event stream dengan pointercancel
        if (cardElementRef.current && pointerIdRef.current !== null) {
          try {
            if (!cardElementRef.current.hasPointerCapture(pointerIdRef.current)) {
              cardElementRef.current.setPointerCapture(pointerIdRef.current);
            }
          } catch {
            // ignore
          }
        }
        setDragging(true);
        setOffsetX(Math.max(-SWIPE_MAX_PX, Math.min(SWIPE_MAX_PX, dx)));
      }
    }
  }

  function endDrag(_e?: ReactPointerEvent<HTMLDivElement>) {
    clearLongPress();
    setCharging(false);

    if (cardElementRef.current && pointerIdRef.current !== null) {
      try {
        if (cardElementRef.current.hasPointerCapture(pointerIdRef.current)) {
          cardElementRef.current.releasePointerCapture(pointerIdRef.current);
        }
      } catch {
        // ignore
      }
    }

    dragStartX.current = null;
    dragStartY.current = null;
    pointerIdRef.current = null;
    setDragging(false);

    if (Math.abs(offsetX) >= SWIPE_THRESHOLD_PX) {
      triggerDoff(offsetX > 0 ? "NORMAL" : "MATCHING");
    } else {
      setOffsetX(0);
    }
  }

  function triggerDoff(kind: "NORMAL" | "MATCHING") {
    if (completing) return;

    function startAnim() {
      setCompleting(kind);
      setOffsetX(kind === "NORMAL" ? 420 : -420);
      window.setTimeout(() => {
        if (kind === "NORMAL") onDoff();
        else onDoffMatching();
      }, 950);
    }

    if (kind === "MATCHING" && guardDoffMatching) {
      // Snap the card back to neutral right away instead of optimistically sliding it off —
      // the guard may show a confirm dialog, and if the operator cancels there'd be nothing to
      // undo the slide-out with. startAnim only runs if/when the guard calls proceed().
      setOffsetX(0);
      guardDoffMatching(startAnim);
      return;
    }
    startAnim();
  }

  function handleZoneClick(action: () => void) {
    if (wasDrag.current || showActionsOverlay) return;
    action();
  }

  const revealSide: "right" | "left" | null = offsetX > 4 ? "right" : offsetX < -4 ? "left" : null;
  const revealOpacity = Math.min(1, Math.abs(offsetX) / SWIPE_THRESHOLD_PX);

  // Jika kartu sedang dijeda, tampilkan kartu langsung di bagian depan dengan styling khusus
  if (isPaused) {
    return (
      <div
        className="radar-card-outer radar-card-paused-outer"
        style={{ ["--urgency-accent" as any]: "var(--amber-500)" }}
        onContextMenu={(e) => e.preventDefault()}
      >
        <div className="radar-card-front radar-card-paused" onContextMenu={(e) => e.preventDefault()}>
          <div className="radar-card-accent" style={{ background: "var(--amber-500)" }} />
          <div className="radar-card-body">
            <div className="radar-card-main" onClick={() => handleZoneClick(onQuickEdit)} role="button">
              <div className="radar-card-title-row">
                <span className="radar-card-mcno" style={{ fontSize: est.mcNo.length >= 3 ? 23 : 27 }}>
                  {est.mcNo}
                </span>
                {mesin && (
                  <span className="radar-card-tipe-icon" style={{ color: TIPE_COLOR[mesin.tipe] }}>
                    <MesinTipeIcon tipe={mesin.tipe} size={12} />
                  </span>
                )}
                <span className="radar-card-tipe-label" style={{ color: mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)" }}>
                  {mesin?.tipe ?? "?"}
                </span>
                <span className="radar-paused-badge">
                  <PauseIcon size={11} />
                  <span>DIJEDA</span>
                </span>
              </div>

              <div className="radar-card-corak" style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <TextureIcon size={11} />
                <span>{corakLine}</span>
              </div>

              <div className="radar-paused-status-line">
                <span>Dibekukan pada sisa:</span>
                <strong style={{ color: "var(--amber-400)" }}>{formatDeltaMin(remaining)}</strong>
              </div>
            </div>

            {/* Aksi langsung di bagian depan kartu dijeda */}
            <div className="radar-paused-actions">
              <button
                type="button"
                className="radar-resume-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  onLanjutkan();
                }}
                aria-label={`Lanjutkan mesin ${est.mcNo}`}
              >
                <PlayIcon size={16} />
                <span>Lanjutkan</span>
              </button>
              <button
                type="button"
                className="radar-paused-delete-icon-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  onHapus();
                }}
                title="Hapus estimasi"
                aria-label={`Hapus estimasi Mc ${est.mcNo}`}
              >
                <DeleteIcon size={16} />
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className="radar-card-outer"
      style={{ ["--urgency-accent" as any]: style.accent }}
      onContextMenu={(e) => e.preventDefault()}
    >
      {revealSide && !completing && (
        <div className={`radar-card-swipe-bg ${revealSide}`} style={{ opacity: revealOpacity }}>
          {revealSide === "right" ? (
            <div className="radar-swipe-hint-content right">
              <div className="radar-swipe-hint-icon">
                <ScissorsIcon size={22} />
              </div>
              <div className="radar-swipe-hint-text">
                <div className="radar-swipe-hint-title">Doffing Normal</div>
                <div className="radar-swipe-hint-desc">Target yard selesai</div>
              </div>
            </div>
          ) : (
            <div className="radar-swipe-hint-content left">
              <div className="radar-swipe-hint-text right-align">
                <div className="radar-swipe-hint-title">Doffing Matching</div>
                <div className="radar-swipe-hint-desc">Sampel beam baru · Uji kualitas</div>
              </div>
              <div className="radar-swipe-hint-icon matching">
                <SparklesIcon size={22} />
              </div>
            </div>
          )}
        </div>
      )}
      <div
        className="radar-card-swipe"
        style={{
          transform: `translateX(${offsetX}px)`,
          transition: dragging
            ? "none"
            : completing
              ? "transform 0.3s cubic-bezier(0.4,0,0.2,1)"
              : "transform 0.32s cubic-bezier(0.34,1.56,0.64,1)",
          opacity: completing ? 0.15 : 1,
        }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onContextMenu={(e) => e.preventDefault()}
      >
        <div
          className={`radar-card-front${level === "OVERDUE" ? " overdue" : ""}${charging ? " charging" : ""}`}
          onContextMenu={(e) => e.preventDefault()}
        >
          {/* Visual indikator saat aksi sentuh & tahan sedang berlangsung */}
          {charging && <div className="radar-card-charge-bar" />}
          <div className="radar-card-charge-overlay" />
          <div className="radar-card-accent" />
          <div className="radar-card-body">
            <div className="radar-card-main" onClick={() => handleZoneClick(onQuickEdit)} role="button">
              <div className="radar-card-title-row">
                <span
                  className="radar-card-mcno"
                  style={{ fontSize: est.mcNo.length >= 3 ? 23 : 27 }}
                >
                  {est.mcNo}
                </span>
                {mesin && (
                  <span className="radar-card-tipe-icon" style={{ color: TIPE_COLOR[mesin.tipe] }}>
                    <MesinTipeIcon tipe={mesin.tipe} size={12} />
                  </span>
                )}
                <span className="radar-card-tipe-label" style={{ color: mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)" }}>
                  {mesin?.tipe ?? "?"}
                </span>
                {level === "SOON" && (
                  <span className="radar-card-urgency-icon" style={{ color: "var(--amber-400)" }}>
                    <ScheduleIcon size={12} />
                  </span>
                )}
                {level === "IMMINENT" && (
                  <span className="radar-card-urgency-icon" style={{ color: "var(--amber-500)" }}>
                    <WarningIcon size={12} />
                  </span>
                )}
                {level === "OVERDUE" && (
                  <span className="radar-card-urgency-icon" style={{ color: "var(--red-400)" }}>
                    <WarningIcon size={12} filled />
                  </span>
                )}
                {clashingMcNos.length > 0 && (
                  <span className="radar-clash-badge" title={`Bentrok waktu dengan Mc ${clashingMcNos.join(", ")}`}>
                    <WarningIcon size={10} filled />
                    <span>Bentrok Mc {clashingMcNos.join(", ")}</span>
                  </span>
                )}
                {shiftHandover && (
                  <span className="shift-badge">
                    <ShiftExchangeIcon size={10} />
                    <span>OPERAN SHIFT</span>
                  </span>
                )}
              </div>

              <div className="radar-card-corak">
                <TextureIcon size={11} />
                <span>{corakLine}</span>
              </div>

              <WaveProgressBar fraction={progress} trackColor="var(--bg-elevated-2)" fillColor={style.bar} height={3} />
            </div>

            <div className="radar-card-time" onClick={() => handleZoneClick(onEditWaktu)} role="button">
              {showDot && <span className={`ping-dot${remaining < 0 ? " danger" : ""}`} />}
              <div className="radar-card-time-text">
                <div className="abs" style={{ color: style.text }}>
                  {absMinToTimeStr(est.estAbsMin)}
                </div>
                <div className="rel" style={{ color: remaining < 0 ? "var(--red-500)" : style.text }}>
                  {formatDeltaMin(remaining)}
                </div>
              </div>
            </div>
          </div>

          {/* Quick Action Overlay saat sentuh & tahan selesai */}
          {showActionsOverlay && (
            <div className="radar-actions-overlay" onClick={(e) => e.stopPropagation()}>
              <div className="radar-actions-overlay-head">
                <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
                  <TagIcon size={13} />
                  <span className="radar-actions-overlay-title">Opsi Mesin {est.mcNo}</span>
                </div>
                <button
                  type="button"
                  className="radar-actions-close-btn"
                  onClick={() => setShowActionsOverlay(false)}
                  aria-label="Tutup menu aksi"
                >
                  <CloseIcon size={16} />
                </button>
              </div>
              <div className="radar-actions-overlay-btns">
                <button
                  type="button"
                  className="radar-action-chip jeda"
                  onClick={() => {
                    setShowActionsOverlay(false);
                    onJeda();
                  }}
                >
                  <PauseIcon size={16} />
                  <span>Jeda Mesin</span>
                </button>
                <button
                  type="button"
                  className="radar-action-chip hapus"
                  onClick={() => {
                    setShowActionsOverlay(false);
                    onHapus();
                  }}
                >
                  <DeleteIcon size={16} />
                  <span>Hapus Estimasi</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {completing && (
        <div className={`radar-card-celebrate ${completing === "MATCHING" ? "matching" : "normal"}`}>
          <div className="radar-card-celebrate-content">
            <div className="radar-card-celebrate-icon">
              {completing === "MATCHING" ? <SparklesIcon size={24} /> : <ScissorsIcon size={24} />}
            </div>
            <div className="radar-card-celebrate-text">
              <span className="radar-card-celebrate-title">
                {completing === "MATCHING" ? "Doffing Matching (Sampel)" : "Doffing Normal"}
              </span>
              <span className="radar-card-celebrate-subtitle">
                {completing === "MATCHING" ? "Tercatat untuk cek kualitas beam" : "Tercatat selesai target yard"}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

