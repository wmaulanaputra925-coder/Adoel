import { useState, type ReactNode } from "react";
import { formatYard } from "../domain/format";
import { DEFAULT_KETERANGAN_SHORTCUTS, type Estimasi, type MesinData, type MesinTipe } from "../domain/types";
import { isPotonganAwalCorak, potonganAwalReminderMessage } from "../domain/matchingRules";
import { useDoffStore } from "../store/DoffStore";
import { useUiStore } from "../store/UiStore";
import { MachineSetupForm } from "./MachineSetupForm";
import { KeteranganShortcutPicker } from "./ShortcutPicker";
import {
  ArrowBackIcon,
  CheckIcon,
  CloseIcon,
  RulerIcon,
  ScissorsIcon,
  SparklesIcon,
  TagIcon,
} from "./Icons";

type Step = "SETUP" | "CHOOSE" | "NORMAL" | "KETERANGAN";

/** Terpandu (guided) DOFFING — langkah pertama menawarkan pilihan besar ("Doffing normal" /
 * "Ada keterangan") sebagai pengganti teks bebas. Setiap jalur berakhir membangun string
 * command yang identik dengan yang dulu dikirim konsol Teks. Port 1:1 dari
 * GuidedDoffingSheet.kt (aplikasi Android). Update bacaan counter D408 sepenuhnya lewat
 * tombol Estimasi (yang sudah otomatis minta field "Bacaan jam counter" untuk tipe D408) —
 * bukan lewat menu Doffing ini, supaya tidak ada dua jalur berbeda menuju hasil yang sama. */
export function GuidedDoffingSheet({
  mcNo,
  mesin,
  estimasi,
  onDismiss,
  onSubmitDoffing,
  onQuickUpdate,
}: {
  mcNo: string;
  mesin: MesinData | null;
  estimasi: Estimasi | null;
  onDismiss: () => void;
  onSubmitDoffing: (value: string) => void;
  onQuickUpdate: (corak: string, targetYard: number | null, tipe: MesinTipe, koreksi: number | null, speed: number | null) => void;
}) {
  const { state } = useDoffStore();
  const { showConfirm } = useUiStore();
  const [activeMesin, setActiveMesin] = useState<MesinData | null>(mesin);
  const needsSetup = !activeMesin || activeMesin.corak.trim() === "" || activeMesin.corak.trim() === "-";
  const [step, setStep] = useState<Step>(needsSetup ? "SETUP" : "CHOOSE");
  const standardYard = estimasi?.yardOverride ?? activeMesin?.targetYard ?? null;

  function handlePickMatching() {
    const corak = activeMesin?.corak;
    if (isPotonganAwalCorak(state, corak)) {
      showConfirm(potonganAwalReminderMessage(corak!), () => onSubmitDoffing(`${mcNo} MATCHING`));
      return;
    }
    onSubmitDoffing(`${mcNo} MATCHING`);
  }

  return (
    <div className="dialog-backdrop" onClick={onDismiss}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 420, maxHeight: "90vh", overflowY: "auto" }}>
        <div style={{ fontWeight: 800, fontSize: 16, marginBottom: 16, display: "flex", alignItems: "center", gap: 8 }}>
          <ScissorsIcon size={18} />
          <span>Catat Doffing — Mc {mcNo}</span>
        </div>

        {step === "SETUP" && (
          <MachineSetupForm
            initial={activeMesin ?? { tipe: "TAPPET", corak: "", targetYard: null, speed: null, koreksi: null }}
            onSave={(corak, targetYard, tipe, koreksi, speed) => {
              const updated: MesinData = { tipe, corak, targetYard, speed, koreksi };
              setActiveMesin(updated);
              onQuickUpdate(corak, targetYard, tipe, koreksi, speed);
              setStep("CHOOSE");
            }}
            onCancel={onDismiss}
          />
        )}
        {step === "CHOOSE" && (
          <ChooseStep
            onPickNormal={() => setStep("NORMAL")}
            onPickMatching={handlePickMatching}
            onPickKeterangan={() => setStep("KETERANGAN")}
            onCancel={onDismiss}
          />
        )}
        {step === "NORMAL" && (
          <NormalYardStep
            standardYard={standardYard}
            onBack={() => setStep("CHOOSE")}
            onConfirm={(yard) => onSubmitDoffing(`${mcNo} ${yard}`)}
          />
        )}
        {step === "KETERANGAN" && (
          <KeteranganStep
            standardYard={standardYard}
            onBack={() => setStep("CHOOSE")}
            onConfirm={(cmd) => onSubmitDoffing(`${mcNo} ${cmd}`)}
          />
        )}
      </div>
    </div>
  );
}

function ChooseStep({
  onPickNormal,
  onPickMatching,
  onPickKeterangan,
  onCancel,
}: {
  onPickNormal: () => void;
  onPickMatching: () => void;
  onPickKeterangan: () => void;
  onCancel: () => void;
}) {
  const { state } = useDoffStore();
  const shortcuts = state.keteranganShortcuts ?? DEFAULT_KETERANGAN_SHORTCUTS;
  const shortcutsPreview = shortcuts.slice(0, 5).join(", ") + (shortcuts.length > 5 ? ", ..." : "");

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      <BigChoiceButton
        icon={<ScissorsIcon size={18} />}
        label="Doffing normal"
        subtitle="Selesai sesuai target yard"
        onClick={onPickNormal}
      />
      <BigChoiceButton
        icon={<SparklesIcon size={18} />}
        label="Doffing matching"
        subtitle="Potong sampel / cek kualitas beam baru"
        onClick={onPickMatching}
        accent="var(--orange-400)"
      />
      <BigChoiceButton
        icon={<TagIcon size={18} />}
        label="Ada keterangan"
        subtitle={shortcutsPreview || "HB, P.LP, P.SN, P.OH, P.EL, P.Sel..."}
        onClick={onPickKeterangan}
      />
      <button className="btn" style={{ marginTop: 6, display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 5 }} onClick={onCancel}>
        <CloseIcon size={14} />
        <span>Batal</span>
      </button>
    </div>
  );
}

function BigChoiceButton({
  icon,
  label,
  subtitle,
  onClick,
  accent = "var(--cyan-600)",
}: {
  icon?: ReactNode;
  label: string;
  subtitle: string;
  onClick: () => void;
  accent?: string;
}) {
  return (
    <button className="big-choice-btn" onClick={onClick}>
      <div className="label" style={{ color: accent, display: "flex", alignItems: "center", gap: 6 }}>
        {icon}
        <span>{label}</span>
      </div>
      <div className="subtitle">{subtitle}</div>
    </button>
  );
}

function NormalYardStep({
  standardYard,
  onBack,
  onConfirm,
}: {
  standardYard: number | null;
  onBack: () => void;
  onConfirm: (yard: string) => void;
}) {
  const [yardInput, setYardInput] = useState(standardYard != null ? formatYard(standardYard) : "");

  return (
    <>
      <YardDeltaField standardYard={standardYard} yardInput={yardInput} onYardInputChange={setYardInput} />
      <div className="actions" style={{ marginTop: 20 }}>
        <button className="cancel" onClick={onBack} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
          <ArrowBackIcon size={14} />
          <span>Kembali</span>
        </button>
        <button
          className="confirm"
          style={{ background: "var(--cyan-600)", display: "inline-flex", alignItems: "center", gap: 5 }}
          disabled={yardInput.trim() === ""}
          onClick={() => yardInput.trim() !== "" && onConfirm(yardInput.trim())}
        >
          <CheckIcon size={14} />
          <span>Simpan</span>
        </button>
      </div>
    </>
  );
}

function YardDeltaField({
  standardYard,
  yardInput,
  onYardInputChange,
}: {
  standardYard: number | null;
  yardInput: string;
  onYardInputChange: (v: string) => void;
}) {
  function step(delta: number) {
    const current = parseFloat(yardInput.trim().replace(",", ".")) || standardYard || 0;
    onYardInputChange(formatYard(current + delta));
  }

  return (
    <>
      <div className="field-label" style={{ display: "flex", alignItems: "center", gap: 4 }}>
        <RulerIcon size={13} />
        <span>Yard aktual</span>
      </div>
      <input
        className="field-input"
        placeholder={standardYard != null ? `Standar: ${formatYard(standardYard)}y` : undefined}
        inputMode="decimal"
        value={yardInput}
        onChange={(e) => onYardInputChange(e.target.value)}
      />
      <div className="yard-step-row">
        {[-5, -1, 1, 5].map((delta) => (
          <button key={delta} className="yard-step-btn" onClick={() => step(delta)}>
            {delta > 0 ? `+${delta}` : delta}
          </button>
        ))}
      </div>
    </>
  );
}

function KeteranganStep({
  standardYard,
  onBack,
  onConfirm,
}: {
  standardYard: number | null;
  onBack: () => void;
  onConfirm: (cmd: string) => void;
}) {
  const [ket, setKet] = useState("");
  const [yardInput, setYardInput] = useState("");

  function toggleDelta() {
    setYardInput((y) => (y.startsWith("+") ? y.slice(1) : "+" + y.replace(/^-/, "")));
  }

  return (
    <>
      <div className="field-label" style={{ display: "flex", alignItems: "center", gap: 4 }}>
        <TagIcon size={13} />
        <span>Keterangan Doffing</span>
      </div>
      <input
        className="field-input"
        placeholder="Pilih dari shortcut di bawah atau ketik keterangan bebas"
        value={ket}
        onChange={(e) => setKet(e.target.value.toUpperCase())}
      />
      <KeteranganShortcutPicker value={ket} onSelect={setKet} />

      <div style={{ height: 14 }} />
      <div className="field-label" style={{ display: "flex", alignItems: "center", gap: 4 }}>
        <RulerIcon size={13} />
        <span>Yard aktual (opsional)</span>
      </div>
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <input
          className="field-input"
          style={{ flex: 1 }}
          placeholder={standardYard != null ? `Standar: ${formatYard(standardYard)}y` : "cth: 70"}
          inputMode="decimal"
          value={yardInput}
          onChange={(e) => setYardInput(e.target.value)}
        />
        <button className={`delta-toggle-btn${yardInput.startsWith("+") ? " active" : ""}`} onClick={toggleDelta}>
          +
        </button>
      </div>

      <div className="actions" style={{ marginTop: 20 }}>
        <button className="cancel" onClick={onBack} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
          <ArrowBackIcon size={14} />
          <span>Kembali</span>
        </button>
        <button
          className="confirm"
          style={{ background: "var(--cyan-600)", display: "inline-flex", alignItems: "center", gap: 5 }}
          disabled={ket.trim() === ""}
          onClick={() => {
            const cmd = [ket.trim(), yardInput.trim()].filter((s) => s.length > 0).join(" ");
            if (cmd.trim() !== "") onConfirm(cmd);
          }}
        >
          <CheckIcon size={14} />
          <span>Simpan</span>
        </button>
      </div>
    </>
  );
}

