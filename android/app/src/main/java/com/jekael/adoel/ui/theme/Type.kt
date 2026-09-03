package com.jekael.adoel.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Named text-style tokens for roles that repeat verbatim across multiple screens/components,
 * so those spots share one definition instead of copy-pasted TextStyle(...) blocks that can
 * silently drift apart. Color is deliberately left unset here — callers apply `.copy(color = ...)`
 * since colors come from LocalAppColors/theme and vary per usage. Styles that only appear once
 * (hero numbers on RadarCard, the "Adoel" wordmark, etc.) are left inline rather than forced
 * into this scale — a shared token should reflect real reuse, not an arbitrary size count. */
object AppType {
    val DialogTitle = TextStyle(fontFamily = InterFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val NumberLarge = TextStyle(fontFamily = InterFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black)
    val LabelBold = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val LabelSmallBold = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val Caption = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp)
    val CaptionBold = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val FieldText = TextStyle(fontFamily = InterFontFamily, fontSize = 14.sp)
    val TabLabel = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val BodySmall = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp)
}
