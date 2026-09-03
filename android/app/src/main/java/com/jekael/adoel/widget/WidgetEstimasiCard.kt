package com.jekael.adoel.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jekael.adoel.data.Estimasi
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.UrgencyLevel
import com.jekael.adoel.data.absMinToTimeStr
import com.jekael.adoel.data.effectiveRemaining
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.data.urgencyLevel
import com.jekael.adoel.ui.components.mesinTipeColor
import com.jekael.adoel.ui.theme.Amber500
import com.jekael.adoel.ui.theme.Amber600
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.Red500
import com.jekael.adoel.ui.theme.Zinc50
import com.jekael.adoel.ui.theme.Zinc900

/** Glance-native, read-only card for one estimasi — no action buttons; the whole row (see
 * AdoelWidget's LazyColumn item wrapper) opens the app on tap instead. Neutral card background
 * (theme-aware) + a colored accent for urgency, mirroring RadarCard's actual in-app pattern
 * rather than the solid saturated urgency backgrounds this card used to have (which were also
 * never theme-aware). */
@Composable
fun WidgetEstimasiCard(est: Estimasi, mesin: MesinData?, now: Long, dark: Boolean) {
    val remaining = est.effectiveRemaining(now)
    val accent = when (urgencyLevel(remaining)) {
        UrgencyLevel.CALM -> Cyan500
        UrgencyLevel.SOON -> Amber500
        UrgencyLevel.IMMINENT -> Amber600
        UrgencyLevel.OVERDUE -> Red500
    }
    val bg = if (dark) Zinc900 else Color.White
    val textColor = if (dark) Zinc50 else Zinc900
    val corak = est.corakOverride ?: mesin?.corak ?: "—"
    val yard = est.yardOverride ?: mesin?.targetYard
    val corakLine = if (yard != null) "$corak · ${formatYard(yard)}y" else corak

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(Dimens.RadiusControl)
            .background(bg),
    ) {
        // Top accent strip echoing RadarCard's colored left edge — Glance (the widget toolkit)
        // has no Canvas/custom drawing API to reuse RadarCard's twisted-thread band effect, so
        // this stays a plain color block; a horizontal strip (not a side column) is what Glance's
        // simplified Row/Column stretching can size reliably without needing cross-axis fill.
        Box(modifier = GlanceModifier.fillMaxWidth().height(3.dp).background(accent)) {}
        Column(modifier = GlanceModifier.padding(12.dp)) {
            Row {
                Text("Mc ${est.mcNo}", style = TextStyle(color = ColorProvider(textColor), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                if (mesin != null) {
                    Text(
                        text = "  ${mesin.tipe.name}",
                        // Same non-urgency machine-type color as RadarCard/DoffingSection — Glance
                        // has no custom Canvas drawing, so this stays a colored label rather than
                        // an icon (see the accent-strip comment above for the same constraint).
                        style = TextStyle(color = ColorProvider(mesinTipeColor(mesin.tipe)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Text(corakLine, style = TextStyle(color = ColorProvider(textColor), fontSize = 12.sp), maxLines = 1)
            Text(
                text = if (est.pausedAtAbsMin != null) {
                    "⏸ Dijeda · siap jam ${absMinToTimeStr(est.estAbsMin)}"
                } else {
                    "Siap jam ${absMinToTimeStr(est.estAbsMin)}"
                },
                style = TextStyle(color = ColorProvider(accent), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}
