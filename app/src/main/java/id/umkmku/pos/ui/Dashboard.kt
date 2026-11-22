package id.umkmku.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import id.umkmku.pos.domain.CashReceipt
import id.umkmku.pos.domain.SaleSnapshot
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CashRead
import id.umkmku.pos.feature.CatalogState

@Composable
internal fun Dashboard(
    state: CatalogState,
    onCashier: () -> Unit,
    onCatalog: () -> Unit,
    onRetry: () -> Unit,
    history: CashRead<List<SaleSnapshot>> = CashRead.Ready(emptyList()),
    onHistory: () -> Unit = {},
    onSale: (SaleSnapshot) -> Unit = {},
    onRetryHistory: () -> Unit = {},
) = MerchantTheme {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("dashboard")) {
        item {
            Column(
                Modifier.fillMaxWidth().background(MerchantStyle.header).padding(Space.page),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text("UMKM-KU", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primaryContainer)
                Text("Beranda", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, color = Color.White)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    MerchantAction("Buka kasir", onCashier, icon = Icons.Default.ShoppingCart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MerchantStyle.header))
                    TextButton(onCatalog, Modifier.heightIn(min = Space.touch),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text("Kelola katalog")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = Space.xs))
                    }
                }
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        MerchantSection("Ringkasan katalog")
                        when (state) {
                            is CatalogState.Ready -> if (state.catalog.products.isEmpty()) {
                                Text("Belum ada produk", style = MaterialTheme.typography.titleMedium)
                                Text("Tambahkan produk untuk mulai berjualan.", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                CatalogOverview(state)
                            }
                            else -> MerchantCatalogStatus(state, onRetry)
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page, top = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MerchantSection("Transaksi terbaru", Modifier.weight(1f))
                TextButton(onHistory, Modifier.heightIn(min = Space.touch)) { Text("Lihat riwayat") }
            }
        }
        when (history) {
            CashRead.Loading -> item { HomeHistoryNotice("Memuat transaksi…", "Membaca riwayat di perangkat.", loading = true) }
            CashRead.Failed -> item { HomeHistoryNotice("Riwayat gagal dimuat", "Coba baca kembali transaksi.",
                "Muat ulang transaksi", onRetryHistory) }
            is CashRead.Ready -> {
                if (history.value.isEmpty()) item {
                    HomeHistoryNotice(
                        "Belum ada transaksi", "Penjualan dan pembayaran QRIS yang menunggu akan tampil di sini.",
                        "Mulai penjualan", onCashier,
                    )
                }
                items(history.value.take(3), key = { it.draft.id }) { sale ->
                    Box(Modifier.padding(start = Space.page, end = Space.page, bottom = Space.sm)) {
                        RecentSale(sale) { onSale(sale) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogOverview(state: CatalogState.Ready) {
    if (LocalDensity.current.fontScale >= 1.6f) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            OverviewCount(state.catalog.products.size, "Produk aktif")
            OverviewCount(state.catalog.categories.size, "Kategori aktif")
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            OverviewCount(state.catalog.products.size, "Produk aktif", Modifier.weight(1f))
            OverviewCount(state.catalog.categories.size, "Kategori aktif", Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewCount(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, MerchantStyle.slot).padding(Space.md)) {
        Text(count.toString(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeHistoryNotice(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}, loading: Boolean = false) {
    Box(Modifier.padding(horizontal = Space.page, vertical = Space.xs)) {
        MerchantNotice(title, detail, action, onAction, loading)
    }
}

@Composable
private fun RecentSale(sale: SaleSnapshot, onOpen: () -> Unit) {
    val badge = when {
        sale.refundJournalId != null -> SaleBadge.REFUNDED
        sale.pending != null -> SaleBadge.PENDING
        sale.receipt is CashReceipt -> SaleBadge.CASH
        else -> SaleBadge.CONFIRMED
    }
    val method = if (sale.receipt is CashReceipt) "Tunai" else "QRIS"
    val openedAt = sale.receipt?.paidAt ?: checkNotNull(sale.pending).startedAt
    val lines = sale.draft.lines
    val name = lines.first().product.name
    val summary = if (lines.size == 1) name else "$name + ${lines.size - 1} produk"
    Panel(
        Modifier.merchantFocus(MerchantStyle.panel, MaterialTheme.colorScheme.primary)
            .clickable(onClickLabel = "Buka transaksi", onClick = onOpen),
        contentPadding = Space.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                Text("$method · ${formatSaleTime(openedAt)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (LocalDensity.current.fontScale >= 1.6f) {
                Text(summary, style = MaterialTheme.typography.titleMedium)
                MerchantMoney(rupiah(sale.draft.total))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    MerchantMoney(rupiah(sale.draft.total))
                }
            }
            MerchantStatusChip(badge)
        }
    }
}
