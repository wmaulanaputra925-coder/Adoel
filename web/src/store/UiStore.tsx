import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

interface ToastState {
  key: number;
  msg: string;
}

interface ConfirmState {
  msg: string;
  onConfirm: () => void;
}

interface UiStore {
  toast: ToastState | null;
  // Tanpa aksi "URUNGKAN" lagi (Master Blueprint v9.2 §9) — undo/redo sekarang tingkat
  // konsol lewat DoffStore.pushUndo, bukan closure per-toast.
  showToast: (msg: string) => void;
  dismissToast: () => void;
  confirm: ConfirmState | null;
  showConfirm: (msg: string, onConfirm: () => void) => void;
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

  const showConfirm = useCallback((msg: string, onConfirm: () => void) => {
    setConfirm({ msg, onConfirm });
  }, []);

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
