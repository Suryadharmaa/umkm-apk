package id.umkmku.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.CashReceipt
import id.umkmku.pos.domain.QrisReceipt
import id.umkmku.pos.domain.SaleReceipt
import id.umkmku.pos.domain.SaleSnapshot
import id.umkmku.pos.domain.parseCashTender
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CashCommand
import id.umkmku.pos.feature.CashRead
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CashCommandError(command: CashCommand) {
    if (command.error != null) Text(
        command.error,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun CashReadStatus(state: CashRead<*>, onRetry: () -> Unit) {
    when (state) {
        CashRead.Loading -> MessagePanel("Memuat penjualan…", "Membaca data di perangkat.")
        CashRead.Failed -> MessagePanel("Penjualan gagal dimuat", "Coba baca kembali data di perangkat.", "Coba lagi", onRetry)
        is CashRead.Ready -> Unit
    }
}

@Composable
internal fun CashCheckoutScreen(
    draft: CashDraft,
    command: CashCommand,
    onPay: (String) -> Unit,
    onAcceptPrices: (CashDraft) -> Unit,
    onDismissReview: () -> Unit,
) {
    var entered by rememberSaveable(draft.id) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val tender = parseCashTender(entered)
    val valid = draft.lines.isNotEmpty() && tender != null && tender >= draft.total
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Panel {
            Text("Total tagihan", style = MaterialTheme.typography.labelLarge)
            Text(rupiah(draft.total), style = MaterialTheme.typography.displaySmall)
        }
        OutlinedTextField(
            value = entered, onValueChange = { entered = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Uang diterima (Rp)") }, singleLine = true, enabled = !command.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = entered.isNotBlank() && tender == null,
            supportingText = {
                if (entered.isNotBlank() && tender == null) Text("Gunakan angka Rupiah tanpa titik atau koma.")
            },
        )
        TextButton({ entered = draft.total.toString(); focus.clearFocus() }, enabled = !command.busy && draft.total > 0L,
            modifier = Modifier.heightIn(min = Space.touch)) { Text("Uang pas") }
        if (tender != null) {
            val change = Math.subtractExact(tender, draft.total)
            Text(
                if (change >= 0) "Kembalian ${rupiah(change)}" else "Kurang ${rupiah(Math.negateExact(change))}",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        CashCommandError(command)
        Button(
            onClick = { focus.clearFocus(); onPay(entered) },
            enabled = valid && !command.busy && command.priceReview == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        ) { Text(if (command.busy) "Menyimpan pembayaran…" else "Catat pembayaran tunai") }
    }

    PriceReviewDialog(draft, command.priceReview, onAcceptPrices, onDismissReview)
}

@Composable
internal fun PriceReviewDialog(
    draft: CashDraft,
    proposal: CashDraft?,
    onAcceptPrices: (CashDraft) -> Unit,
    onDismissReview: () -> Unit,
) {
    proposal?.let { proposed ->
        AlertDialog(
            onDismissRequest = onDismissReview,
            title = { Text("Periksa perubahan produk") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    val previous = draft.lines.associateBy { it.product.id }
                    proposed.lines.filter { it != previous[it.product.id] }.forEach { line ->
                        val old = previous[line.product.id]?.product
                        Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                        if (old != null) Text("${rupiah(old.price)} → ${rupiah(line.product.price)}")
                    }
                    Text("Total ${rupiah(draft.total)} → ${rupiah(proposed.total)}")
                }
            },
            confirmButton = { TextButton({ onAcceptPrices(proposed) }) { Text("Setujui perubahan") } },
            dismissButton = { TextButton(onDismissReview) { Text("Batal") } },
        )
    }
}

@Composable
internal fun ReceiptScreen(receipt: SaleReceipt, onHistory: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Panel {
                val title = if (receipt is CashReceipt) "Pembayaran tunai tercatat" else "QRIS dikonfirmasi manual"
                Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text(rupiah(receipt.draft.total), style = MaterialTheme.typography.displaySmall)
                Text(formatSaleTime(receipt.paidAt))
                Text(receipt.draft.id, style = MaterialTheme.typography.labelSmall)
            }
        }
        items(receipt.draft.lines, key = { it.product.id }) { line ->
            Panel(contentPadding = Space.md) {
                Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                Text("${line.quantity} × ${rupiah(line.product.price)}")
                Text(rupiah(line.subtotal), color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            Panel {
                when (receipt) {
                    is CashReceipt -> {
                        ReceiptAmount("Uang diterima", receipt.tender)
                        ReceiptAmount("Kembalian", receipt.change)
                        ReceiptAmount("Kas masuk", receipt.cashMovement)
                    }
                    is QrisReceipt -> {
                        ReceiptAmount("QRIS belum cair", receipt.draft.total)
                        Text("Bukti dicatat oleh kasir dari aplikasi merchant.")
                        Text(formatSaleTime(receipt.observedAt))
                        Text("Referensi merchant: ${receipt.reference}")
                    }
                }
            }
        }
        item { TextButton(onHistory, Modifier.heightIn(min = Space.touch)) { Text("Lihat riwayat") } }
    }
}

@Composable
private fun ReceiptAmount(label: String, value: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(label, Modifier.weight(1f))
        Text(rupiah(value), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun HistoryScreen(state: CashRead<List<SaleSnapshot>>, onRetry: () -> Unit, onSale: (SaleSnapshot) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (state !is CashRead.Ready) {
            item { CashReadStatus(state, onRetry) }
        } else {
            if (state.value.isEmpty()) item { MessagePanel("Belum ada penjualan", "Pembayaran yang sedang menunggu dan yang sudah tercatat muncul di sini.") }
            items(state.value, key = { it.draft.id }) { sale ->
                Panel(Modifier.clickable { onSale(sale) }, contentPadding = Space.md) {
                    Text(rupiah(sale.draft.total), style = MaterialTheme.typography.titleMedium)
                    val lines = sale.draft.lines
                    val item = lines.first().product.name
                    val summary = if (lines.size == 1) item else "$item + ${lines.size - 1} produk"
                    Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val label = when (sale.receipt) {
                        is CashReceipt -> "Tunai"
                        is QrisReceipt -> "QRIS dikonfirmasi manual"
                        null -> "QRIS menunggu pembayaran"
                    }
                    val timestamp = sale.receipt?.paidAt ?: checkNotNull(sale.pending).startedAt
                    Text("$label · ${formatSaleTime(timestamp)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val saleTime = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))

private fun formatSaleTime(at: Long): String = saleTime.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
