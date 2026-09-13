import { formatYard } from "../domain/format";
import { TIPE_COLOR } from "../domain/mesinVisual";
import type { AktualEntry, MesinData } from "../domain/types";
import { CircleIcon, EditIcon, MesinTipeIcon, TextureIcon } from "./Icons";

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes (Riwayat stacked a big mc number over a corak line; Statistik ran everything
 * inline). Each caller still supplies its own container: Riwayat a list card with edit/hapus
 * buttons after this, Statistik a flat tappable strip inside the shift card.
 *
 * Order: [No Urut] -> [No Mesin] -> [Corak] -> [Panjang] -> [Jam] -> [Keterangan]
 * Corak is flexible so it absorbs any squeeze with ellipsis. Yard matches Corak pill styling.
 * Jam comes before Keterangan, and Keterangan is only rendered when present.
 * Port 1:1 dari DoffEntryRow.kt (aplikasi Android).
 */
export function DoffEntryRowContent({
  num,
  entry,
  mesin,
  showEditHint = false,
}: {
  num: number;
  entry: AktualEntry;
  mesin: MesinData | undefined;
  showEditHint?: boolean;
}) {
  const corak = entry.corakOverride ?? mesin?.corak ?? "—";
  const yard = entry.customYard ?? mesin?.targetYard ?? null;
  // entry.ket is "jam(extra)" when the doff carried a keterangan, or bare "jam" when it didn't
  // (see commands.ts prosesBarisUmum). The time has its own chip, so strip it back off
  // here and keep only the code — otherwise the row prints the clock twice.
  const ketCode = entry.ket.startsWith(entry.jam)
    ? entry.ket.slice(entry.jam.length).replace(/^\((.*)\)$/, "$1").trim()
    : entry.ket.replace(/^\((.*)\)$/, "$1").trim();

  return (
    <>
      <span className="der-num" title={`Urutan #${num}`}>
        {num}
      </span>
      <span className="der-tipe" style={{ color: mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)" }}>
        {mesin ? <MesinTipeIcon tipe={mesin.tipe} size={13} /> : <CircleIcon size={13} />}
      </span>
      <span className="der-mcno">{entry.mcNo}</span>
      <span className="der-corak" title={corak}>
        <TextureIcon size={11} />
        <span className="der-corak-text">{corak}</span>
      </span>
      {yard != null && (
        <span className="der-yard" title={`Target/hasil yard: ${formatYard(yard)}y`}>
          {formatYard(yard)}y
        </span>
      )}
      <span className="der-time" title={`Jam: ${entry.jam}`}>
        <span>{entry.jam}</span>
        {showEditHint && <EditIcon size={10} />}
      </span>
      {ketCode.length > 0 && (
        <span className="der-ket" title={`Keterangan: ${ketCode}`}>
          {ketCode}
        </span>
      )}
    </>
  );
}
