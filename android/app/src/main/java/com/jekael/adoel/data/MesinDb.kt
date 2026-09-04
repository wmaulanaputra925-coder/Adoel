package com.jekael.adoel.data

/** Database awal: 174 mesin (nomor 1-174), semuanya kosong (tipe TAPPET, corak "-") — sengaja
 * tidak diisi preset pabrik tertentu di sini karena itu data spesifik pabrik. Cara mengisi data
 * asli: atur satu-satu di Pengaturan, atau paling cepat import lewat QR Sync/file backup JSON.
 * Port 1:1 dari buildDefaultDb (web/src/domain/defaultDb.ts). */
fun buildDefaultDb(): Map<String, MesinData> {
    val db = mutableMapOf<String, MesinData>()
    for (i in 1..174) db["$i"] = MesinData(tipe = MesinTipe.TAPPET, corak = "-")
    return db
}
