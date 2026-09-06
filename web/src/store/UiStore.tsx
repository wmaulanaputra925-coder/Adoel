import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

interface ToastState {
  key: number;
  msg: string;
}

interface ConfirmState {
  msg: string;
  onConfirm: () => void;
  // Default ke "Ya, Lanjutkan"/"Batal" di ConfirmDialog.tsx kalau tidak diisi — dipakai satu-
  // satunya oleh pengingat Tali Hijau setelah doff HB, yang butuh label aksi yang lebih
  // spesifik daripada konfirmasi generik.
  confirmLabel?: string;
  cancelLabel?: string;
  // Tombol konfirmasi defaultnya merah (var(--red-500)) — cocok untuk mayoritas pemakaian
  // showConfirm yang memang destruktif (Hapus, Reset). Override ini dipakai satu-satunya oleh
  // pengingat Tali Hijau di atas, yang aksinya menandai Matching — positif, bukan destruktif,
  // jadi merah di sana justru menyesatkan.
  confirmColor?: string;
}

interface UiStore {
  toast: ToastState | null;
  // Tanpa aksi "URUNGKAN" lagi (Master Blueprint v9.2 §9) — undo/redo sekarang tingkat
  // konsol lewat DoffStore.pushUndo, bukan closure per-toast.
  showToast: (msg: string) => void;
  dismissToast: () => void;
  confirm: ConfirmState | null;
  showConfirm: (
    msg: string,
    onConfirm: () => void,
    options?: { confirmLabel?: string; cancelLabel?: string; confirmColor?: string },
  ) => void;
  dismissConfirm: () => void;
}

const Ctx = createContext<UiStore | null>(null);

export function UiStoreProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const [confirm, setConfirm] = useState<ConfirmState | null>(null);
  const keyRef = useRef(0);

  const showToast = useCallback((msg: string) => {
    keyRef.current += 1;
    setToast({ key: keyRef.current, msg });
  }, []);

  const dismissToast = useCallback(() => setToast(null), []);

  const showConfirm = useCallback(
    (msg: string, onConfirm: () => void, options?: { confirmLabel?: string; cancelLabel?: string; confirmColor?: string }) => {
      setConfirm({
        msg,
        onConfirm,
        confirmLabel: options?.confirmLabel,
        cancelLabel: options?.cancelLabel,
        confirmColor: options?.confirmColor,
      });
    },
    [],
  );

  const dismissConfirm = useCallback(() => setConfirm(null), []);

  return (
    <Ctx.Provider value={{ toast, showToast, dismissToast, confirm, showConfirm, dismissConfirm }}>
      {children}
    </Ctx.Provider>
  );
}

export function useUiStore(): UiStore {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useUiStore must be used within UiStoreProvider");
  return ctx;
}
