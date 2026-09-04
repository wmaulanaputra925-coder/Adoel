package com.jekael.adoel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.shiftNumberForEpochMin
import com.jekael.adoel.ui.components.GearIcon
import com.jekael.adoel.ui.components.LinearProgressBar
import com.jekael.adoel.ui.components.SlidingToggle
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar

/** Floating header — branding (with a tap-pulse micro-interaction), shift progress, the
 * permanently-visible Statistik/Pengaturan icons, and the Radar/Riwayat page tab row (Master
 * Blueprint §4A/§4E: page switching moved up here, off the console bar, and the two shift-wide
 * shortcut icons stay on screen instead of hiding behind a chevron). Reports its own measured
 * height via [onHeightMeasured] so the scrollable list behind it can pad itself to avoid sitting
 * under the card. */
@Composable
internal fun MainScreenHeader(
    nowAbs: Long,
    totalMc: Int,
    doffCount: Int,
    estimasiCount: Int,
    showRemaining: Boolean,
    onToggleShowRemaining: () -> Unit,
    onDaftarMesin: () -> Unit,
    onGearClick: () -> Unit,
    onSyncClick: () -> Unit,
    onShare: () -> Unit,
    onFinishShift: () -> Unit,
    showFinishShift: Boolean,
    onStatistik: () -> Unit,
    page: Page,
    onPageSelect: (Page) -> Unit,
    onHeightMeasured: (Dp) -> Unit,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                onHeightMeasured(with(density) { coords.size.height.toDp() })
            }
            .padding(horizontal = Dimens.Space12)
            .padding(top = Dimens.Space12)
            .floatingHeaderCard(),
    ) {
      Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space16, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Branding — a one-shot ~320ms tap pulse: logo settles to 0.97, the dot hops up
            // 7dp and a thin glow blooms from it, all finishing on their own (not tied to how
            // long the finger stays down) so repeated daily taps stay quick and subtle.
            var brandPulseKey by remember { mutableStateOf(0) }
            val brandScale = remember { Animatable(1f) }
            val dotOffsetY = remember { Animatable(0f) }
            val glowAlpha = remember { Animatable(0f) }
            val glowScale = remember { Animatable(0.6f) }
            LaunchedEffect(brandPulseKey) {
                if (brandPulseKey == 0) return@LaunchedEffect
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                launch {
                    brandScale.animateTo(0.97f, tween(90, easing = FastOutSlowInEasing))
                    brandScale.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                }
                launch {
                    dotOffsetY.animateTo(-7f, tween(120, easing = FastOutSlowInEasing))
                    dotOffsetY.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                }
                launch {
                    glowScale.snapTo(0.6f)
                    glowAlpha.snapTo(0.4f)
                    launch { glowAlpha.animateTo(0f, tween(300, easing = LinearOutSlowInEasing)) }
                    glowScale.animateTo(2.2f, tween(320, easing = LinearOutSlowInEasing))
                }
            }
            val shiftLabel = remember(nowAbs) {
                val cal = Calendar.getInstance().apply { timeInMillis = nowAbs * 60000L }
                "Shift ${shiftNumberForEpochMin(nowAbs)} · %02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .graphicsLayer { scaleX = brandScale.value; scaleY = brandScale.value }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Animasi logo",
                        ) { brandPulseKey++ },
                ) {
                    Text(
                        text = "Adoel",
                        // Matches the app icon's brand blue + amber dot instead of a neutral
                        // textPrimary wordmark, so the in-app header reads as the same identity.
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = if (colors.isDark) Cyan400 else Cyan600,
                            letterSpacing = (-0.5).sp,
                        ),
                    )
                    Text(
                        text = ".",
                        modifier = Modifier
                            .offset(y = dotOffsetY.value.dp)
                            .drawBehind {
                                drawCircle(
                                    color = Amber500.copy(alpha = glowAlpha.value),
                                    radius = (size.minDimension.coerceAtLeast(20f)) * glowScale.value,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                )
                            },
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Black, color = Amber500),
                    )
                }
                Text(
                    text = shiftLabel,
                    style = AppType.Caption.copy(color = colors.textFaint),
                )
            }

            // Shift progress — centered between branding and the icon buttons
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (totalMc > 0) {
                    val remainingMc = totalMc - doffCount
                    val shiftFraction = doffCount.toFloat() / totalMc
                    val animatedFraction by animateFloatAsState(
                        targetValue = shiftFraction.coerceIn(0f, 1f),
                        animationSpec = tween(250),
                        label = "shiftProgress",
                    )
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "Ganti tampilan jumlah selesai/sisa") {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleShowRemaining()
                            }
                            .padding(horizontal = 10.dp, vertical = Dimens.Space4),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = when {
                                !showRemaining -> "$doffCount/$totalMc"
                                remainingMc <= 0 -> "Selesai"
                                else -> "$remainingMc lagi"
                            },
                            style = AppType.LabelSmallBold.copy(color = Cyan400),
                        )
                        Spacer(Modifier.height(Dimens.Space4))
                        LinearProgressBar(
                            fraction = animatedFraction,
                            trackColor = colors.bgElevated2,
                            fillColor = Cyan500,
                        )
                    }
                }
            }

            var actionsExpanded by remember { mutableStateOf(false) }
            if (showFinishShift) {
                val attention = rememberInfiniteTransition(label = "finishShiftAttention")
                val finishScale by attention.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "finishShiftScale",
                )
                IconButton(
                    onClick = onFinishShift,
                    modifier = Modifier.graphicsLayer {
                        scaleX = finishScale
                        scaleY = finishScale
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = "Selesai Shift",
                        tint = Red500,
                    )
                }
            }
            Box {
                IconButton(onClick = { actionsExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Aksi lainnya",
                        tint = colors.textMuted,
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Daftar Mesin") },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        onClick = { actionsExpanded = false; onDaftarMesin() },
                    )
                    DropdownMenuItem(
                        text = { Text("Statistik") },
                        leadingIcon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                        onClick = { actionsExpanded = false; onStatistik() },
                    )
                    DropdownMenuItem(
                        text = { Text("Pengaturan") },
                        leadingIcon = { GearIcon() },
                        onClick = { actionsExpanded = false; onGearClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("QR Sync") },
                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                        onClick = { actionsExpanded = false; onSyncClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("Bagikan") },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = { actionsExpanded = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Selesai Shift") },
                        leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                        onClick = { actionsExpanded = false; onFinishShift() },
                    )
                }
            }
        }

        // Page tab — Radar Estimasi vs Riwayat Doffing, decoupled from the console bar (Master
        // Blueprint §4A): purely a view switcher now, with no bearing on what the console does.
        SlidingToggle(
            labelLeft = "Radar",
            labelRight = "Riwayat",
            iconLeft = Icons.Outlined.Radar,
            iconRight = Icons.Outlined.History,
            badgeLeft = estimasiCount.takeIf { it > 0 },
            badgeRight = doffCount.takeIf { it > 0 },
            accessibilityLabel = "Halaman: Radar/Riwayat",
            selectedIndex = if (page == Page.RADAR) 0 else 1,
            onSelect = { onPageSelect(if (it == 0) Page.RADAR else Page.RIWAYAT) },
            containerColor = colors.bgElevated2,
            activeColorLeft = Cyan600,
            activeColorRight = Cyan600,
            activeTextColorLeft = Zinc950,
            activeTextColorRight = Zinc950,
            inactiveTextColor = colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(start = Dimens.Space16, end = Dimens.Space16, bottom = Dimens.Space12),
            height = 38.dp,
        )
      }
    }
}
