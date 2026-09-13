import { useUiStore } from "../store/UiStore";
import { CheckIcon, CloseIcon, WarningIcon } from "./Icons";

export function ConfirmDialog() {
  const { confirm, dismissConfirm } = useUiStore();
  if (!confirm) return null;

  return (
    <div className="dialog-backdrop" onClick={dismissConfirm}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", alignItems: "flex-start", gap: 10, marginBottom: 12 }}>
          <div style={{ color: "var(--amber-400)", flexShrink: 0, marginTop: 2 }}>
            <WarningIcon size={20} filled={true} />
          </div>
          <div className="msg" style={{ margin: 0 }}>
            {confirm.msg}
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
              confirm.onConfirm();
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

