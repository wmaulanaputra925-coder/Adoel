import { formatYard } from "../domain/format";
import type { AktualEntry, MesinData } from "../domain/types";
import { RulerIcon, ScheduleIcon, TextureIcon } from "./Icons";

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes. Each caller still supplies its own container: Riwayat a list card with
 * edit/hapus buttons after this, Statistik a flat tappable strip inside the shift card.
 *
 * Two lines: top is No, Mc (the prominent badge — the thing you're actually scanning the column
 * for), Corak; bottom is Panjang (yard), Jam, Keterangan. Corak and keterangan are the only
 * free-typed (variable-length) fields, so they're the only two that ever ellipsize; everything
 * else is short, fixed-format text that always fits. Port 1:1 dari DoffEntryRow.kt (Android).
 */
export function DoffEntryRowContent({
  num,
  entry,
  mesin,
}: {
  num: number;
  entry: AktualEntry;
  mesin: MesinData | undefined;
}) {
  const corak = entry.corakOverride ?? mesin?.corak ?? "—";
  const yard = entry.customYard ?? mesin?.targetYard ?? null;
  // entry.ket is "jam(extra)" when the doff carried a keterangan, or bare "jam" when it didn't
  // (see commands.ts prosesBarisUmum). The time has its own chip below, so strip it back off here
  // and keep only the code — otherwise the row prints the clock twice.
  const ketCode = entry.ket.startsWith(entry.jam)
    ? entry.ket.slice(entry.jam.length).replace(/^\((.*)\)$/, "$1")
    : entry.ket;

  return (
    <span className="der-row">
      <span className="der-top">
        <span className="der-num">{num}</span>
        <span className="der-mc-badge">
          <span className="der-mc-label">MC</span>
          <span className="der-mc-number">{entry.mcNo}</span>
        </span>
        <span className="der-corak">
          <TextureIcon size={12} />
          <span className="der-corak-text">{corak}</span>
        </span>
      </span>
      <span className="der-bottom">
        {yard != null && (
          <span className="der-yard">
            <RulerIcon size={11} />
            {formatYard(yard)}y
          </span>
        )}
        {/* No more edit-pencil here — the row itself is the tap target (Statistik even prints
            "Ketuk baris untuk edit" once above the list), so a second per-row hint was redundant,
            not the reason anyone found the affordance. */}
        <span className="der-time">
          <ScheduleIcon size={11} />
          {entry.jam}
        </span>
        {ketCode.length > 0 && (
          <span
            className={`der-ket${ketCode === "MATCHING" ? " ket-matching" : ketCode === "HB" ? " ket-hb" : ""}`}
          >
            {ketCode}
          </span>
        )}
      </span>
    </span>
  );
}
