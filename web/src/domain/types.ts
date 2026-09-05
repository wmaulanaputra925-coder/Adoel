export type MesinTipe = "TAPPET" | "CAM" | "D405" | "D408";

export interface MesinData {
  tipe: MesinTipe;
  corak: string;
  targetYard: number | null;
  speed: number | null;
  koreksi: number | null;
  isActive?: boolean;
}

export function defaultMesinData(): MesinData {
  return { tipe: "TAPPET", corak: "-", targetYard: null, speed: null, koreksi: null };
}

export interface Estimasi {
  mcNo: string;
  estAbsMin: number;
  startAbsMin: number;
  corakOverride: string | null;
  yardOverride: number | null;
  pausedAtAbsMin: number | null;
}

export interface AktualEntry {
  id: number;
  mcNo: string;
  jam: string;
  ket: string;
  corakOverride: string | null;
  customYard: number | null;
  tsEpochMin: number | null;
}

export interface ShiftRecord {
  id: number;
  startedAtEpochMin: number;
  endedAtEpochMin: number;
  aktual: AktualEntry[];
  estimasiRemaining: Record<string, Estimasi>;
  /** Operator & grup yang menutup shift ini, dicap saat diarsipkan — bukan dibaca ulang dari
   * pengaturan saat laporannya dibagikan, supaya arsip lama tidak berganti nama pemilik ketika
   * operator/grup di pengaturan berubah. Kosong untuk arsip yang dibuat sebelum ada pendataan
   * ini; teks bagikannya sekadar tidak mencantumkan baris operator. */
  operatorNama?: string;
  operatorGrup?: string;
}

export type ThemeMode = "SYSTEM" | "LIGHT" | "DARK";

export const DEFAULT_KETERANGAN_SHORTCUTS: string[] = [];
export const DEFAULT_CORAK_SHORTCUTS: string[] = [];

/** Corak yang berlaku aturan "potongan awal 70 yard" — begitu beam lusi baru naik, kain di
 * awal jalan sering masih banyak cacat (LTK, lusi putus) sampai mesin stabil, jadi sampel
 * Matching (1 yard) untuk corak-corak ini baru diambil setelah 70y, bukan langsung dari 0. */
export const DEFAULT_CORAK_POTONGAN_AWAL: string[] = ["80125", "21242", "66335"];

/** Panjang potongan yang benar-benar dicatat untuk Doffing Matching pada corak potongan awal:
 * kainnya dipotong setelah 70 yard pertama, bukan sepanjang target standar mesin. Tanpa angka ini
 * Riwayat menampilkan target standar (mis. 303y) untuk potongan yang nyatanya 70y. Sama persis
 * dengan POTONGAN_AWAL_YARD di Models.kt (Android). */
export const POTONGAN_AWAL_YARD = 70;

export interface DoffState {
  db: Record<string, MesinData>;
  estimasi: Record<string, Estimasi>;
  aktual: AktualEntry[];
  nextId: number;
  themeMode: ThemeMode;
  history: ShiftRecord[];
  nextShiftId: number;
  onboardingSeen: boolean;
  /** Identitas operator pemakai aplikasi ini — ditanyakan sekali saat pertama kali dibuka dan
   * bisa diubah kapan saja di Pengaturan. Ikut tercetak di teks bagikan supaya rekan yang
   * membaca laporan di WhatsApp tahu laporan itu dari siapa tanpa harus bertanya. */
  operatorNama?: string;
  operatorGrup?: string;
  /** Sudah pernah ditanyai identitasnya (termasuk kalau pertanyaannya dilewati). Terpisah dari
   * operatorNama supaya "dilewati" tidak berarti "tanya lagi tiap buka", dan terpisah dari
   * onboardingSeen supaya pemakai lama — yang panduannya sudah lewat — tetap ditanya sekali,
   * bukan diam-diam mengirim laporan tanpa nama. */
  operatorAsked?: boolean;
  keteranganShortcuts?: string[];
  corakShortcuts?: string[];
  corakPotonganAwal?: string[];
}

export type ProsesResult =
  | {
      ok: true;
      msg: string;
      mcNo: string;
      estAbs?: number;
      prevEst?: Estimasi | null;
      undo?: () => void;
      /** Entri aktual persis yang baru dibuat prosesBarisUmum (kalau ada) — dipakai redo
       * supaya mengembalikan baris yang sama (id sama), bukan menjalankan ulang command
       * dan membuat baris baru (yang akan menyisakan duplikat di Riwayat tiap siklus
       * undo/redo, karena nextId selalu maju). */
      entry?: AktualEntry;
    }
  | { ok: false; msg: string };
