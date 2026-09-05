package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.AktualEntry
import com.jekael.adoel.data.MesinData
import com.jekael.adoel.data.formatYard
import com.jekael.adoel.ui.theme.Amber400
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.LocalAppColors

/**
 * The one row layout for a recorded doff, shared by Riwayat and by Statistik's shift detail so the
 * two can't drift apart again — they used to be two hand-written layouts saying the same thing in
 * different shapes (Riwayat stacked a big mc number over a corak line; Statistik ran everything
 * inline). Each caller still supplies its own container: Riwayat wraps this in a swipeable list
 * card, Statistik in a flat tappable strip inside the shift card.
 *
 * Every field gets its own chip so they read apart at a glance rather than running together as one
 * sentence: cyan mc number, corak behind the kain icon, yard, keterangan in amber, and the time
 * last. Everything stays on one line — corak is the only part that flexes, so it takes the squeeze
 * (and an ellipsis) before anything else does, and keterangan is capped rather than allowed to
 * wrap the row onto a second line.
 */
@Composable
fun RowScope.DoffEntryRowContent(
    num: Int,
    entry: AktualEntry,
    mesin: MesinData?,
    showEditHint: Boolean,
) {
    val colors = LocalAppColors.current
    val corak = entry.corakOverride ?: mesin?.corak ?: "—"
    val yard = entry.customYard ?: mesin?.targetYard
    // entry.ket is "$jam($extra)" when the doff carried a keterangan, or bare "$jam" when it
    // didn't (see DoffViewModel.prosesBarisUmum). The time has its own chip at the end, so strip
    // it back off here and keep only the code — otherwise the row prints the clock twice.
    val ketCode = entry.ket.removePrefix(entry.jam).removeSurrounding("(", ")")

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.bgElevated)
            .border(1.dp, colors.border, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$num",
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.textFaint),
        )
    }

    if (mesin != null) {
        MesinTipeIcon(tipe = mesin.tipe, tint = mesinTipeColor(mesin.tipe), modifier = Modifier.size(13.dp))
    } else {
        Icon(
            imageVector = Icons.Outlined.Circle,
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(13.dp),
        )
    }

    // Just the number — the surrounding chips already make it obvious this is the machine.
    Text(
        entry.mcNo,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Cyan400),
        maxLines = 1,
        softWrap = false,
    )

    // The only flexible chip in the row: it gives up width first, so the fixed ones after it stay
    // whole no matter how long a corak name gets.
    Row(
        modifier = Modifier
            .weight(1f, fill = false)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.bgElevated)
            .border(1.dp, colors.border, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Texture,
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(11.dp),
        )
        Text(
            corak,
            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (yard != null) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(colors.bgElevated)
                .border(1.dp, colors.border, RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                "${formatYard(yard)}y",
                style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.textFaint),
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (ketCode.isNotEmpty()) {
        Box(
            modifier = Modifier
                // Capped instead of flexible: a long free-typed keterangan should trail off, not
                // push the row onto a second line or squeeze the time chip out.
                .widthIn(max = 104.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Amber400.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                ketCode,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = Amber400),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.bgElevated)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            entry.jam,
            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary),
            maxLines = 1,
            softWrap = false,
        )
        if (showEditHint) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = colors.textFaint,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
