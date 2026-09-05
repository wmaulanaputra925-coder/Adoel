import { useEffect, useState } from "react";
import { useUiStore } from "../store/UiStore";
import { CloseIcon } from "./Icons";

/** Toast tanpa aksi "URUNGKAN" lagi — undo/redo sekarang tingkat konsol lewat tombol Undo/Redo
 * di ConsoleBar, bukan closure per-toast. Tombol X di sini murni menutup lebih awal. Port 1:1
 * dari redesign ToastHost.kt (aplikasi Android, Master Blueprint v9.2 §9). */
export function ToastHost() {
  const { toast, dismissToast } = useUiStore();
  // Toast terakhir ditahan sebentar setelah dibuang supaya animasi keluarnya sempat jalan.
  // Sebelumnya komponen langsung `return null` begitu toast dikosongkan, jadi toast lenyap
  // seketika padahal masuknya beranimasi — di Android ToastHost.kt selalu slide + fade keluar.
  const [shown, setShown] = useState(toast);
  const leaving = toast === null;

  useEffect(() => {
    if (toast) setShown(toast);
  }, [toast]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(dismissToast, 3500);
    return () => clearTimeout(t);
  }, [toast, dismissToast]);

  useEffect(() => {
    if (toast || !shown) return;
    const t = setTimeout(() => setShown(null), 200);
    return () => clearTimeout(t);
  }, [toast, shown]);

  if (!shown) return null;

  // Pesan gagal selalu diawali "⚠" (lihat flashError di useConsoleHandlers) — beri cincin merah
  // supaya penolakan tidak terbaca sama dengan konfirmasi berhasil, sama seperti ToastHost.kt.
  const isError = shown.msg.startsWith("⚠");

  return (
    <div className="toast-host">
      <div className={`toast${isError ? " error" : ""}${leaving ? " leaving" : ""}`} key={shown.key}>
        <span style={{ flex: 1 }}>{shown.msg}</span>
        <button className="toast-close" aria-label="Tutup" onClick={dismissToast}>
          <CloseIcon size={14} />
        </button>
      </div>
    </div>
  );
}
