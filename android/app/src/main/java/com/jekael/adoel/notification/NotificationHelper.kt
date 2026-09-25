package com.jekael.adoel.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jekael.adoel.MainActivity
import com.jekael.adoel.R
import com.jekael.adoel.data.Estimasi
import com.jekael.adoel.data.REMINDER_LEAD_MIN
import com.jekael.adoel.data.nowAbsMin

// Brand accent (Cyan600 #0891B2) — tints the small icon & app name in the notification shade.
private const val BRAND_COLOR = 0xFF0891B2.toInt()

object NotificationHelper {
    const val CHANNEL_ID = "doff_alerts"

    private const val REMINDER_ID_OFFSET = 100_000

    // Amber500 (reminder/"bersiap") and Emerald500 ("siap doff") — same urgency-color language
    // RadarCard already uses, so the notification tells the two states apart at a glance instead
    // of repeating the small icon (which Android forces monochrome in the status bar anyway).
    private const val REMINDER_COLOR = 0xFFF59E0B.toInt()
    private const val READY_COLOR = 0xFF10B981.toInt()

    // Drawn once per type, not decoded from a resource — cheap to build at runtime with plain
    // Canvas primitives, so doff alerts firing dozens of times per shift don't redo any work.
    @Volatile private var cachedReminderIcon: Bitmap? = null
    @Volatile private var cachedReadyIcon: Bitmap? = null

    private fun contextIcon(isReminder: Boolean): Bitmap {
        (if (isReminder) cachedReminderIcon else cachedReadyIcon)?.let { return it }
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isReminder) REMINDER_COLOR else READY_COLOR
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.09f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (isReminder) {
            // Clock face — "waktu hampir tiba".
            val r = size * 0.28f
            val cx = size / 2f
            val cy = size / 2f
            canvas.drawCircle(cx, cy, r, fgPaint)
            canvas.drawLine(cx, cy, cx, cy - r * 0.6f, fgPaint)
            canvas.drawLine(cx, cy, cx + r * 0.5f, cy, fgPaint)
        } else {
            // Checkmark — "siap doff".
            val path = Path().apply {
                moveTo(size * 0.28f, size * 0.52f)
                lineTo(size * 0.44f, size * 0.68f)
                lineTo(size * 0.74f, size * 0.34f)
            }
            canvas.drawPath(path, fgPaint)
        }
        if (isReminder) cachedReminderIcon = bmp else cachedReadyIcon = bmp
        return bmp
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Doff Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifikasi waktu doff mesin"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // leadMinutes defaults to REMINDER_LEAD_MIN (the constant RadarCard's swipe-gating also uses)
    // but is deliberately a separate, caller-supplied value — Pengaturan's notification lead-time
    // picker changes this without touching REMINDER_LEAD_MIN itself, so adjusting how early a
    // doff alert fires never shifts when swipe-right unlocks on the radar card.
    fun scheduleNotif(context: Context, mcNo: String, estAbsMin: Long, leadMinutes: Long = REMINDER_LEAD_MIN) {
        // Replace any old reminder/ready alarms before applying the new future-time policy.
        // Otherwise editing an estimate into the past leaves the old PendingIntent armed.
        cancelNotif(context, mcNo)
        val now = System.currentTimeMillis() / 60000L
        val reminderAt = estAbsMin - leadMinutes
        if (reminderAt > now) {
            scheduleAt(context, mcNo, reminderAt, isReminder = true, leadMinutes = leadMinutes)
        }
        // Only schedule the "siap doff" alarm if the estimate is still in the future.
        // For an already-past estimate, AlarmManager would fire it instantly — but the operator
        // is looking at the screen when they enter it and the RadarCard already shows it as
        // overdue, so an immediate notification would just be redundant noise.
        if (estAbsMin > now) {
            scheduleAt(context, mcNo, estAbsMin, isReminder = false, leadMinutes = leadMinutes)
        }
    }

    private fun scheduleAt(context: Context, mcNo: String, atAbsMin: Long, isReminder: Boolean, leadMinutes: Long) {
        val alarmTime = atAbsMin * 60000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("mcNo", mcNo)
            putExtra("isReminder", isReminder)
            // The reminder alarm's own notification text says "X menit lagi" — carried through
            // the PendingIntent so AlarmReceiver can show the lead time this specific alarm was
            // actually scheduled with, not whatever Pengaturan's picker happens to read at fire
            // time (which may have changed since — rescheduleAll already re-arms every pending
            // alarm whenever the setting changes, so this only matters for the brief window
            // before that reschedule catches up).
            putExtra("leadMinutes", leadMinutes)
        }
        val notifId = notifIdFor(mcNo, isReminder)
        val pi = PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, alarmTime, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
            }
        } catch (e: SecurityException) {
            runCatching { am.set(AlarmManager.RTC_WAKEUP, alarmTime, pi) }
        }
    }

    fun cancelNotif(context: Context, mcNo: String) {
        cancelOne(context, mcNo, isReminder = true)
        cancelOne(context, mcNo, isReminder = false)
    }

    private fun cancelOne(context: Context, mcNo: String, isReminder: Boolean) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val notifId = notifIdFor(mcNo, isReminder)
        val pi = PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) context.getSystemService(AlarmManager::class.java).cancel(pi)
        context.getSystemService(NotificationManager::class.java).cancel(notifId)
    }

    fun cancelAll(context: Context, mcNos: List<String>) {
        mcNos.forEach { cancelNotif(context, it) }
    }

    /** Schedules a notif for every estimasi still in the future — shared by BootReceiver (after a
     * device reboot) and MainScreen's backup-import flow, which both need to reschedule a whole
     * batch of estimasi at once from a freshly-loaded/restored [DoffState]. */
    fun rescheduleAll(
        context: Context,
        estimasi: Collection<Estimasi>,
        now: Long = nowAbsMin(),
        leadMinutes: Long = REMINDER_LEAD_MIN,
    ) {
        estimasi
            .filter { it.pausedAtAbsMin == null && it.estAbsMin > now }
            .forEach { scheduleNotif(context, it.mcNo, it.estAbsMin, leadMinutes) }
    }

    fun showNotification(context: Context, mcNo: String, isReminder: Boolean, leadMinutes: Long = REMINDER_LEAD_MIN) {
        val notifId = notifIdFor(mcNo, isReminder)
        // Corak/target yard used to be appended here, but on a real device that pushed the actual
        // "X menit lagi"/"siap doff" status past the title's truncation point (see the reported
        // notification screenshot) — mcNo is the one thing an operator actually needs to spot fast.
        val label = "Mc $mcNo"
        val tapIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doffIntent = PendingIntent.getBroadcast(
            context, notifId,
            Intent(context, DoffActionReceiver::class.java).apply {
                putExtra("mcNo", mcNo)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Small icon: status bar, forced monochrome by Android. Large icon: shown in the shade —
        // used to just be a second copy of the app icon (redundant with the small one, see the
        // reported screenshot), now a reminder/ready glyph so it actually adds information instead
        // of repeating the app identity twice in the same row.
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setLargeIcon(contextIcon(isReminder))
            .setContentTitle(if (isReminder) "$label — $leadMinutes menit lagi" else "$label — siap doff")
            .setContentText(if (isReminder) "Bersiap, estimasi hampir tiba" else "Estimasi waktu telah tiba")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .addAction(0, "Doff", doffIntent)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notifId, notif)
    }

    private fun notifIdFor(mcNo: String, isReminder: Boolean = false): Int {
        val base = mcNo.toIntOrNull() ?: mcNo.hashCode()
        return if (isReminder) base + REMINDER_ID_OFFSET else base
    }

    /** Fires immediately, bypassing AlarmManager entirely — Pengaturan's "Kirim Uji Coba" button
     * so an operator can confirm the channel/permission actually delivers a notification to this
     * phone, without waiting for a real estimasi to reach its reminder window. */
    fun sendTestNotification(context: Context) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setLargeIcon(contextIcon(isReminder = true))
            .setContentTitle("🔔 Adoel Doffing · Uji Notifikasi")
            .setContentText("Notifikasi aktif! Operator akan diberi tahu otomatis saat mesin mendekati waktu doffing.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notifIdFor("test"), notif)
    }
}
