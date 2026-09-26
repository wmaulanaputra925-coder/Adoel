package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.data.DEFAULT_CORAK_SHORTCUTS
import com.jekael.adoel.data.DEFAULT_KETERANGAN_SHORTCUTS
import com.jekael.adoel.ui.theme.*

/**
 * Reusable shortcut chip selector and quick inline adder for Corak and Keterangan.
 * Aligns 1:1 with Web's ShortcutPicker component.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaseShortcutPicker(
    value: String,
    onSelect: (String) -> Unit,
    shortcuts: List<String>,
    onAddShortcut: (String) -> Unit,
    itemTypeLabel: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    showToast: ((String) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    var isAdding by remember { mutableStateOf(false) }
    var inlineInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val currentTrimmed = remember(value) { value.trim().uppercase() }
    val canSaveCurrent = remember(currentTrimmed, shortcuts) {
        currentTrimmed.isNotEmpty() && shortcuts.none { it.equals(currentTrimmed, ignoreCase = true) }
    }

    fun handleSaveCurrent() {
        if (currentTrimmed.isEmpty()) return
        onAddShortcut(currentTrimmed)
        showToast?.invoke("$itemTypeLabel \"$currentTrimmed\" ditambahkan ke shortcut ✓")
    }

    fun handleAddInline() {
        val trimmed = inlineInput.trim().uppercase()
        if (trimmed.isEmpty()) return
        if (shortcuts.any { it.equals(trimmed, ignoreCase = true) }) {
            showToast?.invoke("$itemTypeLabel \"$trimmed\" sudah ada di shortcut")
        } else {
            onAddShortcut(trimmed)
            showToast?.invoke("$itemTypeLabel \"$trimmed\" ditambahkan ke shortcut ✓")
        }
        onSelect(trimmed)
        inlineInput = ""
        isAdding = false
    }

    // Same autoFocus web's inline <input> gets when the editor opens.
    LaunchedEffect(isAdding) {
        if (isAdding) focusRequester.requestFocus()
    }

    // Everything — chips, the "save current" button, the add-trigger/editor — lives in one
    // FlowRow, same as web's single flexbox (ShortcutPicker.tsx): the inline editor used to break
    // out into its own full-width bar underneath once isAdding flipped, sized like a distinct
    // "add new item" panel (56dp-tall OutlinedTextField, 40dp button) instead of sitting inline as
    // just another compact, chip-sized item that wraps together with everything else.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier.fillMaxWidth().padding(top = Dimens.Space10),
    ) {
        // Existing shortcut chips — tinted+bordered when active, neutral+bordered otherwise
        // (matches the meta-tag/corak-chip-filter pill language used everywhere else now: a pill
        // always carries its own border, not just a flat fill, active or not).
        shortcuts.forEach { code ->
            val isActive = currentTrimmed.equals(code, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isActive) Cyan500.copy(alpha = 0.16f) else colors.bgElevated)
                    .border(1.dp, if (isActive) Cyan400.copy(alpha = 0.45f) else colors.border, RoundedCornerShape(6.dp))
                    .clickable { onSelect(code) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code,
                    style = AppType.Caption.copy(
                        color = if (isActive) Cyan400 else colors.textSecondary,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.5.sp,
                    ),
                )
            }
        }

        // Quick save current value button
        if (canSaveCurrent) {
            Surface(
                onClick = { handleSaveCurrent() },
                shape = RoundedCornerShape(6.dp),
                color = Cyan600.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Cyan600.copy(alpha = 0.35f)),
                modifier = Modifier.height(28.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Simpan \"$currentTrimmed\" ke Shortcut",
                        style = AppType.Caption.copy(
                            color = Cyan400,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }

        if (isAdding) {
            // Compact inline editor — one wrapping item in the same flow, not a separate bar.
            // Field width mirrors web's own 90px (Corak) / 128px (Keterangan, longer text like
            // "GANTI BEAM") split.
            val fieldWidth = if (itemTypeLabel == "Corak") 84.dp else 114.dp
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.bgElevated2)
                    .border(1.dp, Cyan400, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.width(fieldWidth), contentAlignment = Alignment.CenterStart) {
                    if (inlineInput.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = AppType.Caption.copy(color = colors.textFaint, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = inlineInput,
                        onValueChange = { inlineInput = it.uppercase() },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle = AppType.Caption.copy(color = colors.textPrimary, fontSize = 12.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(Cyan400),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { handleAddInline() }),
                    )
                }
                Surface(
                    onClick = { handleAddInline() },
                    shape = RoundedCornerShape(4.dp),
                    color = if (inlineInput.isNotBlank()) Cyan600 else colors.bgElevated1,
                ) {
                    Text(
                        text = "+ OK",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        style = AppType.Caption.copy(
                            color = if (inlineInput.isNotBlank()) Color.White else colors.textFaint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                    )
                }
                Text(
                    text = "✕",
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isAdding = false }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    style = AppType.Caption.copy(color = colors.textFaint, fontSize = 12.sp),
                )
            }
        } else {
            // Inline Add Trigger Button
            Surface(
                onClick = { isAdding = true },
                shape = RoundedCornerShape(6.dp),
                color = colors.bgElevated1,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.height(28.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Tambah $itemTypeLabel",
                        style = AppType.Caption.copy(
                            color = colors.textMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun CorakShortcutPicker(
    value: String,
    onSelect: (String) -> Unit,
    shortcuts: List<String>?,
    onAddShortcut: (String) -> Unit,
    modifier: Modifier = Modifier,
    showToast: ((String) -> Unit)? = null,
) {
    BaseShortcutPicker(
        value = value,
        onSelect = onSelect,
        shortcuts = shortcuts ?: DEFAULT_CORAK_SHORTCUTS,
        onAddShortcut = onAddShortcut,
        itemTypeLabel = "Corak",
        placeholder = "Cth: 4520",
        modifier = modifier,
        showToast = showToast,
    )
}

@Composable
fun KeteranganShortcutPicker(
    value: String,
    onSelect: (String) -> Unit,
    shortcuts: List<String>?,
    onAddShortcut: (String) -> Unit,
    modifier: Modifier = Modifier,
    showToast: ((String) -> Unit)? = null,
) {
    BaseShortcutPicker(
        value = value,
        onSelect = onSelect,
        shortcuts = shortcuts ?: DEFAULT_KETERANGAN_SHORTCUTS,
        onAddShortcut = onAddShortcut,
        itemTypeLabel = "Keterangan",
        placeholder = "Cth: GANTI BEAM",
        modifier = modifier,
        showToast = showToast,
    )
}
