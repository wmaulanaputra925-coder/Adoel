package com.jekael.adoel.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.jekael.adoel.BarcodeScanActivity
import com.jekael.adoel.data.DoffRepository
import com.jekael.adoel.data.DoffState
import com.jekael.adoel.data.currentShiftStartAbsMin
import com.jekael.adoel.data.nowAbsMin
import com.jekael.adoel.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SyncDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val clipboardManager = LocalClipboardManager.current
    val repository = remember(context) { DoffRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val doffState by repository.observeState().collectAsState(initial = DoffState())

    // 0 = Terima / Scan, 1 = Kirim
    var tabIndex by remember { mutableStateOf(0) }
    var qrType by remember { mutableStateOf("HANDOVER") } // "HANDOVER" or "MASTER_DB"
    var dbScope by remember { mutableStateOf("CUSTOMIZED_ONLY") } // "CUSTOMIZED_ONLY", "RANGE_1_30", "RANGE_31_60"

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawQrString by remember { mutableStateOf("") }
    var pastedText by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }

    // Same "customized" test prepareMasterDbData() uses to decide what CUSTOMIZED_ONLY includes,
    // mirrored here purely for the chip-label counts (web shows these same counts on its chips).
    val customizedCount = remember(doffState.db) {
        doffState.db.values.count { m ->
            (m.corak.trim().isNotEmpty() && m.corak != "-") ||
                m.targetYard != null || m.speed != null || (m.koreksi != null && m.koreksi != 0.0)
        }
    }
    val nextShiftCount = remember(doffState.estimasi) {
        val shiftEndAbs = currentShiftStartAbsMin(nowAbsMin()) + 480L
        doffState.estimasi.values.count { it.estAbsMin > shiftEndAbs }
    }
    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(2000)
            isCopied = false
        }
    }

    // Update QR Bitmap whenever Kirim tab, qrType, or dbScope changes
    LaunchedEffect(tabIndex, qrType, dbScope) {
        if (tabIndex == 1) {
            withContext(Dispatchers.IO) {
                val raw = if (qrType == "HANDOVER") {
                    repository.prepareHandoverData()
                } else {
                    repository.prepareMasterDbData(dbScope)
                }
                rawQrString = raw
                qrBitmap = createQrBitmap(raw)
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        scope.launch {
            val (imported, msg) = withContext(Dispatchers.IO) {
                repository.processScannedQr(contents, context)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (imported != null) {
                onClose()
            }
        }
    }

    fun handleProcessText(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val (imported, msg) = withContext(Dispatchers.IO) {
                repository.processScannedQr(trimmed, context)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (imported != null) {
                onClose()
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) { decodeQrFromUri(context, uri) }
            if (decoded != null) {
                handleProcessText(decoded)
            } else {
                Toast.makeText(context, "⚠ QR Code tidak terdeteksi pada gambar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    FloatingEditDialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Cyan500.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "QR Sync Mesin",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        )
                        Text(
                            text = "Sinkronisasi data mesin & estimasi",
                            style = TextStyle(fontSize = 11.sp, color = colors.textMuted),
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = colors.textSecondary,
                    )
                }
            }

            // Tab switcher: Terima vs Kirim
            SlidingToggle(
                labelLeft = "Terima / Scan",
                labelRight = "Kirim",
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
                containerColor = colors.bgElevated2,
                activeColorLeft = Cyan600,
                activeColorRight = Cyan600,
                activeTextColorLeft = Color.White,
                activeTextColorRight = Color.White,
                inactiveTextColor = colors.textMuted,
                modifier = Modifier.fillMaxWidth(),
                accessibilityLabel = "Mode QR Sync",
            )

            if (tabIndex == 1) {
                // KIRIM MODE
                Text(
                    text = "Pilih data yang ingin dikirim:",
                    style = TextStyle(fontSize = 12.sp, color = colors.textMuted),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                ) {
                    ChipBtn(
                        label = "Oper Shift ($nextShiftCount Mc)",
                        selected = qrType == "HANDOVER",
                        onClick = { qrType = "HANDOVER" },
                        modifier = Modifier.weight(1f),
                    )
                    ChipBtn(
                        label = "Daftar Mesin (${if (customizedCount > 0) "$customizedCount Terisi" else "60 Mc"})",
                        selected = qrType == "MASTER_DB",
                        onClick = { qrType = "MASTER_DB" },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (qrType == "MASTER_DB") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgElevated2, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
                    ) {
                        ChipBtn(
                            label = "Mesin Terisi (${if (customizedCount > 0) customizedCount else 30})",
                            selected = dbScope == "CUSTOMIZED_ONLY",
                            onClick = { dbScope = "CUSTOMIZED_ONLY" },
                            modifier = Modifier.weight(1f),
                        )
                        ChipBtn(
                            label = "Mc 01–30 (P1)",
                            selected = dbScope == "RANGE_1_30",
                            onClick = { dbScope = "RANGE_1_30" },
                            modifier = Modifier.weight(1f),
                        )
                        ChipBtn(
                            label = "Mc 31–60 (P2)",
                            selected = dbScope == "RANGE_31_60",
                            onClick = { dbScope = "RANGE_31_60" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgElevated2, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (qrType == "HANDOVER") {
                            "💡 Oper Shift Berikutnya: Hanya membagikan data estimasi untuk shift berikutnya dalam format super ringkas."
                        } else {
                            "💡 QR Ringkas & Renggang: Kode QR dioptimalkan agar modul titik besar dan mudah di-scan kamera ponsel. Gunakan P1 / P2 jika ingin membagi data mesin menjadi 2 bagian."
                        },
                        style = TextStyle(fontSize = 11.sp, color = colors.textSecondary, lineHeight = 15.sp),
                    )
                }

                // QR Preview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(Dimens.RadiusControl))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR Code Sync",
                            modifier = Modifier.size(220.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Menyiapkan QR...", style = TextStyle(color = Color(0xFF666666), fontSize = 12.sp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                ) {
                    Button(
                        onClick = {
                            if (rawQrString.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(rawQrString))
                                isCopied = true
                                Toast.makeText(context, "Data QR disalin ke clipboard ✓", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(Dimens.RadiusControl),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.bgElevated2,
                            contentColor = colors.textPrimary,
                        ),
                    ) {
                        Text(if (isCopied) "Tersalin ✓" else "Salin Data Teks")
                    }

                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(Dimens.RadiusControl),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                    ) {
                        Text("Selesai")
                    }
                }
            } else {
                // TERIMA / SCAN MODE
                Text(
                    text = "Arahkan kamera ke kode QR perangkat lain atau tempel teks datanya:",
                    style = TextStyle(fontSize = 12.sp, color = colors.textMuted),
                )

                Button(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setCaptureActivity(BarcodeScanActivity::class.java)
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Arahkan ke QR Sync Adoel")
                                setBeepEnabled(false)
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Dimens.Space8))
                    Text("Buka Kamera Pemindai")
                }

                // ATAU separator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.border),
                    )
                    Text(
                        text = "ATAU",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textMuted),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.border),
                    )
                }

                // Pick a QR image from the gallery — no runtime permission needed, the
                // system Photo Picker (or its pre-API-33 fallback) handles file access itself.
                Button(
                    onClick = {
                        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, colors.border, RoundedCornerShape(Dimens.RadiusControl)),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.bgElevated2,
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Dimens.Space8))
                    Text("Pilih Gambar QR dari File")
                }

                // Text paste input
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space6)) {
                    Text(
                        text = "Masukkan / Tempel Teks QR Sync:",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            placeholder = { Text("Tempel data JSON/teks QR...", style = TextStyle(fontSize = 13.sp, color = colors.textMuted)) },
                            singleLine = true,
                            colors = outlinedFieldColors(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary),
                        )

                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    pastedText = clip.trim()
                                    Toast.makeText(context, "Teks ditempel dari clipboard ✓", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.bgElevated2, RoundedCornerShape(8.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Tempel Clipboard",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Button(
                            onClick = { handleProcessText(pastedText) },
                            enabled = pastedText.isNotBlank(),
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                        ) {
                            Text("Impor")
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.Space8))

                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.bgElevated2,
                        contentColor = colors.textSecondary,
                    ),
                ) {
                    Text("Batal")
                }
            }
        }
    }
}

private fun createQrBitmap(data: String): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 2,
    )
    BarcodeEncoder().encodeBitmap(data, BarcodeFormat.QR_CODE, 600, 600, hints)
}.getOrNull()

/** Decodes a QR code out of a gallery-picked image, mirroring web's upload-a-QR-photo path
 * (jsQR over a canvas there; ZXing's own MultiFormatReader here — no extra dependency needed,
 * it already ships inside zxing-android-embedded, which the camera scanner above uses to encode). */
private fun decodeQrFromUri(context: Context, uri: Uri): String? = runCatching {
    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val source = RGBLuminanceSource(width, height, pixels)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    MultiFormatReader().decode(binaryBitmap).text
}.getOrNull()
