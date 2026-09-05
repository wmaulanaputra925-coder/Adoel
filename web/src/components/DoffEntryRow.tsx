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
 * Every field gets its own chip so they read apart at a glance rather than running together as one
 * sentence: cyan mc number, corak behind the kain icon, yard, keterangan in amber, and the time
 * last. Everything stays on one line — corak is the only part that flexes, so it takes the squeeze
 * (and an ellipsis) before anything else does, and keterangan is capped rather than allowed to
 * wrap the row onto a second line. Port 1:1 dari DoffEntryRow.kt (aplikasi Android).
 */
export function DoffEntryRowContent({
  num,
  entry,
  mesin,
  showEditHint,
}: {
  num: number;
  entry: AktualEntry;
  mesin: MesinData | undefined;
  showEditHint: boolean;
}) {
  const corak = entry.corakOverride ?? mesin?.corak ?? "—";
  const yard = entry.customYard ?? mesin?.targetYard ?? null;
  // entry.ket is "jam(extra)" when the doff carried a keterangan, or bare "jam" when it didn't
  // (see commands.ts prosesBarisUmum). The time has its own chip at the end, so strip it back off
  // here and keep only the code — otherwise the row prints the clock twice.
  const ketCode = entry.ket.startsWith(entry.jam)
    ? entry.ket.slice(entry.jam.length).replace(/^\((.*)\)$/, "$1")
    : entry.ket;

  return (
    <>
      <span className="der-num">{num}</span>
      <span className="der-tipe" style={{ color: mesin ? TIPE_COLOR[mesin.tipe] : "var(--text-faint)" }}>
        {mesin ? <MesinTipeIcon tipe={mesin.tipe} size={13} /> : <CircleIcon size={13} />}
      </span>
      {/* Just the number — the surrounding chips already make it obvious this is the machine. */}
      <span className="der-mcno">{entry.mcNo}</span>
      <span className="der-corak">
        <TextureIcon size={11} />
        <span className="der-corak-text">{corak}</span>
      </span>
      {yard != null && <span className="der-yard">{formatYard(yard)}y</span>}
      {ketCode.length > 0 && <span className="der-ket">{ketCode}</span>}
      <span className="der-time">
        <span>{entry.jam}</span>
        {showEditHint && <EditIcon size={11} />}
      </span>
    </>
  );
}
