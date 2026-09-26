import { useState } from "react";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { AddIcon } from "./Icons";

interface BaseShortcutPickerProps {
  value: string;
  onSelect: (selected: string) => void;
  shortcuts: string[];
  onAddShortcut: (item: string) => void;
  itemTypeLabel: string; // e.g. "Corak" | "Keterangan"
  placeholder: string; // e.g. "Cth: 4520" | "Cth: GANTI BEAM"
}

function BaseShortcutPicker({
  value,
  onSelect,
  shortcuts,
  onAddShortcut,
  itemTypeLabel,
  placeholder,
}: BaseShortcutPickerProps) {
  const { showToast } = useUiStore();
  const [isAdding, setIsAdding] = useState(false);
  const [inlineInput, setInlineInput] = useState("");

  const currentTrimmed = value.trim().toUpperCase();
  const canSaveCurrent = currentTrimmed.length > 0 && !shortcuts.includes(currentTrimmed);

  function handleSaveCurrent() {
    if (!currentTrimmed) return;
    onAddShortcut(currentTrimmed);
    showToast(`${itemTypeLabel} "${currentTrimmed}" ditambahkan ke shortcut ✓`);
  }

  function handleAddInline() {
    const trimmed = inlineInput.trim().toUpperCase();
    if (!trimmed) return;
    if (shortcuts.includes(trimmed)) {
      showToast(`${itemTypeLabel} "${trimmed}" sudah ada di shortcut`);
    } else {
      onAddShortcut(trimmed);
      showToast(`${itemTypeLabel} "${trimmed}" ditambahkan ke shortcut ✓`);
    }
    onSelect(trimmed);
    setInlineInput("");
    setIsAdding(false);
  }

  return (
    <div style={{ marginTop: 10, display: "flex", flexWrap: "wrap", alignItems: "center", gap: 7 }}>
      {shortcuts.map((code) => (
        <button
          key={code}
          type="button"
          className={`chip-btn${currentTrimmed === code ? " active" : ""}`}
          style={{ fontSize: 12.5, padding: "5px 10px" }}
          onClick={() => onSelect(code)}
        >
          {code}
        </button>
      ))}

      {canSaveCurrent && (
        <button
          type="button"
          className="btn-link"
          style={{
            fontSize: 12,
            color: "var(--cyan-400)",
            display: "inline-flex",
            alignItems: "center",
            gap: 3,
            padding: "2px 8px",
            background: "rgba(6, 182, 212, 0.12)",
            borderRadius: 4,
            border: "1px solid rgba(6, 182, 212, 0.3)",
            fontWeight: 600,
          }}
          onClick={handleSaveCurrent}
          title={`Simpan ${currentTrimmed} ke shortcut ${itemTypeLabel.toLowerCase()}`}
        >
          <AddIcon size={12} />
          <span>Simpan "{currentTrimmed}" ke Shortcut</span>
        </button>
      )}

      {!isAdding ? (
        <button
          type="button"
          className="btn-link"
          style={{
            fontSize: 12,
            color: "var(--text-secondary)",
            display: "inline-flex",
            alignItems: "center",
            gap: 3,
            padding: "4px 8px",
            background: "var(--bg-elevated)",
            borderRadius: 4,
            border: "1px dashed var(--border-subtle)",
          }}
          onClick={() => setIsAdding(true)}
          title={`Tambah ${itemTypeLabel.toLowerCase()} baru ke shortcut`}
        >
          <AddIcon size={12} />
          <span>Tambah {itemTypeLabel}</span>
        </button>
      ) : (
        <div
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            background: "var(--bg-elevated)",
            padding: "3px 4px",
            borderRadius: 4,
            border: "1px solid var(--cyan-400)",
          }}
        >
          <input
            className="field-input"
            style={{ width: itemTypeLabel === "Corak" ? 90 : 128, padding: "3px 7px", fontSize: 12, height: 25 }}
            placeholder={placeholder}
            value={inlineInput}
            autoFocus
            onChange={(e) => setInlineInput(e.target.value.toUpperCase())}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleAddInline();
              } else if (e.key === "Escape") {
                setIsAdding(false);
              }
            }}
          />
          <button
            type="button"
            className="btn primary"
            style={{ padding: "3px 7px", fontSize: 12, height: 25 }}
            disabled={!inlineInput.trim()}
            onClick={handleAddInline}
          >
            + OK
          </button>
          <button
            type="button"
            className="btn-link"
            style={{ fontSize: 12, color: "var(--text-faint)", padding: "0 3px" }}
            onClick={() => setIsAdding(false)}
          >
            ✕
          </button>
        </div>
      )}
    </div>
  );
}

export function CorakShortcutPicker({
  value,
  onSelect,
}: {
  value: string;
  onSelect: (corak: string) => void;
}) {
  const { state, addCorakShortcut } = useDoffStore();
  const shortcuts = state.corakShortcuts ?? [];

  return (
    <BaseShortcutPicker
      value={value}
      onSelect={onSelect}
      shortcuts={shortcuts}
      onAddShortcut={addCorakShortcut}
      itemTypeLabel="Corak"
      placeholder="Cth: 4520"
    />
  );
}

export function KeteranganShortcutPicker({
  value,
  onSelect,
}: {
  value: string;
  onSelect: (keterangan: string) => void;
}) {
  const { state, addKeteranganShortcut } = useDoffStore();
  const shortcuts = state.keteranganShortcuts ?? [];

  return (
    <BaseShortcutPicker
      value={value}
      onSelect={onSelect}
      shortcuts={shortcuts}
      onAddShortcut={addKeteranganShortcut}
      itemTypeLabel="Keterangan"
      placeholder="Cth: GANTI BEAM"
    />
  );
}
