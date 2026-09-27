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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Random cheers swapped into the branding tap's subtitle line for a beat (see brandPulseKey) —
 * plain, casual shop-floor encouragement, not corporate-sounding, and "Bravooo!!!" stays exactly
 * as loud as it was typed. */
private val logoTapPhrases = listOf(
    "Gaspol!",
    "Semangat, Bos!",
    "Mantap jiwa!",
    "Kerja solid!",
    "Tetap melaju!",
    "Juara hari ini!",
    "Ayo lanjut!",
    "Kerja cerdas!",
    "Ngebut terus!",
    "Top markotop!",
    "Bravooo!!!",
)

/** Fixed 6-way spread for the tap confetti below — angles only, no randomness (recomputing a
 * fresh random spread every tap would jitter between one burst and the next; a fixed spread reads
 * as one consistent little firework each time instead). */
private val confettiAngles = List(6) { i -> (i * 60f) * (PI.toFloat() / 180f) }
private val confettiColors = listOf(Cyan400, Amber500, Emerald400, Red400, Cyan400, Amber500)

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
    onClearShiftNoArchive: () -> Unit,
    showFinishShift: Boolean,
    onStatistik: () -> Unit,
    page: Page,
    onPageSelect: (Page) -> Unit,
    onHeightMeasured: (Dp) -> Unit,
    haptic: HapticFeedback,
    operatorNama: String?,
    operatorGrup: String?,
    // Machine count of a clash run about to come due soon (see upcomingClashMachineCount),
    // or null when nothing's imminent — surfaced here rather than left to RadarSection's own
    // GASPOL divider so it reaches the operator even when that part of the list isn't currently
    // scrolled into view, or Riwayat is the active page.
    upcomingClashCount: Int?,
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
            // long the finger stays down) so repeated daily taps stay quick and subtle. A small
            // confetti burst off the dot plus a random cheer swapped into the subtitle line for a
            // beat (see logoTapPhrases) layer on top of that first small experiment — this is a
            // rigid production-floor app the rest of the time, so the one purely playful moment
            // in it earns a proper pop instead of staying this understated forever.
            var brandPulseKey by remember { mutableStateOf(0) }
            val brandScale = remember { Animatable(1f) }
            val dotOffsetY = remember { Animatable(0f) }
            val glowAlpha = remember { Animatable(0f) }
            val glowScale = remember { Animatable(0.6f) }
            val confetti = remember { Animatable(0f) }
            var tapPhrase by remember { mutableStateOf<String?>(null) }
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
                launch {
                    confetti.snapTo(0f)
                    confetti.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
                }
                launch {
                    tapPhrase = logoTapPhrases.random()
                    delay(1400)
                    tapPhrase = null
                }
            }
            val shiftLabel = remember(nowAbs) {
                val cal = Calendar.getInstance().apply { timeInMillis = nowAbs * 60000L }
                "Shift ${shiftNumberForEpochMin(nowAbs)} · %02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
            }
            // Operator identitas ditambahkan ke label yang sama, sebagai info saja — mengedit
            // identitas sekarang cuma lewat Pengaturan (satu tempat, bukan tiga: dulu caption ini,
            // menu dropdown, dan Pengaturan semuanya membuka OperatorDialog yang sama).
            val operatorSuffix = remember(operatorNama, operatorGrup) {
                if (!operatorNama.isNullOrBlank() || !operatorGrup.isNullOrBlank()) {
                    val nama = operatorNama?.takeIf { it.isNotBlank() } ?: "Grup $operatorGrup"
                    val grup = if (!operatorNama.isNullOrBlank() && !operatorGrup.isNullOrBlank()) " ($operatorGrup)" else ""
                    " • $nama$grup"
                } else {
                    ""
                }
            }
            val gaspolWarning = upcomingClashCount?.let { "⚡ Bersiap! $it mesin GASPOL sebentar lagi" }
            // Only pays for the animation while there's actually a warning to show — same
            // reasoning RadarCard's own OVERDUE pulse uses for not running unconditionally.
            val gaspolAlpha = if (gaspolWarning != null) {
                val pulse = rememberInfiniteTransition(label = "gaspolWarningPulse")
                val alpha by pulse.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "gaspolWarningAlpha",
                )
                alpha
            } else {
                1f
            }
            Column(
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
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
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val burst = confetti.value
                                if (burst > 0f && burst < 1f) {
                                    val travel = (size.minDimension.coerceAtLeast(20f)) * 1.6f * burst
                                    val fade = 1f - burst
                                    confettiColors.forEachIndexed { i, color ->
                                        val angle = confettiAngles[i]
                                        drawCircle(
                                            color = color.copy(alpha = fade),
                                            radius = 2.5f * fade + 1f,
                                            center = Offset(
                                                center.x + cos(angle) * travel,
                                                center.y + sin(angle) * travel - 6f * burst * burst,
                                            ),
                                        )
                                    }
                                }
                            },
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Black, color = Amber500),
                    )
                }
                // Same slot the shift/operator label already occupies, in priority order: the
                // tap cheer (brief, dismisses itself) outranks the GASPOL warning (holds as long
                // as the condition does) outranks the normal label — no separate floating bubble
                // to worry about clipping against floatingHeaderCard's own rounded-rect clip.
                Text(
                    text = tapPhrase ?: gaspolWarning ?: (shiftLabel + operatorSuffix),
                    style = AppType.Caption.copy(
                        color = when {
                            tapPhrase != null -> Emerald400
                            gaspolWarning != null -> Amber400.copy(alpha = gaspolAlpha)
                            else -> colors.textFaint
                        },
                        fontWeight = if (tapPhrase != null || gaspolWarning != null) FontWeight.Bold else FontWeight.Normal,
                    ),
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
                // Custom-shaped instead of stock DropdownMenuItem — Material's default menu reads
                // stiff (4dp corners, its own min-width/padding rules) against every other surface
                // in this app, which is all rounded-10/16dp cards with a border. Matches web's own
                // .header-dropdown-menu/.dropdown-item (App.tsx/index.css) instead: a bordered
                // rounded-16dp card sized to its content — not the near-full-width block the old
                // default menu rendered as once "Hapus Semua (Tanpa Arsip)" set its min width.
                //
                // widthIn(max=) caps how wide that one long label can stretch the whole menu:
                // without it, sizing "to its content" means sizing to its *widest* item, and
                // every short item's row (Statistik, QR Sync, …) stretches out to match — capped,
                // only "Hapus Semua"'s own row grows a second line (its subtitle), the rest stay
                // compact single-line rows.
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = colors.bgElevated,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.padding(4.dp).widthIn(max = 220.dp),
                ) {
                    ActionMenuItem(icon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }, label = "Daftar Mesin") {
                        actionsExpanded = false; onDaftarMesin()
                    }
                    ActionMenuItem(icon = { Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(16.dp)) }, label = "Statistik") {
                        actionsExpanded = false; onStatistik()
                    }
                    ActionMenuItem(icon = { GearIcon() }, label = "Pengaturan") {
                        actionsExpanded = false; onGearClick()
                    }
                    ActionMenuItem(icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp)) }, label = "QR Sync") {
                        actionsExpanded = false; onSyncClick()
                    }
                    ActionMenuItem(icon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp)) }, label = "Bagikan") {
                        actionsExpanded = false; onShare()
                    }
                    ActionMenuItem(
                        icon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = Red400, modifier = Modifier.size(16.dp)) },
                        label = "Selesai Shift",
                        danger = true,
                    ) { actionsExpanded = false; onFinishShift() }
                    ActionMenuItem(
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Red400, modifier = Modifier.size(16.dp)) },
                        label = "Hapus Semua",
                        subtitle = "(Tanpa Arsip)",
                        danger = true,
                    ) { actionsExpanded = false; onClearShiftNoArchive() }
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

/** One row in the "Aksi lainnya" dropdown — Android equivalent of web's `.dropdown-item`
 * (App.tsx/index.css): icon + label, rounded-10dp hover/press tint instead of Material's default
 * item chrome. Filled to the menu's own width (matching web: `.header-dropdown-menu` is a flex
 * column, and its children stretch to fill it by default) rather than sized to just the icon+label
 * — leaving a row narrower than the menu meant whatever empty space sat to its right, inside the
 * same visible highlight-shaped row, wasn't actually tappable. [danger] reads the label/icon in
 * [Red400] (Selesai Shift, Hapus Semua) — everything else stays neutral [LocalAppColors.textPrimary].
 * [subtitle] (only "Hapus Semua"'s own qualifier, "(Tanpa Arsip)") sits on its own smaller, muted
 * line below [label] instead of trailing inline after it — a parenthetical qualifier read as part
 * of the same sentence as the action's name, when it's really a separate, secondary detail. */
@Composable
private fun ActionMenuItem(
    icon: @Composable () -> Unit,
    label: String,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (subtitle != null) "$label $subtitle" else label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column {
            Text(
                label,
                style = TextStyle(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (danger) Red400 else colors.textPrimary,
                ),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (danger) Red400.copy(alpha = 0.75f) else colors.textMuted,
                    ),
                )
            }
        }
    }
}
