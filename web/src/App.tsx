import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import { DoffStoreProvider, useDoffStore } from "./store/DoffStore";
import { UiStoreProvider, useUiStore } from "./store/UiStore";
import { useConsoleHandlers } from "./hooks/useConsoleHandlers";
import { ConsoleBar } from "./components/ConsoleBar";
import { RadarScreen } from "./components/RadarScreen";
import { DoffingScreen } from "./components/DoffingScreen";
import { StatistikScreen } from "./components/StatistikScreen";
import { SettingsScreen } from "./components/SettingsScreen";
import { BarisMesinScreen } from "./components/BarisMesinScreen";
import { ToastHost } from "./components/ToastHost";
import { ConfirmDialog } from "./components/ConfirmDialog";
import { GuidedEstimasiSheet } from "./components/GuidedEstimasiSheet";
import { GuidedDoffingSheet } from "./components/GuidedDoffingSheet";
import { OnboardingDialog } from "./components/OnboardingDialog";
import { ShiftFinishedOverlay } from "./components/ShiftFinishedOverlay";
import { SyncDialog } from "./components/SyncDialog";
import { WaveProgressBar } from "./components/WaveProgressBar";
import {
  BarChartIcon,
  CalendarIcon,
  CheckCircleIcon,
  FlagIcon,
  HistoryIcon,
  MoreVertIcon,
  QrCodeScannerIcon,
  RadarIcon,
  SettingsIcon,
  ShareIcon,
  SlidersIcon,
  WarningIcon,
} from "./components/Icons";
import { defaultMesinData, type MesinTipe } from "./domain/types";
import { currentShiftStartAbsMin, nowAbsMin, shiftNumberForEpochMin } from "./domain/format";
import { shareHistoryText, shareOrCopy } from "./domain/share";
import { isMachineDataEmpty } from "./domain/sync";

type Page = "RADAR" | "RIWAYAT";
type Screen = "main" | "statistik" | "settings" | "mesin";

function AppInner() {
  const { state, setMesin, setOnboardingSeen, undo, redo, canUndo, canRedo } = useDoffStore();
  const { showToast } = useUiStore();
  const { handleEstimasiSubmit, handleAktualSubmit, handleFinishShift } = useConsoleHandlers();
  const [page, setPage] = useState<Page>("RADAR");
  const [screen, setScreen] = useState<Screen>("main");
  const [guidedEstimasiMcNo, setGuidedEstimasiMcNo] = useState<string | null>(null);
  const [guidedDoffingMcNo, setGuidedDoffingMcNo] = useState<string | null>(null);
  const [staleDismissed, setStaleDismissed] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [syncOpen, setSyncOpen] = useState(false);
  const [autoQrDismissed, setAutoQrDismissed] = useState(false);
  const [showRemaining, setShowRemaining] = useState(false);
  const [actionsMenuOpen, setActionsMenuOpen] = useState(false);
  const [brandPulse, setBrandPulse] = useState(false);
  const [shiftFinishedVisible, setShiftFinishedVisible] = useState(false);
  const [, forceTick] = useState(0);

  const isDbEmpty = useMemo(() => isMachineDataEmpty(state.db), [state.db]);
  const shouldShowAutoQr = !state.onboardingSeen && isDbEmpty && !autoQrDismissed;

  // Tema: SYSTEM mengikuti preferensi OS, DARK/LIGHT dipaksa lewat atribut di <html>.
  useEffect(() => {
    const root = document.documentElement;
    if (state.themeMode === "SYSTEM") {
      root.removeAttribute("data-theme");
    } else {
      root.setAttribute("data-theme", state.themeMode === "DARK" ? "dark" : "light");
    }
  }, [state.themeMode]);

  // Perbarui hitungan "shift tertinggal" & progress header tiap 20 detik.
  useEffect(() => {
    const id = setInterval(() => forceTick((n) => n + 1), 20000);
    return () => clearInterval(id);
  }, []);

  // Cegah menu konteks bawaan browser (download gambar, bagikan, cetak) saat aksi sentuh & tahan (long-press) pada kartu/tombol
  useEffect(() => {
    const handleContextMenu = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) {
        return;
      }
      e.preventDefault();
    };

    window.addEventListener("contextmenu", handleContextMenu, { passive: false });
    return () => {
      window.removeEventListener("contextmenu", handleContextMenu);
    };
  }, []);

  const nowAbs = nowAbsMin();
  const staleCount = useMemo(() => {
    const shiftStart = currentShiftStartAbsMin(nowAbs);
    return state.aktual.filter((a) => a.tsEpochMin != null && a.tsEpochMin < shiftStart).length;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.aktual, nowAbs]);

  // Un-snooze begitu batch yang tertinggal benar-benar sudah terselesaikan (mis.
  // lewat Selesai Shift), supaya shift lain yang lupa ditutup nanti tidak ikut
  // terdiam oleh "Nanti" yang ditekan untuk kejadian sebelumnya.
  useEffect(() => {
    if (staleCount === 0) setStaleDismissed(false);
  }, [staleCount]);

  const shiftStartAbs = currentShiftStartAbsMin(nowAbs);
  const shiftEndAbs = shiftStartAbs + 8 * 60;
  const totalMc = useMemo(
    () =>
      new Set([
        ...Object.values(state.estimasi)
          .filter((est) => est.estAbsMin <= shiftEndAbs)
          .map((est) => est.mcNo),
        ...state.aktual.map((a) => a.mcNo),
      ]).size,
    [state.estimasi, state.aktual, shiftEndAbs],
  );
  const doffCount = state.aktual.length;
  const remainingMc = totalMc - doffCount;
  const showFinishShift = staleCount > 0 || (doffCount > 0 && nowAbs >= shiftEndAbs - 30);

  const shiftLabel = useMemo(() => {
    const date = new Date(nowAbs * 60000);
    const day = String(date.getDate()).padStart(2, "0");
    const month = String(date.getMonth() + 1).padStart(2, "0");
    return `Shift ${shiftNumberForEpochMin(nowAbs)} · ${day}/${month}`;
  }, [nowAbs]);

  function triggerBrandPulse() {
    setBrandPulse(true);
    setTimeout(() => setBrandPulse(false), 350);
  }

  function onFinishShiftAction() {
    setActionsMenuOpen(false);
    handleFinishShift(() => {
      setShiftFinishedVisible(true);
      setTimeout(() => setShiftFinishedVisible(false), 1000);
    });
  }

  async function handleShareAction() {
    setActionsMenuOpen(false);
    const outcome = await shareOrCopy(shareHistoryText(state), "Riwayat Doffing Adoel");
    if (outcome === "copied") showToast("Teks disalin ke clipboard ✓");
  }

  function openGuidedEstimasi(mcNo: string) {
    setGuidedEstimasiMcNo(mcNo);
  }

  function openGuidedDoffing(mcNo: string) {
    if (!state.db[mcNo]) {
      showToast(`⚠ Mc ${mcNo} tidak ditemukan`);
      return;
    }
    setGuidedDoffingMcNo(mcNo);
  }

  function quickUpdateMesin(
    mcNo: string,
    corak: string,
    targetYard: number | null,
    tipe: MesinTipe = defaultMesinData().tipe,
    koreksi: number | null = null,
    speed: number | null = null,
  ) {
    const mesin = state.db[mcNo] ?? defaultMesinData();
    setMesin(mcNo, { ...mesin, tipe, corak, targetYard, koreksi, speed });
  }

  function dismissKeyboardUnlessTypingHere(e: ReactMouseEvent) {
    const active = document.activeElement as HTMLElement | null;
    if (active && (active.tagName === "INPUT" || active.tagName === "TEXTAREA") && e.target !== active) {
      active.blur();
    }
    if (actionsMenuOpen) {
      setActionsMenuOpen(false);
    }
  }

  return (
    <div className="app-shell" onClick={dismissKeyboardUnlessTypingHere}>
      <ToastHost />
      <ShiftFinishedOverlay visible={shiftFinishedVisible} />

      <div className="edge-fade edge-fade-top" />
      <div className="edge-fade edge-fade-bottom" />

      <div className="app-header floating-card">
        <div className="app-header-top">
          <div className="app-branding" onClick={triggerBrandPulse} role="button" aria-label="Animasi logo">
            <div className={`app-title-wordmark${brandPulse ? " pulsing" : ""}`}>
              <span className="app-title-text">Adoel</span>
              <span className={`app-title-dot${brandPulse ? " hopping" : ""}`}>.</span>
            </div>
            <div className="app-shift-caption">
              <CalendarIcon size={11} />
              <span>{shiftLabel}</span>
            </div>
          </div>

          {totalMc > 0 && (
            <div
              className="shift-progress"
              onClick={() => setShowRemaining((s) => !s)}
              role="button"
              aria-label="Ganti tampilan selesai/sisa"
            >
              <div className="shift-progress-label-row">
                <CheckCircleIcon size={11} />
                <span className="shift-progress-text">
                  {!showRemaining ? `${doffCount}/${totalMc}` : remainingMc <= 0 ? "Selesai" : `${remainingMc} lagi`}
                </span>
              </div>
              <WaveProgressBar
                fraction={totalMc > 0 ? Math.min(1, Math.max(0, doffCount / totalMc)) : 0}
                trackColor="var(--bg-elevated-2)"
                fillColor="var(--cyan-500)"
                height={4}
                width={76}
              />
            </div>
          )}

          <div className="app-header-actions">
            {showFinishShift && (
              <button
                className="icon-btn finish-shift-pulse-btn"
                onClick={onFinishShiftAction}
                aria-label="Selesai Shift"
                title="Selesai Shift"
              >
                <FlagIcon size={18} />
              </button>
            )}

            <div className="menu-container">
              <button
                className="icon-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  setActionsMenuOpen((o) => !o);
                }}
                aria-label="Aksi lainnya"
              >
                <MoreVertIcon size={20} />
              </button>

              {actionsMenuOpen && (
                <div className="header-dropdown-menu" onClick={(e) => e.stopPropagation()}>
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      setActionsMenuOpen(false);
                      setScreen("mesin");
                    }}
                  >
                    <SlidersIcon size={16} />
                    <span>Daftar Mesin</span>
                  </button>
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      setActionsMenuOpen(false);
                      setScreen("statistik");
                    }}
                  >
                    <BarChartIcon size={16} />
                    <span>Statistik</span>
                  </button>
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      setActionsMenuOpen(false);
                      setScreen("settings");
                    }}
                  >
                    <SettingsIcon size={16} />
                    <span>Pengaturan</span>
                  </button>
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      setActionsMenuOpen(false);
                      setSyncOpen(true);
                    }}
                  >
                    <QrCodeScannerIcon size={16} />
                    <span>QR Sync</span>
                  </button>
                  <button className="dropdown-item" onClick={handleShareAction}>
                    <ShareIcon size={16} />
                    <span>Bagikan</span>
                  </button>
                  <button className="dropdown-item danger" onClick={onFinishShiftAction}>
                    <FlagIcon size={16} />
                    <span>Selesai Shift</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="page-toggle">
          <button
            className={page === "RADAR" ? "active" : ""}
            onClick={() => setPage("RADAR")}
            aria-label="Halaman Radar Pantauan"
          >
            <RadarIcon size={15} />
            <span>Radar</span>
            {Object.keys(state.estimasi).length > 0 && (
              <span className="toggle-badge">{Object.keys(state.estimasi).length}</span>
            )}
          </button>
          <button
            className={page === "RIWAYAT" ? "active" : ""}
            onClick={() => setPage("RIWAYAT")}
            aria-label="Halaman Riwayat Doffing"
          >
            <HistoryIcon size={15} />
            <span>Riwayat</span>
            {state.aktual.length > 0 && (
              <span className="toggle-badge">{state.aktual.length}</span>
            )}
          </button>
        </div>
      </div>

      {staleCount > 0 && !staleDismissed && (
        <div style={{ padding: "0 12px 8px" }}>
          <div className="banner">
            <WarningIcon size={16} filled={true} />
            <span className="msg">Ada {staleCount} catatan dari shift sebelumnya yang belum diarsipkan</span>
            <button onClick={onFinishShiftAction} style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
              <FlagIcon size={13} />
              <span>Selesai Shift</span>
            </button>
            <button onClick={() => setStaleDismissed(true)}>Nanti</button>
          </div>
        </div>
      )}

      {page === "RADAR" ? <RadarScreen onEditWaktu={openGuidedEstimasi} /> : <DoffingScreen />}

      <ConsoleBar
        onEstimasiClick={openGuidedEstimasi}
        onDoffingClick={openGuidedDoffing}
        onUndo={undo}
        onRedo={redo}
        canUndo={canUndo}
        canRedo={canRedo}
      />

      {guidedEstimasiMcNo && (
        <GuidedEstimasiSheet
          mcNo={guidedEstimasiMcNo}
          mesin={state.db[guidedEstimasiMcNo] ?? null}
          onDismiss={() => setGuidedEstimasiMcNo(null)}
          onSubmit={(value) => handleEstimasiSubmit(value, () => setGuidedEstimasiMcNo(null))}
          onQuickUpdate={(corak, targetYard, tipe, koreksi, speed) =>
            quickUpdateMesin(guidedEstimasiMcNo, corak, targetYard, tipe, koreksi, speed)
          }
        />
      )}

      {guidedDoffingMcNo && (
        <GuidedDoffingSheet
          mcNo={guidedDoffingMcNo}
          mesin={state.db[guidedDoffingMcNo] ?? null}
          estimasi={state.estimasi[guidedDoffingMcNo] ?? null}
          onDismiss={() => setGuidedDoffingMcNo(null)}
          onSubmitDoffing={(value) => handleAktualSubmit(value, () => setGuidedDoffingMcNo(null))}
          onQuickUpdate={(corak, targetYard, tipe, koreksi, speed) =>
            quickUpdateMesin(guidedDoffingMcNo, corak, targetYard, tipe, koreksi, speed)
          }
        />
      )}

      {screen === "mesin" && <BarisMesinScreen onClose={() => setScreen("main")} />}
      {screen === "statistik" && <StatistikScreen onClose={() => setScreen("main")} />}
      {screen === "settings" && <SettingsScreen onClose={() => setScreen("main")} onOpenHelp={() => setHelpOpen(true)} />}

      {/* Jendela QR Otomatis untuk Pengguna Baru bila Data Mesin Masih Kosong — Panduan menyusul
          setelah ini ditutup (onboardingSeen sengaja TIDAK diset di sini), bukan dilewati. */}
      {shouldShowAutoQr && (
        <SyncDialog
          initialTab="terima"
          isFirstTimeEmpty={true}
          onClose={() => {
            setAutoQrDismissed(true);
          }}
        />
      )}

      {syncOpen && !shouldShowAutoQr && <SyncDialog onClose={() => setSyncOpen(false)} />}

      {!shouldShowAutoQr && (!state.onboardingSeen || helpOpen) && (
        <OnboardingDialog
          onClose={() => {
            setOnboardingSeen();
            setHelpOpen(false);
          }}
        />
      )}

      <ConfirmDialog />
    </div>
  );
}

export default function App() {
  return (
    <DoffStoreProvider>
      <UiStoreProvider>
        <AppInner />
      </UiStoreProvider>
    </DoffStoreProvider>
  );
}
