import { formatYard } from "../domain/format";
import { TIPE_COLOR } from "../domain/mesinVisual";
import type { AktualEntry, MesinData } from "../domain/types";
import { CircleIcon, MesinTipeIcon, TextureIcon } from "./Icons";

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes (Riwayat stacked a big mc number over a corak line; Statistik ran everything
 * inline). Each caller still supplies its own container: Riwayat a list card with edit/hapus
 * buttons after this, Statistik a flat tappable strip inside the shift card.
 *
 * Two zones instead of one straight line of chips, which is what used to clip corak mid-word the
 * moment a shift had a long-ish corak or a keterangan: .der-left wraps its own chips (num/tipe
 * icon/mc number/corak with yard folded in/waktu) onto a second line on its own — the row's
 * height simply grows instead of anything getting squeezed — while keterangan (.der-ket) sits
 * pinned on the right, no longer fighting the same single line for space. Port 1:1 dari
 * DoffEntryRow.kt (aplikasi Android).
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
  // (see commands.ts prosesBarisUmum). The time has its own chip on the left, so strip it back
  // off here and keep only the code — otherwise the row prints the clock twice.
  const ketCode = entry.ket.startsWith(entry.jam)
    ? entry.ket.slice(entry.jam.length).replace(/^\((.*)\)$/, "$1")
    : entry.ket;
  // Yard folded straight into the corak chip text instead of its own separate chip — one less
  // fixed-width box competing for room is what actually freed up enough space for corak to stop
  // truncating (see .der-corak in index.css).
  const corakText = yard != null ? `${corak} (${formatYard(yard)}y)` : corak;

  return (
    <>
      <span className="der-left">
        <span className="der-num">{num}</span>
        <span className="der-tipe" style={{ color: mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)" }}>
          {mesin ? <MesinTipeIcon tipe={mesin.tipe} size={13} /> : <CircleIcon size={13} />}
        </span>
        {/* Just the number — the surrounding chips already make it obvious this is the machine. */}
        <span className="der-mcno">{entry.mcNo}</span>
        <span className="der-corak">
          <TextureIcon size={11} />
          <span className="der-corak-text">{corakText}</span>
        </span>
        {/* No more edit-pencil here — the row itself is the tap target (Statistik even prints
            "Ketuk baris untuk edit" once above the list), so a second per-row hint was
            redundant, not the reason anyone found the affordance. */}
        <span className="der-time">{entry.jam}</span>
      </span>
      {ketCode.length > 0 && (
        <span
          className={`der-ket${ketCode === "MATCHING" ? " ket-matching" : ketCode === "HB" ? " ket-hb" : ""}`}
        >
          {ketCode}
        </span>
      )}
    </>
  );
}
