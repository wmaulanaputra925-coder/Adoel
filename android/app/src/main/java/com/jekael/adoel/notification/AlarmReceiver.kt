package com.jekael.adoel.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jekael.adoel.data.REMINDER_LEAD_MIN

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mcNo = intent.getStringExtra("mcNo") ?: return
        val isReminder = intent.getBooleanExtra("isReminder", false)
        val leadMinutes = intent.getLongExtra("leadMinutes", REMINDER_LEAD_MIN)
        try {
            NotificationHelper.showNotification(context, mcNo, isReminder, leadMinutes)
        } catch (e: Exception) {
            // Notifikasi gagal tampil tidak boleh merobohkan proses — cukup dicatat.
            Log.e("AlarmReceiver", "Gagal menampilkan notifikasi doff Mc $mcNo", e)
        }
    }
}
