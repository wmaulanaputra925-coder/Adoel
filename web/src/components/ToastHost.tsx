import { useEffect } from "react";
import { useUiStore } from "../store/UiStore";
import { CloseIcon } from "./Icons";

/** Toast tanpa aksi "URUNGKAN" lagi — undo/redo sekarang tingkat konsol lewat tombol Undo/Redo
 * di ConsoleBar, bukan closure per-toast. Tombol X di sini murni menutup lebih awal. Port 1:1
 * dari redesign ToastHost.kt (aplikasi Android, Master Blueprint v9.2 §9). */
export function ToastHost() {
  const { toast, dismissToast } = useUiStore();

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(dismissToast, 3500);
    return () => clearTimeout(t);
  }, [toast, dismissToast]);

  if (!toast) return null;

  return (
    <div className="toast-host">
      <div className="toast" key={toast.key}>
        <span style={{ flex: 1 }}>{toast.msg}</span>
        <button className="toast-close" aria-label="Tutup" onClick={dismissToast}>
          <CloseIcon size={14} />
        </button>
      </div>
    </div>
  );
}
