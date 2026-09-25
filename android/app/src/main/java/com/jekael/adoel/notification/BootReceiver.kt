package com.jekael.adoel.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jekael.adoel.data.DoffRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = DoffRepository.getInstance(context).load()
                if (state.notifEnabled) {
                    NotificationHelper.rescheduleAll(context, state.estimasi.values, leadMinutes = state.notifLeadMinutes.toLong())
                }
            } catch (e: Exception) {
                // Gagal reschedule setelah reboot berarti SEMUA alarm doff hilang diam-diam
                // sampai app dibuka lagi — kegagalan sepenting itu wajib meninggalkan jejak.
                Log.e("BootReceiver", "Gagal menjadwalkan ulang notifikasi setelah boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
