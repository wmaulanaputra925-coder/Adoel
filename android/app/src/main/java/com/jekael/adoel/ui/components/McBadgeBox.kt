package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Cyan700
import com.jekael.adoel.ui.theme.LocalAppColors

/**
 * The "MC" caption stacked over a big bold machine number, glossed with a top-lit cyan gradient —
 * the one badge every screen that lists or refers to a specific machine (Baris Mesin, Riwayat,
 * rincian shift di Statistik, dialog edit mesin) shares, so a machine number always reads the
 * same way wherever it shows up instead of drifting into a plain-text number in some places and a
 * boxed one in others.
 */
@Composable
fun McBadgeBox(
    mcNo: String,
    modifier: Modifier = Modifier,
    boxWidth: Dp = 48.dp,
    boxHeight: Dp = 44.dp,
    numberFontSize: TextUnit = 16.5.sp,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    // Dark: a navy-to-cyan gradient bright enough to carry a white, shadowed number. Light: too
    // pale a card for white text, so it stays a faint cyan wash under Cyan700 (matches web's
    // separate :root[data-theme="light"] .mc-badge-box/.mc-badge-num rules).
    val gradient = if (colors.isDark) {
        Brush.verticalGradient(listOf(lerp(colors.bgElevated, Cyan400, 0.20f), lerp(colors.bgElevated, Color(0xFF082F49), 0.30f)))
    } else {
        Brush.verticalGradient(listOf(Cyan400.copy(alpha = 0.15f), Cyan400.copy(alpha = 0.05f)))
    }
    val labelColor = if (colors.isDark) Cyan400 else Cyan600
    val numberColor = if (colors.isDark) Color.White else Cyan700
    Box(
        modifier = modifier
            .size(width = boxWidth, height = boxHeight)
            .clip(shape)
            .background(colors.bgElevated)
            .background(gradient)
            .border(1.dp, Cyan400.copy(alpha = if (colors.isDark) 0.45f else 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // Glossy top sheen — the inset highlight that reads as light falling on a raised surface.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.7f)),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "MC",
                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = labelColor),
            )
            Text(
                mcNo,
                style = TextStyle(
                    fontSize = numberFontSize,
                    fontWeight = FontWeight.Black,
                    color = numberColor,
                    letterSpacing = (-0.3).sp,
                    shadow = Shadow(
                        color = if (colors.isDark) Color.Black.copy(alpha = 0.5f) else Color.Transparent,
                        offset = Offset(0f, 1f),
                        blurRadius = 2f,
                    ),
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Neutral (no color tint) counterpart to [McBadgeBox] — same glossy top-lit gradient over a plain
 * tonal box instead of a cyan wash, [value]'s color overridable per caller. Backs DoffEntryRow's
 * "No" badge and Statistik's per-shift "SHIFT" badge (web: `.shift-badge-box`, which reuses the
 * same box shape but drops the cyan tint and colors the number per shift instead).
 */
@Composable
fun GlossyBadgeBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    boxWidth: Dp = 48.dp,
    boxHeight: Dp = 44.dp,
    valueFontSize: TextUnit = 16.5.sp,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(width = boxWidth, height = boxHeight)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(colors.bgElevated, Color.White, 0.12f), colors.bgElevated)))
            .border(1.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = colors.textFaint),
            )
            Text(
                value,
                style = TextStyle(
                    fontSize = valueFontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp,
                    color = valueColor ?: colors.textPrimary,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
