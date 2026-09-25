package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.LocalAppColors

/**
 * The "MC" caption stacked over a big bold machine number, boxed in a tinted cyan pill — the one
 * badge every screen that lists or refers to a specific machine (Baris Mesin, Riwayat, rincian
 * shift di Statistik, dialog edit mesin) shares, so a machine number always reads the same way
 * wherever it shows up instead of drifting into a plain-text number in some places and a boxed
 * one in others.
 */
@Composable
fun McBadgeBox(
    mcNo: String,
    modifier: Modifier = Modifier,
    numberFontSize: TextUnit = 18.sp,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Cyan500.copy(alpha = 0.14f))
            .border(1.dp, Cyan500.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "MC",
            style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = Cyan400),
        )
        Text(
            mcNo,
            style = TextStyle(fontSize = numberFontSize, fontWeight = FontWeight.Black, color = colors.textPrimary),
            maxLines = 1,
            softWrap = false,
        )
    }
}
