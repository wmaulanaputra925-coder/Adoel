package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jekael.adoel.ui.theme.AppType
import com.jekael.adoel.ui.theme.Cyan400
import com.jekael.adoel.ui.theme.Cyan500
import com.jekael.adoel.ui.theme.Cyan600
import com.jekael.adoel.ui.theme.Dimens
import com.jekael.adoel.ui.theme.LocalAppColors

/**
 * Nama operator & grupnya. Ditanyakan sekali saat aplikasi pertama kali dibuka dan bisa dibuka
 * lagi kapan saja dari Pengaturan — datanya dipakai sebagai kepala teks bagikan, supaya rekan
 * yang menerima laporan di WhatsApp langsung tahu itu laporan siapa.
 *
 * Boleh dilewati: aplikasinya tetap jalan penuh tanpa identitas, teks bagikannya saja yang tidak
 * mencantumkan baris operator. Karena itu tidak ada validasi yang memblokir — memaksa isi di
 * layar pertama hanya jadi penghalang buat operator yang cuma ingin cepat mencatat doffing.
 */
@Composable
fun OperatorDialog(
    nama: String,
    grup: String,
    onDismiss: () -> Unit,
    onSave: (nama: String, grup: String) -> Unit,
    isFirstLaunch: Boolean = false,
) {
    val colors = LocalAppColors.current
    var namaInput by remember { mutableStateOf(nama) }
    var grupInput by remember { mutableStateOf(grup) }

    FloatingEditDialog(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space10)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cyan500.copy(alpha = 0.14f))
                    .border(1.dp, Cyan500.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Outlined.Badge, contentDescription = null, tint = Cyan400, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    text = if (isFirstLaunch) "Selamat Datang di Adoel" else "Identitas Operator",
                    style = AppType.DialogTitle.copy(color = colors.textPrimary),
                )
                Text("Nama & grup kerja untuk laporan", style = TextStyle(fontSize = 11.sp, color = colors.textFaint))
            }
        }
        Spacer(Modifier.height(Dimens.Space8))
        Text(
            text = "Nama dan grup kamu dicantumkan di kepala teks laporan yang dibagikan ke WhatsApp. Bisa diubah kapan saja lewat Pengaturan.",
            style = AppType.Caption.copy(color = colors.textFaint),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Nama Operator")
        ClearableOutlinedTextField(
            value = namaInput,
            onValueChange = { namaInput = it },
            placeholder = "mis. Wahyu Maulana",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Grup")
        ClearableOutlinedTextField(
            value = grupInput,
            onValueChange = { grupInput = it },
            placeholder = "mis. B",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )

        Spacer(Modifier.height(Dimens.Space20))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                border = BorderStroke(1.dp, colors.border),
            ) { Text(if (isFirstLaunch) "Lewati" else "Batal") }
            Button(
                onClick = { onSave(namaInput.trim(), grupInput.trim()) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
            ) { Text("Simpan") }
        }
    }
}
