import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { formatDeltaMin, nowAbsMin } from "../domain/format";
import { effectiveRemaining } from "../domain/estimasiUtils";

function vibrate(pattern: number | number[]) {
  navigator.vibrate?.(pattern);
}

/** Handler aksi konsol/radar/doffing — port 1:1 dari MainScreenHandlers.kt (aplikasi Android).
 * Membungkus DoffStore (murni) + riwayat undo/redo tingkat-konsol + toast/haptic, dipakai
 * ConsoleBar/guided sheets (App.tsx) dan RadarCard (RadarScreen.tsx) supaya kedua jalur aksi
 * itu (ketik nomor mesin vs swipe/tekan-tahan kartu) berbagi persis perilaku yang sama. */
export function useConsoleHandlers() {
  const store = useDoffStore();
  const {
    state,
    submitEstimasi,
    submitAktual,
    hapusEstimasi,
    restoreEstimasi,
    pauseEstimasi,
    resumeEstimasi,
    toggleEstimasiMatching,
    markPendingMatching,
    hapusAktualById,
    restoreAktual,
    finishShift,
    pushUndo,
  } = store;
  const { showToast, showConfirm } = useUiStore();

  function flashError(msg: string) {
    vibrate(30);
    showToast(`⚠ ${msg}`);
  }

  function submitEstimasiNow(cmd: string, onCleared?: () => void) {
    const result = submitEstimasi(cmd);
    if (result.ok) {
      vibrate(20);
      showToast(result.msg);
      onCleared?.();
    } else {
      flashError(result.msg);
    }
  }

  /** Ketik/tap Estimasi. Estimasi yang masih aktif & mepet (<10 menit lagi) minta konfirmasi
   * dulu sebelum ditimpa — prosesBarisKondisiMesin sendiri tidak punya undo. */
  function handleEstimasiSubmit(cmd: string, onCleared?: () => void) {
    const mcNo = cmd.trim().split(/\s+/)[0];
    const existing = state.estimasi[mcNo];
    const remaining = existing ? effectiveRemaining(existing, nowAbsMin()) : null;
    if (existing && remaining != null && remaining >= 0 && remaining < 10) {
      showConfirm(`Mc ${mcNo} sudah diestimasi ${formatDeltaMin(remaining)} lagi. Timpa dengan estimasi baru?`, () => {
        submitEstimasiNow(cmd, onCleared);
      });
    } else {
      submitEstimasiNow(cmd, onCleared);
    }
  }

  /** Kirim command AKTUAL mentah ("$mcNo $value") — dipakai baik oleh tap tombol Doff/Matching
   * di kartu radar (mcNo saja / mcNo+"MATCHING") maupun oleh GuidedDoffingSheet (mcNo+yard atau
   * mcNo+keterangan+yard), sama seperti handleCommand(Mode.AKTUAL, ...) di MainScreenHandlers.kt. */
  function handleAktualSubmit(cmd: string, onCleared?: () => void) {
    const result = submitAktual(cmd);
    if (result.ok) {
      vibrate(20);
      const entry = result.entry;
      const undoFn = result.undo;
      const prevEst = result.prevEst ?? null;
      pushUndo({
        undo: () => {
          undoFn?.();
          if (prevEst) restoreEstimasi(prevEst);
        },
        // Mengembalikan entri persis yang sama (id sama) alih-alih menjalankan ulang command,
        // supaya tiap siklus undo/redo tidak menyisakan duplikat di Riwayat.
        redo: () => {
          if (entry) restoreAktual(entry);
          hapusEstimasi(result.mcNo);
        },
      });
      showToast(result.msg);
      onCleared?.();
      // Tali Hijau: HB (Habis Beam) berarti beam lusi lama habis dan beam baru sudah naik — potongan
      // pertama gulungan itu adalah sampel Matching, dan operator harus memasang tali hijau fisik di
      // tepi kainnya. Tawarkan langsung menandai isMatching untuk siklus berikutnya, supaya nanti
      // waktu doffingnya tiba operator tidak perlu memeriksa kain lagi. Cek pada string ket yang
      // sudah dibakukan (bukan cmd mentah), sama seperti extra.includes("MATCHING") di
      // commands.ts — ket selalu berbentuk "jam(HB)" persis, tidak pernah tergabung dengan token lain.
      if (entry && entry.ket.includes("(HB)")) {
        showConfirm(
          `⚠️ Pengingat Beam Baru Mc ${entry.mcNo}: Pasangkan tali hijau pada tepi kain gulungan awal!`,
          () => markPendingMatching(entry.mcNo),
          { confirmLabel: "Sudah Pasang & Tandai Matching", cancelLabel: "Nanti / Lewati" },
        );
      }
    } else {
      showToast(`⚠ ${result.msg}`);
    }
  }

  // "MATCHING" doffs are gated one layer up, in RadarCard's guardDoffMatching (wired from
  // RadarScreen) — that has to run *before* the swipe's slide-out animation starts, not here
  // after it's already played. GuidedDoffingSheet's Matching pick gates itself the same way, in
  // its own component. This function's own keterangan is trusted as already-confirmed.
  function handleDoff(mcNo: string, keterangan?: string) {
    handleAktualSubmit(keterangan ? `${mcNo} ${keterangan}` : mcNo);
  }

  function handleHapusEst(mcNo: string) {
    showConfirm(`Hapus estimasi Mc ${mcNo}?`, () => {
      const prevEst = state.estimasi[mcNo] ?? null;
      hapusEstimasi(mcNo);
      vibrate(20);
      pushUndo({
        undo: () => {
          if (prevEst) restoreEstimasi(prevEst);
        },
        redo: () => hapusEstimasi(mcNo),
      });
      showToast(`Mc ${mcNo} dihapus`);
    });
  }

  /** Membekukan hitung mundur Mc mcNo (RadarCard tekan-tahan → Jeda) — tanpa dialog konfirmasi,
   * berbeda dari Hapus, karena sepenuhnya reversibel dengan satu tap lagi (Lanjutkan). */
  function handleJeda(mcNo: string) {
    const prevEst = state.estimasi[mcNo];
    if (!prevEst) return;
    pauseEstimasi(mcNo);
    vibrate(20);
    pushUndo({
      undo: () => restoreEstimasi(prevEst),
      redo: () => pauseEstimasi(mcNo),
    });
    showToast(`Mc ${mcNo} dijeda`);
  }

  function handleLanjutkan(mcNo: string) {
    const prevEst = state.estimasi[mcNo];
    if (!prevEst) return;
    resumeEstimasi(mcNo);
    vibrate(20);
    pushUndo({
      undo: () => restoreEstimasi(prevEst),
      redo: () => resumeEstimasi(mcNo),
    });
    showToast(`Mc ${mcNo} dilanjutkan`);
  }

  /** Tali Hijau: RadarCard's always-visible one-tap toggle. No confirm dialog and no reminder
   * reschedule — isMatching never touches estAbsMin, only which flavor of doff gets forced at
   * commands.ts prosesBarisUmum once the machine's time actually comes. */
  function handleToggleMatching(mcNo: string) {
    const prevEst = state.estimasi[mcNo];
    if (!prevEst) return;
    toggleEstimasiMatching(mcNo);
    vibrate(20);
    pushUndo({
      undo: () => restoreEstimasi(prevEst),
      redo: () => toggleEstimasiMatching(mcNo),
    });
    const nowMatching = !prevEst.isMatching;
    showToast(nowMatching ? `Mc ${mcNo} ditandai Tali Hijau` : `Penanda Tali Hijau Mc ${mcNo} dilepas`);
  }

  function handleHapusAktual(id: number, onCleared?: () => void) {
    const entry = state.aktual.find((a) => a.id === id);
    if (!entry) return;
    showConfirm(`Hapus riwayat Mc ${entry.mcNo}?`, () => {
      hapusAktualById(id);
      vibrate(20);
      onCleared?.();
      pushUndo({
        undo: () => restoreAktual(entry),
        redo: () => hapusAktualById(id),
      });
      showToast(`Mc ${entry.mcNo} dihapus`);
    });
  }

  function handleFinishShift(onFinished?: () => void) {
    const doffCount = state.aktual.length;
    const estCount = Object.keys(state.estimasi).length;
    if (doffCount === 0 && estCount === 0) {
      showToast("Tidak ada data untuk diarsipkan");
      return;
    }

    const confirmMsg =
      doffCount > 0
        ? estCount > 0
          ? `Akhiri shift? ${doffCount} riwayat doffing akan diarsipkan ke Statistik, dan ${estCount} estimasi aktif akan dihapus.`
          : `Akhiri shift? ${doffCount} riwayat doffing akan diarsipkan ke Statistik.`
        : `Akhiri shift? Tidak ada riwayat doffing untuk diarsipkan, ${estCount} estimasi aktif akan dihapus.`;

    showConfirm(confirmMsg, () => {
      finishShift();
      vibrate(20);
      onFinished?.();
      showToast("Shift selesai ✓");
    });
  }

  return {
    handleEstimasiSubmit,
    handleAktualSubmit,
    handleDoff,
    handleHapusEst,
    handleJeda,
    handleLanjutkan,
    handleToggleMatching,
    handleHapusAktual,
    handleFinishShift,
    flashError,
  };
}
