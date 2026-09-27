package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.BuildConfig
import com.jekael.adoel.ui.theme.*

@Composable
internal fun AboutDialog(onClose: () -> Unit) {
    val colors = LocalAppColors.current
    FloatingEditDialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Same brand blue "Adoel" + amber "." split MainScreenHeader's own wordmark uses
            // (matching the app icon's own colors) — this used to be a single flat textPrimary
            // string, its own separate identity instead of the one the header/icon already
            // established.
            Row {
                Text(
                    "Adoel",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        color = if (colors.isDark) Cyan400 else Cyan600,
                    ),
                )
                Text(
                    ".",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        color = Amber500,
                    ),
                )
            }
            Spacer(Modifier.height(Dimens.Space4))
            Text(
                "Aplikasi Estimasi Doff & Manajemen Mesin Tenun",
                style = AppType.Caption.copy(color = colors.textFaint, fontSize = 12.sp),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Dimens.Space16))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgElevated2)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Versi ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · Android Edition",
                    style = AppType.Caption.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Mendukung sinkronisasi QR dua arah dengan versi Web",
                    style = AppType.Caption.copy(
                        color = colors.textFaint,
                        fontSize = 11.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(Dimens.Space12))

            Text(
                "Dikembangkan oleh Wahyu Maulana Putra",
                style = AppType.Caption.copy(
                    color = colors.textFaint,
                    fontSize = 11.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Dimens.Space20))

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
            ) {
                Text("Tutup", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
