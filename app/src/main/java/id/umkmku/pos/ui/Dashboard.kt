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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    val colors = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().background(colors.background).testTag("dashboard")) {
        item {
            Column(
                Modifier.fillMaxWidth().background(colors.primary)
                    .padding(start = Space.page, end = Space.page, top = Space.md, bottom = Space.sm),
            ) {
                Text("UMKM-KU", style = MaterialTheme.typography.labelSmall, color = colors.primaryContainer)
                Text("Beranda", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, color = colors.onPrimary)
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    drawRect(colors.primary, size = Size(size.width, 28.dp.toPx()))
                }.padding(horizontal = Space.page),
            ) {
                Panel(contentPadding = Space.md) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text("Ringkasan katalog", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        when (state) {
                            is CatalogState.Ready -> if (state.catalog.products.isEmpty()) {
                                Text("Belum ada produk", style = MaterialTheme.typography.titleMedium)
                                Text("Tambahkan produk untuk mulai berjualan.", style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant)
                            } else {
                                CatalogOverview(state)
                            }
                            else -> MerchantCatalogStatus(state, onRetry)
                        }
                        HorizontalDivider(color = colors.surfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            MerchantPill("Buka kasir", onCashier, icon = Icons.Default.ShoppingCart)
                            MerchantPill("Kelola katalog", onCatalog, icon = Icons.AutoMirrored.Filled.List, primary = false)
                        }
                    }
                }
            }
        }
        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page, top = Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                MerchantSection("Transaksi terbaru")
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
        val catalog = (state as? CatalogState.Ready)?.catalog
        if (catalog != null && catalog.categories.isNotEmpty()) {
            item {
                val counts = catalog.products.groupingBy { it.categoryId }.eachCount()
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.sm),
                    shape = MerchantStyle.panel,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ) {
                    Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text("Produk per kategori", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        catalog.categories.take(3).forEach { category ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                                Text(category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${counts[category.id] ?: 0} produk", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (catalog.categories.size > 3) {
                            Text("+${catalog.categories.size - 3} kategori lainnya", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogOverview(state: CatalogState.Ready) {
    val products = state.catalog.products.size
    val categories = state.catalog.categories.size
    if (LocalDensity.current.fontScale >= 1.6f) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            OverviewCount(products, "Produk aktif", Icons.Default.ShoppingCart)
            OverviewCount(categories, "Kategori aktif", Icons.AutoMirrored.Filled.List)
        }
    } else {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, MerchantStyle.slot),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            OverviewCount(products, "Produk aktif", Icons.Default.ShoppingCart, Modifier.weight(1f))
            OverviewCount(categories, "Kategori aktif", Icons.AutoMirrored.Filled.List, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewCount(count: Int, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.padding(Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = colors.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
        }
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
    val colors = MaterialTheme.colorScheme
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
    val largeText = LocalDensity.current.fontScale >= 1.6f
    Panel(
        Modifier.merchantFocus(MerchantStyle.panel, colors.primary)
            .clickable(onClickLabel = "Buka transaksi", onClick = onOpen),
        contentPadding = Space.md,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top) {
            if (!largeText) {
                Box(
                    Modifier.size(36.dp).background(colors.primaryContainer, MerchantStyle.slot),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp), tint = colors.primary) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (largeText) {
                    Text(summary, style = MaterialTheme.typography.titleMedium)
                    MerchantMoney(rupiah(sale.draft.total))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        MerchantMoney(rupiah(sale.draft.total))
                    }
                }
                Text("$method · ${formatSaleTime(openedAt)}", style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant)
                MerchantStatusChip(badge)
            }
        }
    }
}
