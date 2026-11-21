package id.umkmku.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import id.umkmku.pos.domain.CartLine
import id.umkmku.pos.domain.Catalog
import id.umkmku.pos.domain.Category
import id.umkmku.pos.domain.Product
import id.umkmku.pos.domain.cartLines
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CatalogState

@Composable
fun Dashboard(state: CatalogState, onCashier: () -> Unit, onCatalog: () -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("dashboard")) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(HeaderDeep, HeaderBlue)))
                    .padding(horizontal = Space.page, vertical = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text("UMKM-KU", style = MaterialTheme.typography.labelLarge, color = HeaderSecondary)
                Text("Beranda", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text("Kelola produk dan siapkan pesanan warung.", color = HeaderSecondary)
                Button(onClick = onCashier, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = HeaderDeep,
                ), modifier = Modifier.heightIn(min = Space.touch)) {
                    Icon(Icons.Default.ShoppingCart, null)
                    Text("Buka kasir", Modifier.padding(start = Space.sm))
                }
            }
        }
        item {
            Column(Modifier.padding(Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text("Katalog warung", style = MaterialTheme.typography.titleLarge)
                when (state) {
                    is CatalogState.Ready -> if (state.catalog.products.isEmpty()) {
                        MessagePanel("Belum ada produk", "Tambahkan produk agar bisa dipilih di kasir.", "Kelola katalog", onCatalog)
                        if (state.catalog.categories.isNotEmpty()) {
                            Text("${state.catalog.categories.size} kategori aktif", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Panel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.page)) {
                                CatalogCount(state.catalog.products.size, "Produk aktif", Modifier.weight(1f))
                                CatalogCount(state.catalog.categories.size, "Kategori aktif", Modifier.weight(1f))
                            }
                            TextButton(onClick = onCatalog, modifier = Modifier.heightIn(min = Space.touch)) {
                                Text("Kelola katalog")
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = Space.sm))
                            }
                        }
                    }
                    else -> CatalogStatus(state, onRetry)
                }
            }
        }
    }
}

@Composable
private fun CatalogCount(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(count.toString(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Cashier(
    state: CatalogState,
    quantities: Map<String, Int>,
    onQuantity: (String, Int) -> Unit,
    onCart: () -> Unit,
    onCatalog: () -> Unit,
    onRetry: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var search by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = (state as? CatalogState.Ready)?.catalog
    val filter = categoryId?.takeIf { id -> catalog?.categories?.any { it.id == id } == true }
    val lines = catalog?.let { cartLines(it, quantities) }.orEmpty()
    val products = catalog?.products.orEmpty().filter {
        (filter == null || it.categoryId == filter) && it.name.contains(search.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().testTag("cashier")) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Space.page),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (catalog == null) {
                item { CatalogStatus(state, onRetry) }
            } else if (catalog.products.isEmpty()) {
                item { MessagePanel("Belum ada produk", "Isi katalog untuk mulai membuat pesanan.", "Kelola katalog", onCatalog) }
            } else {
                item {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cari produk") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    )
                }
                if (catalog.categories.isNotEmpty()) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        FilterChip(filter == null, { categoryId = null }, label = { Text("Semua") }, modifier = Modifier.heightIn(min = Space.touch))
                        catalog.categories.forEach { category ->
                            FilterChip(filter == category.id, { categoryId = category.id },
                                label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.heightIn(min = Space.touch))
                        }
                    }
                }
                if (products.isEmpty()) item { MessagePanel("Produk tidak ditemukan", "Ubah kata kunci atau pilih kategori lain.") }
                items(products, key = { it.id }) { product ->
                    val count = quantities[product.id] ?: 0
                    Panel(contentPadding = Space.md) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(rupiah(product.price), color = MaterialTheme.colorScheme.primary)
                            }
                            QuantityStepper(product.name, count) { delta ->
                                focus.clearFocus()
                                onQuantity(product.id, delta)
                            }
                        }
                    }
                }
            }
        }
        if (lines.isNotEmpty()) CartBar(lines, onCart)
    }
}

@Composable
private fun CartBar(lines: List<CartLine>, onCart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = Space.xs) {
        Button(
            onClick = onCart,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.sm).heightIn(min = Space.touch),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.ShoppingCart, null)
            Text("${lines.sumOf { it.quantity.toLong() }} item", Modifier.weight(1f).padding(start = Space.sm))
            Text(rupiah(lines.sumOf { it.subtotal }))
        }
    }
}

@Composable
fun CartScreen(lines: List<CartLine>, onQuantity: (String, Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("cart"), contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (lines.isEmpty()) item { MessagePanel("Keranjang kosong", "Tambahkan produk dari Kasir.") }
        items(lines, key = { it.product.id }) { line ->
            Panel(contentPadding = Space.md) {
                Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                Text("${line.quantity} × ${rupiah(line.product.price)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rupiah(line.subtotal), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    QuantityStepper(line.product.name, line.quantity) { onQuantity(line.product.id, it) }
                }
            }
        }
        if (lines.isNotEmpty()) item {
            Panel {
                Text("Total keranjang", style = MaterialTheme.typography.labelLarge)
                Text(rupiah(lines.sumOf { it.subtotal }), style = MaterialTheme.typography.displaySmall)
            }
        }
    }
}

private val previewCatalog = Catalog(
    products = listOf(Product("1", "Teh manis", 5000, "drinks"), Product("2", "Nasi kuning", 15000, null)),
    categories = listOf(Category("drinks", "Minuman")),
)

@Preview(widthDp = 360, heightDp = 660, locale = "id")
@Composable
private fun DashboardPreview() {
    WarungTheme { Surface(color = MaterialTheme.colorScheme.background) { Dashboard(CatalogState.Ready(previewCatalog), {}, {}, {}) } }
}

@Preview(widthDp = 360, heightDp = 660, locale = "id")
@Composable
private fun CashierPreview() {
    WarungTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Cashier(CatalogState.Ready(previewCatalog), mapOf("1" to 1), { _, _ -> }, {}, {}, {})
        }
    }
}
