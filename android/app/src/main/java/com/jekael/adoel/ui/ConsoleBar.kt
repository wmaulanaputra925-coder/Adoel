package com.jekael.adoel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.*

/** Floating command bar — a single machine-number field flanked by Undo/Redo on the left and the
 * two guided actions (Estimasi, Doffing) on the right (Master Blueprint v9.2 §1/§7):
 * `[Undo][Redo]  [nomor mesin]  [Estimasi][Doffing]`. No more single "Mulai" button that then asks
 * which action was meant — the operator picks the action by which icon they tap, one motion
 * instead of two. No bulk multi-machine entry either (that moved to Pengaturan > Mesin's own
 * console, one machine at a time — see MesinTab). Reports its own measured height via
 * [onHeightMeasured] so the scrollable list behind it can pad itself to avoid sitting under it. */
@Composable
internal fun ConsoleBar(
    onEstimasiClick: (mcNo: String) -> Unit,
    onDoffingClick: (mcNo: String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onHeightMeasured: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    var mcNoInput by remember { mutableStateOf("") }

    fun submit(action: (String) -> Unit) {
        if (mcNoInput.isNotBlank()) {
            action(mcNoInput)
            mcNoInput = ""
        }
    }

    Box(
        modifier = modifier
            .imePadding()
            .onGloballyPositioned { coords ->
                onHeightMeasured(with(density) { coords.size.height.toDp() })
            }
            .padding(horizontal = Dimens.Space12)
            .padding(bottom = Dimens.Space12)
            .floatingHeaderCard(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space12)
                .padding(top = 10.dp)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                // Centered as a group (not spread with weight()) — a bare Undo/Redo/field/
                // Estimasi/Doffing row left-aligned with the field stretched to fill the rest
                // left a lot of dead padding around whatever 1-3 digits were actually typed,
                // reading as oversized for what the field is for. Fixed-width field, whole
                // group centered, matches how full the bar visually looks now.
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleIconButton(
                    icon = Icons.Outlined.Undo,
                    contentDescription = "Undo",
                    enabled = canUndo,
                    // Amber — the app icon's own accent color (the dot on the "A."), already this
                    // bar's own accent (the text field's focus/cursor color), so Undo/Redo read as
                    // a pair native to the console itself, distinct from Estimasi's Cyan and
                    // Doffing's Emerald.
                    accent = Amber500,
                    onClick = onUndo,
                )
                ConsoleIconButton(
                    icon = Icons.Outlined.Redo,
                    contentDescription = "Redo",
                    enabled = canRedo,
                    accent = Amber500,
                    onClick = onRedo,
                )
                OutlinedTextField(
                    value = mcNoInput,
                    onValueChange = { mcNoInput = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.width(96.dp),
                    // Shortened from "Nomor mesin" — doesn't fit this field's new, deliberately
                    // compact width, and singleLine would otherwise just clip it mid-word.
                    placeholder = { Text("No. Mc", color = colors.textFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber500,
                        unfocusedBorderColor = colors.border,
                        cursorColor = Amber500,
                        focusedContainerColor = colors.bgElevated2,
                        unfocusedContainerColor = colors.bgElevated2,
                    ),
                    shape = RoundedCornerShape(50.dp),
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit(onEstimasiClick) }),
                    singleLine = true,
                )
                ConsoleIconButton(
                    icon = Icons.Outlined.Schedule,
                    contentDescription = "Estimasi",
                    enabled = mcNoInput.isNotBlank(),
                    accent = Cyan600,
                    onClick = { submit(onEstimasiClick) },
                )
                ConsoleIconButton(
                    icon = Icons.Outlined.ContentCut,
                    contentDescription = "Doffing",
                    enabled = mcNoInput.isNotBlank(),
                    accent = Emerald500,
                    onClick = { submit(onDoffingClick) },
                )
            }
        }
    }
}

@Composable
private fun ConsoleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            disabledContainerColor = colors.bgElevated2,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else colors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}
