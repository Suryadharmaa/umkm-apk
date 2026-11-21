package id.umkmku.pos.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.umkmku.pos.data.qrisBitmap
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.QrisAsset
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.QrisProfile
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CashCommand
import id.umkmku.pos.feature.CashRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun QrisProfileScreen(
    active: CashRead<QrisProfile?>,
    preview: QrisAsset?,
    command: CashCommand,
    onRetry: () -> Unit,
    onImport: (Uri) -> Unit,
    onPayload: (String) -> Unit,
    onActivate: (QrisAsset, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val focus = LocalFocusManager.current
    // The imported code and any entered references stay out of the saved navigation bundle.
    var payload by remember { mutableStateOf("") }
    var attested by remember(preview) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImport(uri)
    }
    QrisColumn {
        when (active) {
            is CashRead.Ready -> Panel {
                Text("Profil QRIS aktif", style = MaterialTheme.typography.titleMedium)
                val asset = active.value?.asset
                if (asset == null) {
                    Text("Belum ada profil.")
                } else {
                    MerchantCaption(asset)
                    TextButton(onDeactivate, enabled = !command.busy) { Text("Nonaktifkan profil") }
                }
            }
            else -> CashReadStatus(active, onRetry)
        }
        if (preview == null) {
            Text("Tambah atau ganti profil dari QRIS statis yang diterbitkan penyedia pembayaran merchant.")
            Button({ focus.clearFocus(); picker.launch("image/*") }, enabled = !command.busy) { Text("Pilih gambar QRIS") }
            OutlinedTextField(
                payload, { payload = it.take(2049) }, Modifier.fillMaxWidth(),
                enabled = !command.busy, maxLines = 3, label = { Text("Payload QRIS") },
            )
            TextButton({ focus.clearFocus(); onPayload(payload) }, enabled = payload.isNotBlank() && !command.busy) {
                Text("Periksa payload")
            }
        } else {
            Panel {
                MerchantCaption(preview)
                QrisImage(preview)
            }
            CheckStatement("Saya sudah mencocokkan profil ini dengan QRIS resmi dari penyedia pembayaran merchant.",
                attested, !command.busy) { attested = it }
            Button(
                { onActivate(preview, attested); payload = "" },
                enabled = attested && !command.busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
            ) { Text("Aktifkan profil") }
            TextButton(onCancel, enabled = !command.busy) { Text("Batal impor") }
        }
        CashCommandError(command)
    }
}

@Composable
internal fun QrisStartScreen(
    draft: CashDraft,
    active: CashRead<QrisProfile?>,
    command: CashCommand,
    onRetry: () -> Unit,
    onBegin: (String) -> Unit,
    onProfile: () -> Unit,
    onAcceptPrices: (CashDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    QrisColumn {
        QrisTotal(draft.total)
        when (active) {
            is CashRead.Ready -> {
                val profile = active.value
                if (profile != null) {
                    Panel { MerchantCaption(profile.asset) }
                    Button(
                        { onBegin(profile.id) }, enabled = !command.busy && command.priceReview == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
                    ) { Text("Tampilkan QRIS") }
                } else {
                    MessagePanel("Profil QRIS belum aktif", "Impor QRIS statis merchant untuk menerima pembayaran.",
                        "Buka profil QRIS", onProfile)
                }
            }
            else -> CashReadStatus(active, onRetry)
        }
        CashCommandError(command)
    }
    PriceReviewDialog(draft, command.priceReview, onAcceptPrices, onDismiss)
}

@Composable
internal fun QrisPendingScreen(
    draft: CashDraft,
    profile: QrisProfile,
    onConfirm: () -> Unit,
    onNewSale: () -> Unit,
) {
    QrisColumn {
        QrisTotal(draft.total)
        Panel {
            Text("Menunggu pembayaran QRIS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            MerchantCaption(profile.asset)
            QrisImage(profile.asset)
            Text("Minta pembeli memindai QR dan memasukkan nominal tagihan.")
        }
        Button(onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
            Text("Periksa bukti merchant")
        }
        TextButton(onNewSale) { Text("Kembali ke kasir") }
    }
}

@Composable
internal fun QrisConfirmationScreen(
    draft: CashDraft,
    profile: QrisProfile,
    command: CashCommand,
    onConfirm: (QrisEvidenceInput) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var matched by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    QrisColumn {
        QrisTotal(draft.total)
        Panel {
            MerchantCaption(profile.asset)
            Text("Buka aplikasi atau riwayat merchant. Salin nominal, nomor referensi, dan waktu dari transaksi yang sudah berhasil.")
        }
        OutlinedTextField(
            amount, { amount = it.take(20) }, Modifier.fillMaxWidth(), enabled = !command.busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Nominal diterima (Rp)") },
        )
        OutlinedTextField(
            reference, { reference = it.take(101) }, Modifier.fillMaxWidth(), enabled = !command.busy, singleLine = true,
            label = { Text("Nomor referensi merchant") },
        )
        OutlinedTextField(
            time, { time = it.take(20) }, Modifier.fillMaxWidth(), enabled = !command.busy, singleLine = true,
            label = { Text("Waktu transaksi merchant") },
            supportingText = { Text("TTTT-BB-HH JJ:MM · zona waktu perangkat") },
        )
        CheckStatement("Saya melihat pembayaran berhasil pada aplikasi penerima dan telah mencocokkan identitas merchant.",
            matched, !command.busy) { matched = it }
        CashCommandError(command)
        Button(
            { focus.clearFocus(); onConfirm(QrisEvidenceInput(amount, reference, time, matched)) },
            enabled = matched && amount.isNotBlank() && reference.isNotBlank() && time.isNotBlank() && !command.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        ) { Text(if (command.busy) "Menyimpan konfirmasi…" else "Catat konfirmasi manual") }
    }
}

@Composable
private fun QrisColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) { content() }
}

@Composable
private fun MerchantCaption(asset: QrisAsset) {
    Text(asset.merchantName, style = MaterialTheme.typography.titleMedium)
    Text("NMID • ${asset.merchantId}")
}

@Composable
private fun CheckStatement(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touch)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun QrisTotal(amount: Long) {
    Panel {
        Text("Total tagihan", style = MaterialTheme.typography.labelLarge)
        Text(rupiah(amount), style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun QrisImage(asset: QrisAsset) {
    val bitmap by produceState<Bitmap?>(null, asset) {
        value = withContext(Dispatchers.Default) { qrisBitmap(asset) }
    }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), "Kode QRIS merchant", Modifier.fillMaxWidth().heightIn(max = 244.dp)
            .padding(vertical = Space.md))
    } else {
        Text("Memuat gambar QRIS…")
    }
}
