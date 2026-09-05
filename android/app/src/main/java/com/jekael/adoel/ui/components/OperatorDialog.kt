package com.jekael.adoel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jekael.adoel.ui.theme.AppType
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
        Text(
            text = if (isFirstLaunch) "Selamat Datang di Adoel" else "Identitas Operator",
            style = AppType.DialogTitle.copy(color = colors.textPrimary),
        )
        Spacer(Modifier.height(Dimens.Space8))
        Text(
            text = "Nama dan grup kamu dicantumkan di kepala teks laporan yang dibagikan ke WhatsApp. Bisa diubah kapan saja lewat Pengaturan.",
            style = AppType.Caption.copy(color = colors.textFaint),
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Nama Operator")
        OutlinedTextField(
            value = namaInput,
            onValueChange = { namaInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mis. Wahyu Maulana", color = colors.textFaint) },
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            textStyle = AppType.FieldText.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            singleLine = true,
        )

        Spacer(Modifier.height(Dimens.Space16))

        FieldLabel("Grup")
        OutlinedTextField(
            value = grupInput,
            onValueChange = { grupInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mis. B", color = colors.textFaint) },
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(Dimens.RadiusControl),
            textStyle = AppType.FieldText.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
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
