import { useEffect, useState } from "react";
import { CheckCircleIcon } from "./Icons";

/** Overlay perayaan "Selesai Shift" — animasi checkmark membesar & backdrop gelap halus.
 * Port 1:1 dari ShiftFinishedOverlay.kt (aplikasi Android). */
export function ShiftFinishedOverlay({ visible }: { visible: boolean }) {
  // Ditahan 250ms setelah `visible` dimatikan supaya backdrop-nya sempat memudar, persis seperti
  // ShiftFinishedOverlay.kt yang menganimasikan backdropAlpha kembali ke 0 (tween 250ms).
  // Sebelumnya overlay langsung `return null`, jadi perayaannya terpotong mendadak.
  const [mounted, setMounted] = useState(visible);

  useEffect(() => {
    if (visible) {
      setMounted(true);
      return;
    }
    if (!mounted) return;
    const t = setTimeout(() => setMounted(false), 250);
    return () => clearTimeout(t);
  }, [visible, mounted]);

  if (!mounted) return null;

  return (
    <div className={`shift-finished-overlay${visible ? "" : " leaving"}`}>
      <div className="shift-finished-content">
        <div className="shift-finished-icon">
          <CheckCircleIcon size={96} />
        </div>
        <div className="shift-finished-text">Shift Selesai</div>
      </div>
    </div>
  );
}
