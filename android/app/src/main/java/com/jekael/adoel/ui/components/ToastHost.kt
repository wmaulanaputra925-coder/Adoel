package com.jekael.adoel.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.*
import com.jekael.adoel.viewmodel.ToastState
import kotlinx.coroutines.delay

@Composable
fun ToastHost(
    toast: ToastState?,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    LaunchedEffect(toast?.key) {
        if (toast != null) {
            delay(Motion.TOAST_VISIBLE_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        toast?.let {
            // Pesan gagal selalu diawali "⚠" (lihat flashError di MainScreenHandlers) — beri
            // cincin merah supaya perintah yang ditolak tidak terbaca persis sama dengan
            // konfirmasi berhasil kalau operator hanya melirik sekilas.
            val isError = it.msg.startsWith("⚠")
            val shape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Space16, vertical = Dimens.Space12),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .shadow(elevation = 10.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.4f))
                        .background(
                            if (isError) lerp(colors.bgElevated2, Red500, 0.10f) else colors.bgElevated2,
                            shape,
                        )
                        .then(
                            if (isError) Modifier.border(1.dp, Red500.copy(alpha = 0.45f), shape) else Modifier
                        )
                        .padding(horizontal = Dimens.Space20, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
                ) {
                    Text(
                        text = it.msg,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    // Every toast gets a plain close button now — no more per-toast "Undo" action,
                    // since Undo/Redo moved to their own permanent buttons in ConsoleBar (Master
                    // Blueprint v9.2 §9).
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                    ) {
                        CloseIcon()
                    }
                }
            }
        }
    }
}
