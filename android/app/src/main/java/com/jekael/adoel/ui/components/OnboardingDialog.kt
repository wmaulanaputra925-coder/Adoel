package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.*

/**
 * Onboarding and Operational Guide Dialog for Adoel.
 * Provides two comprehensive sections: Operational Workflow and Radar Gestures.
 */
@Composable
fun OnboardingDialog(onClose: () -> Unit) {
    val colors = LocalAppColors.current
    var tab by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    FloatingEditDialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.Space8),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Cyan600.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            "Panduan Penggunaan",
                            style = AppType.DialogTitle.copy(color = colors.textPrimary, fontSize = 16.sp),
                        )
                        Text(
                            "Pelajari cara operasional & gestur cepat Adoel",
                            style = AppType.Caption.copy(color = colors.textFaint, fontSize = 11.sp),
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Panduan",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Tab Switcher
            SlidingToggle(
                labelLeft = "Alur Operasional",
                labelRight = "Gestur Radar",
                selectedIndex = tab,
                onSelect = { tab = it },
                containerColor = colors.bgElevated2,
                activeColorLeft = Cyan600,
                activeColorRight = Cyan600,
                activeTextColorLeft = Color.White,
                activeTextColorRight = Color.White,
                inactiveTextColor = colors.textMuted,
                modifier = Modifier.fillMaxWidth(),
                accessibilityLabel = "Pilihan tab panduan Adoel",
                iconLeft = Icons.AutoMirrored.Outlined.MenuBook,
                iconRight = Icons.Outlined.Swipe,
            )

            // Body Content (Scrollable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) {
                    OperationalFlowGuide()
                } else {
                    RadarGestureGuide()
                }
            }

            Spacer(Modifier.height(Dimens.Space4))

            // Footer Action Button
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Mengerti & Tutup Panduan",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun OperationalFlowGuide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GuideRow(
            icon = Icons.Outlined.Schedule,
            iconBg = Cyan600,
            titleColor = Cyan400,
            stepNumber = "1. Estimasi Waktu Doff",
            description = "Ketik nomor mesin di konsol bawah lalu ketuk tombol ⏱ Estimasi. Isi sisa menit (Tappet/Cam), yard berjalan (D405), atau jam counter (D408).",
        )
        GuideRow(
            icon = Icons.Outlined.ContentCut,
            iconBg = Emerald600,
            titleColor = Emerald400,
            stepNumber = "2. Potong Kain (Doffing)",
            description = "Ketik nomor mesin lalu tekan tombol ✂ Doffing. Atau gunakan cara kilat dengan menggeser kartu mesin di layar Radar.",
        )
        GuideRow(
            icon = Icons.Outlined.Pause,
            iconBg = Amber600,
            titleColor = Amber400,
            stepNumber = "3. Jeda Mesin & Macet",
            description = "Jika mesin berhenti atau ada kendala putus lusi, tekan lama kartu mesin lalu pilih Jeda. Perhitungan waktu istirahat tetap akurat dan data rol kain dibekukan.",
        )
        GuideRow(
            icon = Icons.AutoMirrored.Outlined.Undo,
            iconBg = Amber600,
            titleColor = Amber400,
            stepNumber = "4. Urungkan (Undo / Redo)",
            description = "Salah mencatat atau salah hapus? Tekan tombol panah ↩ Urungkan atau ↪ Ulangi di sisi kiri konsol bawah untuk mengembalikan data seketika.",
        )
        GuideRow(
            icon = Icons.Outlined.Forward,
            iconBg = Sky600,
            titleColor = Sky400,
            stepNumber = "5. Operan Antar-Shift",
            description = "Mesin yang jadwal doffing-nya melebihi jam kerja shift saat ini (>8 jam) secara otomatis ditandai sebagai Operan agar grafik progres kerja tetap rapi.",
        )
    }
}

@Composable
private fun GuideRow(
    icon: ImageVector,
    iconBg: Color,
    titleColor: Color,
    stepNumber: String,
    description: String,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusControl))
            .background(colors.bgElevated2)
            .border(1.dp, colors.border, RoundedCornerShape(Dimens.RadiusControl))
            .padding(Dimens.Space12),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = stepNumber, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stepNumber,
                style = AppType.LabelBold.copy(color = titleColor, fontSize = 13.sp),
            )
            Text(
                description,
                style = AppType.BodySmall.copy(color = colors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp),
            )
        }
    }
}

@Composable
private fun RadarGestureGuide() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Anatomy Preview Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusControl))
                .background(colors.bgElevated2)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "CONTOH ANATOMI KARTU RADAR",
                style = AppType.Caption.copy(
                    fontWeight = FontWeight.Bold,
                    color = Cyan400,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bgElevated1)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("MC 12", style = AppType.LabelBold.copy(color = colors.textPrimary, fontSize = 14.sp))
                    Text("⏱ 02j 40m", style = AppType.LabelBold.copy(color = Amber400, fontSize = 12.sp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.border),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight()
                            .background(Cyan500),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("D408 • Corak 4500", style = AppType.Caption.copy(color = colors.textFaint, fontSize = 11.sp))
                    Text("300y", style = AppType.Caption.copy(color = colors.textFaint, fontSize = 11.sp))
                }
            }
            Text(
                "Sentuh, geser, atau tahan kartu untuk aksi instan:",
                style = AppType.Caption.copy(color = colors.textMuted, fontSize = 11.sp),
            )
        }

        // Gesture 1: Geser ke Kanan
        GestureItem(
            icon = Icons.AutoMirrored.Outlined.ArrowForward,
            iconTint = Emerald400,
            actionLabel = "Geser ke Kanan",
            badgeText = "Doffing Normal",
            badgeColor = Emerald400,
            description = "Usap kartu ke kanan untuk mencatat Doffing Normal saat kain selesai sesuai target yard standar.",
        )

        // Gesture 2: Geser ke Kiri
        GestureItem(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            iconTint = Sky400,
            actionLabel = "Geser ke Kiri",
            badgeText = "Doffing Matching",
            badgeColor = Sky400,
            description = "Usap kartu ke kiri untuk mencatat Doffing Matching (doffing awal pada beam lusi baru untuk potong sampel & cek kualitas kain).",
        )

        // Gesture 3: Ketuk Angka Jam
        GestureItem(
            icon = Icons.Outlined.Schedule,
            iconTint = Cyan400,
            actionLabel = "Ketuk Angka Jam",
            badgeText = "Edit Estimasi",
            badgeColor = Cyan400,
            description = "Ketuk langsung pada angka jam/sisa waktu untuk memperbarui estimasi doffing.",
        )

        // Gesture 4: Ketuk Nomor / Corak
        GestureItem(
            icon = Icons.Outlined.Texture,
            iconTint = Purple400,
            actionLabel = "Ketuk Nomor / Corak",
            badgeText = "Edit Data Mesin",
            badgeColor = Purple400,
            description = "Ketuk nomor mesin atau nama corak untuk mengedit spesifikasi, yard, atau tipe mesin.",
        )

        // Gesture 5: Tekan Lama
        GestureItem(
            icon = Icons.Outlined.TouchApp,
            iconTint = Amber400,
            actionLabel = "Tekan Lama (Long-Press)",
            badgeText = "Menu Jeda / Hapus",
            badgeColor = Amber400,
            description = "Tahan sentuhan pada kartu untuk membuka menu cepat Jeda Mesin atau Hapus. Kartu yang dijeda akan dipisahkan ke baris khusus 'Dijeda' dan dapat dilanjutkan seketika melalui tombol '▶ Lanjutkan'.",
        )
    }
}

@Composable
private fun GestureItem(
    icon: ImageVector,
    iconTint: Color,
    actionLabel: String,
    badgeText: String,
    badgeColor: Color,
    description: String,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusControl))
            .background(colors.bgElevated1)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    actionLabel,
                    style = AppType.LabelBold.copy(color = colors.textPrimary, fontSize = 13.sp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    badgeText,
                    style = AppType.Caption.copy(
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    ),
                )
            }
        }
        Text(
            description,
            style = AppType.BodySmall.copy(color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}
