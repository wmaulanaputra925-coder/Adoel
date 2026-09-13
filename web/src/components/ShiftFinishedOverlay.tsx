import { CheckCircleIcon } from "./Icons";

/** Overlay perayaan "Selesai Shift" — animasi checkmark membesar & backdrop gelap halus.
 * Port 1:1 dari ShiftFinishedOverlay.kt (aplikasi Android). */
export function ShiftFinishedOverlay({ visible }: { visible: boolean }) {
  if (!visible) return null;

  return (
    <div className="shift-finished-overlay">
      <div className="shift-finished-content">
        <div className="shift-finished-icon">
          <CheckCircleIcon size={96} />
        </div>
        <div className="shift-finished-text">Shift Selesai</div>
      </div>
    </div>
  );
}
