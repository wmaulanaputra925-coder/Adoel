package com.jekael.adoel.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jekael.adoel.MainActivity
import com.jekael.adoel.data.DoffRepository
import com.jekael.adoel.data.formatDeltaMin
import com.jekael.adoel.data.absMinToTimeStr
import com.jekael.adoel.data.nowAbsMin
import com.jekael.adoel.data.partitionSegeraMenunggu
import com.jekael.adoel.data.shiftNumberForEpochMin
import com.jekael.adoel.data.sortedByNearest
import com.jekael.adoel.ui.BREAK_GAP_THRESHOLD_MIN
import com.jekael.adoel.ui.theme.Amber500
import com.jekael.adoel.ui.theme.Emerald500
import com.jekael.adoel.ui.theme.ThemeMode
import com.jekael.adoel.ui.theme.Zinc50
import com.jekael.adoel.ui.theme.Zinc900
import com.jekael.adoel.ui.theme.Zinc950

/**
 * Home-screen widget: read-only scrollable list of every pending estimasi (nearest/most-overdue
 * first) — tapping anywhere on it opens the app, no in-widget actions. Reads the shared
 * [DoffRepository] singleton directly — same independent-access pattern already used by
 * [com.jekael.adoel.notification.DoffActionReceiver] outside the Activity/ViewModel scope.
 *
 * Tap-to-open is applied to the header, the empty state, and each row individually (not just the
 * outer Column) because a RemoteViews-backed LazyColumn intercepts touches for its own rows —
 * an ancestor's clickable modifier alone doesn't fire for taps landing on list items.
 */
class AdoelWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = DoffRepository.getInstance(context).load()
        val now = nowAbsMin()
        val sorted = sortedByNearest(state.estimasi)
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        val dark = resolveWidgetDarkTheme(context, state.themeMode)
        val bg = if (dark) Zinc950 else Zinc50
        val textColor = if (dark) Zinc50 else Zinc900
        val shiftLabel = "Shift ${shiftNumberForEpochMin(now)}"
        // Mirrors the in-app leading break gap (RadarSection's BreakGapCard): only worth
        // surfacing when nothing is overdue and the wait to the next doff clears the same
        // threshold — an operator glancing at the lock screen wants "can I actually step away",
        // not a running clock down to every upcoming machine.
        val (segera, menunggu) = partitionSegeraMenunggu(sorted, now)
        val jedaText = if (segera.isEmpty()) {
            menunggu.firstOrNull()?.let { next ->
                val gap = next.estAbsMin - now
                if (gap >= BREAK_GAP_THRESHOLD_MIN) {
                    "⏸ JEDA: ${formatDeltaMin(gap)} lagi (s.d ${absMinToTimeStr(next.estAbsMin)})"
                } else null
            }
        } else null

        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().background(bg)) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable(openApp),
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = "Adoel",
                            style = TextStyle(color = ColorProvider(textColor), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = ".",
                            style = TextStyle(color = ColorProvider(Amber500), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = " $shiftLabel",
                            style = TextStyle(color = ColorProvider(textColor), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    if (jedaText != null) {
                        Text(
                            text = jedaText,
                            style = TextStyle(color = ColorProvider(Emerald500), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
                if (sorted.isEmpty()) {
                    Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp).clickable(openApp)) {
                        Text(
                            text = "Tidak ada estimasi",
                            style = TextStyle(color = ColorProvider(textColor), fontSize = 13.sp),
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(items = sorted, itemId = { it.mcNo.hashCode().toLong() }) { est ->
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clickable(openApp),
                            ) {
                                WidgetEstimasiCard(est = est, mesin = state.db[est.mcNo], now = now, dark = dark)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Mirrors [com.jekael.adoel.ui.theme.resolveDarkTheme] for Glance, which has no Compose
 * `isSystemInDarkTheme()` — "SYSTEM" mode reads the OS night-mode flag directly instead. */
private fun resolveWidgetDarkTheme(context: Context, themeModeRaw: String): Boolean {
    val mode = runCatching { ThemeMode.valueOf(themeModeRaw) }.getOrDefault(ThemeMode.SYSTEM)
    return when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
