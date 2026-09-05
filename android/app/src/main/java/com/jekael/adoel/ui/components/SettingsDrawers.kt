package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jekael.adoel.data.*
import com.jekael.adoel.ui.theme.*

/**
 * Shared slide-in-from-right shell for [MesinDrawer] and [PengaturanDrawer] — a floating header
 * (title + close button) overlaying a full-bleed scrollable body, with swipe-right-to-dismiss.
 * Split out of what used to be one combined drawer with a Mesin/Data tab toggle, matching the
 * web app's "Daftar Mesin" and "Pengaturan" as two separate screens (opened from two separate
 * menu items) rather than tabs inside one.
 */
@Composable
private fun SlideOverPanel(
    onClose: () -> Unit,
    title: String,
    content: @Composable (headerHeight: Dp) -> Unit,
) {
    val colors = LocalAppColors.current

    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    SlidePanel(onClose = onClose) { requestClose ->
        // Box, not Column: the header floats as an overlay on top of the content (which is laid
        // out full-size from the very top) so the content actually scrolls behind it, matching
        // MainScreen's header/console concept instead of just sitting above it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                // Swipe-right-to-dismiss, shared with Statistik so all three full-screen pages
                // close with the same motion (see swipeRightToClose). Separate from SlidePanel's
                // own enter/exit transition so the two animation systems never fight over the
                // same value.
                .swipeRightToClose(onClose),
            // No systemBarsPadding() here — this panel now lives inside MainScreen's own root
            // Box, which already insets its children from the system bars once.
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.Space20),
            ) {
                content(headerHeight)
            }

            // Owned here rather than by each tab: every panel content scrolls behind the header
            // below, so the fade belongs to the panel, not to whichever tab happens to be in it.
            // Bottom fades stay with whatever floats there — MesinTab's search console draws its
            // own; Pengaturan has nothing at the bottom to fade behind.
            EdgeFadeScrim(atTop = true, height = 10.dp + headerHeight + Dimens.Space16)

            // Header — floating overlay, matching the header/console bar's look (tonal
            // background + a subtle border — see floatingHeaderCard's doc for why this isn't a
            // shadow).
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        headerHeight = with(density) { coords.size.height.toDp() }
                    }
                    .padding(horizontal = Dimens.Space12)
                    .padding(top = Dimens.Space12)
                    .floatingHeaderCard(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Dimens.Space20, end = Dimens.Space8, top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = AppType.DialogTitle.copy(color = colors.textPrimary))
                    IconButton(onClick = { requestClose() }) {
                        CloseIcon()
                    }
                }
            }
        }
    }
}

@Composable
internal fun MesinDrawer(
    state: DoffState,
    onClose: () -> Unit,
    onSetMesin: (String, MesinData) -> Unit,
    onResetMesin: (String) -> Unit,
    onAddCorakShortcut: (String) -> Unit = {},
    showToast: (String) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
) {
    SlideOverPanel(onClose = onClose, title = "Daftar Mesin") { headerHeight ->
        MesinTab(
            state = state,
            headerHeight = headerHeight,
            onSetMesin = onSetMesin,
            onResetMesin = onResetMesin,
            showToast = showToast,
            showConfirm = showConfirm,
            onAddCorakShortcut = onAddCorakShortcut,
        )
    }
}

@Composable
internal fun PengaturanDrawer(
    state: DoffState,
    onClose: () -> Unit,
    onResetDb: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onExportJson: () -> String,
    onImport: (String) -> Unit,
    onAddKeteranganShortcut: (String) -> Unit = {},
    onRemoveKeteranganShortcut: (String) -> Unit = {},
    onResetKeteranganShortcuts: () -> Unit = {},
    onAddCorakShortcut: (String) -> Unit = {},
    onRemoveCorakShortcut: (String) -> Unit = {},
    onResetCorakShortcuts: () -> Unit = {},
    onAddCorakPotonganAwal: (String) -> Unit = {},
    onRemoveCorakPotonganAwal: (String) -> Unit = {},
    onResetCorakPotonganAwal: () -> Unit = {},
    showToast: (String) -> Unit,
    showConfirm: (String, () -> Unit) -> Unit,
) {
    var helpOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }

    SlideOverPanel(onClose = onClose, title = "Pengaturan") { headerHeight ->
        DataTab(
            state = state,
            headerHeight = headerHeight,
            onResetDb = onResetDb,
            onSetThemeMode = onSetThemeMode,
            onExportJson = onExportJson,
            onImport = onImport,
            onAddKeteranganShortcut = onAddKeteranganShortcut,
            onRemoveKeteranganShortcut = onRemoveKeteranganShortcut,
            onResetKeteranganShortcuts = onResetKeteranganShortcuts,
            onAddCorakShortcut = onAddCorakShortcut,
            onRemoveCorakShortcut = onRemoveCorakShortcut,
            onResetCorakShortcuts = onResetCorakShortcuts,
            onAddCorakPotonganAwal = onAddCorakPotonganAwal,
            onRemoveCorakPotonganAwal = onRemoveCorakPotonganAwal,
            onResetCorakPotonganAwal = onResetCorakPotonganAwal,
            onOpenHelp = { helpOpen = true },
            onOpenAbout = { aboutOpen = true },
            showToast = showToast,
            showConfirm = showConfirm,
        )
    }

    if (aboutOpen) {
        AboutDialog(onClose = { aboutOpen = false })
    }
    if (helpOpen) {
        OnboardingDialog(onClose = { helpOpen = false })
    }
}
