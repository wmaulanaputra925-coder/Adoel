import { formatYard } from "../domain/format";
import { TIPE_COLOR } from "../domain/mesinVisual";
import type { AktualEntry, MesinData } from "../domain/types";
import { CircleIcon, MesinTipeIcon, ScheduleIcon, TextureIcon } from "./Icons";

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes (Riwayat stacked a big mc number over a corak line; Statistik ran everything
 * inline). Each caller still supplies its own container: Riwayat a list card with edit/hapus
 * buttons after this, Statistik a flat tappable strip inside the shift card.
 *
 * Fixed two-row layout instead of a wrapping flex line — the wrap point used to depend on content
 * length (a long keterangan or corak could push time onto its own line unpredictably, so card
 * height and vertical alignment varied row to row while scrolling). Now every row is exactly two
 * lines, always: identity (No/tipe/Mc/corak) on .der-row1, result (yard/jam/keterangan) on
 * .der-row2. Corak and yard are also no longer folded into one chip — corak alone can already run
 * long, and appending "(303y)" to it just made the single chip wider still; each gets its own chip
 * so neither one's length affects the other's legibility. Port 1:1 dari DoffEntryRow.kt (Android).
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
    <>
      {/* Identitas (No/tipe/Mc) rapat di kiri, corak didorong ke ujung kanan lewat
          justify-content: space-between pada .der-row1 — bukan cuma menumpuk semua di kiri lalu
          membiarkan sisa lebar kartu kosong begitu saja. */}
      <span className="der-row1">
        <span className="der-row1-left">
          <span className="der-num">{num}</span>
          <span className="der-tipe" style={{ color: mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)" }}>
            {mesin ? <MesinTipeIcon tipe={mesin.tipe} size={13} /> : <CircleIcon size={13} />}
          </span>
          {/* Just the number — the surrounding chips already make it obvious this is the machine. */}
          <span className="der-mcno">{entry.mcNo}</span>
        </span>
        <span className="der-corak">
          <TextureIcon size={11} />
          <span className="der-corak-text">{corak}</span>
        </span>
      </span>
      {/* Yard + jam rapat di kiri, keterangan (kalau ada) didorong ke ujung kanan — simetris
          dengan baris 1. Tanpa keterangan, space-between dengan satu anak saja otomatis rapat
          kiri (tidak ada elemen kedua untuk didorong ke kanan). */}
      <span className="der-row2">
        <span className="der-row2-left">
          {yard != null && <span className="der-yard">{formatYard(yard)}y</span>}
          {/* No more edit-pencil here — the row itself is the tap target (Statistik even prints
              "Ketuk baris untuk edit" once above the list), so a second per-row hint was
              redundant, not the reason anyone found the affordance. */}
          <span className="der-time">
            <ScheduleIcon size={11} />
            {entry.jam}
          </span>
        </span>
        {ketCode.length > 0 && (
          <span
            className={`der-ket${ketCode === "MATCHING" ? " ket-matching" : ketCode === "HB" ? " ket-hb" : ""}`}
          >
            {ketCode}
          </span>
        )}
      </span>
    </>
  );
}
