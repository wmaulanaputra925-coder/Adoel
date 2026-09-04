import { DEFAULT_CORAK_POTONGAN_AWAL, type DoffState } from "./types";

/** Corak dengan aturan "potongan awal 70 yard": begitu beam lusi baru naik, kain di awal jalan
 * sering masih banyak cacat (LTK, lusi putus) sampai mesin stabil, jadi sampel Doffing Matching
 * (1 yard) untuk corak-corak ini baru boleh diambil setelah 70y — bukan langsung dari 0 seperti
 * yang belakangan terjadi di inspecting grey. Sama persis dengan isPotonganAwalCorak di
 * Models.kt (aplikasi Android) — jaga agar daftar & pesannya tetap identik di kedua platform. */
export function isPotonganAwalCorak(state: DoffState, corak: string | undefined | null): boolean {
  if (!corak) return false;
  const trimmed = corak.trim().toUpperCase();
  if (!trimmed) return false;
  const list = state.corakPotonganAwal ?? DEFAULT_CORAK_POTONGAN_AWAL;
  return list.some((c) => c.trim().toUpperCase() === trimmed);
}

export function potonganAwalReminderMessage(corak: string): string {
  return `Corak ${corak} termasuk daftar potongan awal 70 yard. Pastikan beam sudah jalan minimal 70y sebelum ambil sampel Matching (1 yard), supaya sampel tidak kena LTK/lusi putus di awal jalan. Lanjutkan catat Doffing Matching sekarang?`;
}
