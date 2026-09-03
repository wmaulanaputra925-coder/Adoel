package com.jekael.adoel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jekael.adoel.ui.theme.*
import com.jekael.adoel.viewmodel.ConfirmState
import kotlinx.coroutines.delay

/**
 * Custom-animated replacement for a plain [AlertDialog] — matches the fade/scale motion language
 * used by [FloatingEditDialog] and the slide-in panels, instead of the platform's default dialog
 * transition. Keeps rendering the last non-null [confirm] content while it fades/scales out, since
 * the caller clears `confirm` back to null the instant Ya/Batal is tapped — without this, the exit
 * animation would have nothing left to animate.
 */
@Composable
fun ConfirmDialog(
    confirm: ConfirmState?,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(confirm) }
    var visible by remember { mutableStateOf(confirm != null) }

    LaunchedEffect(confirm) {
        if (confirm != null) {
            shown = confirm
            visible = true
        }
    }

    val current = shown ?: return
    val colors = LocalAppColors.current

    fun requestClose(action: () -> Unit) {
        visible = false
        action()
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(Motion.DIALOG_DISMISS_MS.toLong())
            shown = null
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { requestClose { current.onCancel?.invoke() } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DisableDialogWindowAnimation()

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { requestClose { current.onCancel?.invoke() } },
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.92f, animationSpec = tween(220)) + fadeIn(tween(200)),
                exit = scaleOut(targetScale = 0.95f, animationSpec = tween(160)) + fadeOut(tween(140)),
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .background(colors.bgElevated)
                        .padding(Dimens.Space20),
                ) {
                    Text(
                        text = "Konfirmasi",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    )
                    Spacer(Modifier.height(Dimens.Space12))
                    Text(
                        text = current.msg,
                        style = TextStyle(fontSize = 14.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.height(Dimens.Space20))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { requestClose { current.onCancel?.invoke() } },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
                        ) {
                            Text("Batal")
                        }
                        TextButton(
                            onClick = { requestClose { current.onConfirm() } },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(Dimens.RadiusControl),
                            colors = ButtonDefaults.textButtonColors(contentColor = Red400),
                        ) {
                            Text("Ya", style = TextStyle(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
