import { useEffect, useState } from "react";
import { useUiStore } from "../store/UiStore";
import { CheckIcon, CloseIcon, WarningIcon } from "./Icons";

/** Port dari ConfirmDialog.kt: muncul dengan fade + scale, dan yang penting **menutupnya juga
 * beranimasi**. Dialog ditahan 160ms setelah dibuang (persis exit scaleOut/fadeOut di sisi
 * Android) — tanpa ini dialog hilang seketika padahal munculnya beranimasi. */
export function ConfirmDialog() {
  const { confirm, dismissConfirm } = useUiStore();
  const [shown, setShown] = useState(confirm);
  const leaving = confirm === null;

  useEffect(() => {
    if (confirm) setShown(confirm);
  }, [confirm]);

  useEffect(() => {
    if (confirm || !shown) return;
    const t = setTimeout(() => setShown(null), 160);
    return () => clearTimeout(t);
  }, [confirm, shown]);

  if (!shown) return null;

  return (
    <div className={`dialog-backdrop${leaving ? " leaving" : ""}`} onClick={dismissConfirm}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", alignItems: "flex-start", gap: 10, marginBottom: 12 }}>
          <div style={{ color: "var(--amber-400)", flexShrink: 0, marginTop: 2 }}>
            <WarningIcon size={20} filled={true} />
          </div>
          <div className="msg" style={{ margin: 0 }}>
            {shown.msg}
          </div>
        </div>
        <div className="actions">
          <button className="cancel" onClick={dismissConfirm} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
            <CloseIcon size={14} />
            <span>Batal</span>
          </button>
          <button
            className="confirm"
            onClick={() => {
              shown.onConfirm();
              dismissConfirm();
            }}
            style={{ display: "inline-flex", alignItems: "center", gap: 5 }}
          >
            <CheckIcon size={14} />
            <span>Ya, Lanjutkan</span>
          </button>
        </div>
      </div>
    </div>
  );
}

