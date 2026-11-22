package id.umkmku.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import id.umkmku.pos.domain.Category
import id.umkmku.pos.domain.Product
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CatalogState
import id.umkmku.pos.feature.SaveState

@Composable
fun CatalogScreen(state: CatalogState, onRetry: () -> Unit, edit: (Boolean, String?) -> Unit) = MerchantTheme {
    var showProducts by rememberSaveable { mutableStateOf(true) }
    var productQuery by rememberSaveable { mutableStateOf("") }
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = (state as? CatalogState.Ready)?.catalog
    val categories = catalog?.categories.orEmpty()
    val names = categories.associate { it.id to it.name }
    val activeCategory = names[selectedCategory]
    val products = catalog?.products.orEmpty()
    val filteredProducts = products.filter {
        (activeCategory == null || selectedCategory == it.categoryId) && it.name.contains(productQuery.trim(), ignoreCase = true)
    }
    val filteredCategories = categories.filter { it.name.contains(categoryQuery.trim(), ignoreCase = true) }
    val createLabel = if (showProducts) "Tambah produk" else "Tambah kategori"
    val create = { edit(showProducts, null) }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("catalog"), contentAlignment = Alignment.TopCenter) {
        val compactHeight = maxHeight < 220.dp
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).testTag("catalog-list"),
                contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (catalog == null) {
                    item { MerchantCatalogStatus(state, onRetry) }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            CatalogMode("Produk", showProducts, { showProducts = true }, Modifier.weight(1f))
                            CatalogMode("Kategori", !showProducts, { showProducts = false }, Modifier.weight(1f))
                        }
                    }
                    if (showProducts && products.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                verticalAlignment = Alignment.CenterVertically) {
                                MerchantSearch(productQuery, "Cari produk", { productQuery = it }, Modifier.weight(1f))
                                if (categories.isNotEmpty()) CatalogCategoryFilter(categories, selectedCategory, activeCategory, {
                                    selectedCategory = it
                                })
                            }
                        }
                        if (activeCategory != null) item {
                            Text("Kategori: $activeCategory", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (!showProducts && categories.isNotEmpty()) {
                        item { MerchantSearch(categoryQuery, "Cari kategori", { categoryQuery = it }, Modifier.fillMaxWidth()) }
                    }
                    if (compactHeight) item {
                        MerchantAction(createLabel, create, Modifier.fillMaxWidth(), Icons.Default.Add)
                    }
                    if (showProducts) {
                        when {
                            products.isEmpty() -> item { MerchantNotice("Belum ada produk", "Produk aktif tampil di Kasir.") }
                            filteredProducts.isEmpty() -> item {
                                MerchantNotice("Produk tidak ditemukan", "Ubah pencarian atau kategori.", "Bersihkan filter", {
                                    productQuery = ""
                                    selectedCategory = null
                                })
                            }
                        }
                        items(filteredProducts, key = { "product:${it.id}" }) { product ->
                            val category = names[product.categoryId]
                            CatalogRow(product.name, category ?: "Tanpa kategori", rupiah(product.price)) {
                                edit(true, product.id)
                            }
                        }
                    } else {
                        when {
                            categories.isEmpty() -> item {
                                MerchantNotice("Belum ada kategori", "Kelompokkan produk agar lebih mudah dicari.")
                            }
                            filteredCategories.isEmpty() -> item {
                                MerchantNotice("Kategori tidak ditemukan", "Coba nama kategori lain.", "Bersihkan pencarian", {
                                    categoryQuery = ""
                                })
                            }
                        }
                        items(filteredCategories, key = { "category:${it.id}" }) { category ->
                            CatalogRow(category.name, "${products.count { it.categoryId == category.id }} produk") {
                                edit(false, category.id)
                            }
                        }
                    }
                }
            }
            if (catalog != null && !compactHeight) {
                Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 1.dp) {
                    MerchantAction(createLabel, create,
                        Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.sm), Icons.Default.Add)
                }
            }
        }
    }
}

@Composable
private fun CatalogMode(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    FilterChip(
        selected, onSelect, label = { Text(label, Modifier.padding(vertical = Space.xs)) },
        modifier = modifier.heightIn(min = Space.touch).merchantFocus(MerchantStyle.control, MaterialTheme.colorScheme.primary),
        shape = MerchantStyle.control,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun CatalogCategoryFilter(
    categories: List<Category>, selected: String?, active: String?, onSelect: (String?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val label = if (active == null) "Filter kategori" else "Filter kategori: $active"
    Box {
        Surface(
            color = if (active == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            shape = MerchantStyle.control,
        ) {
            MerchantIconButton(Icons.AutoMirrored.Filled.List, label, {
                focus.clearFocus()
                open = true
            })
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text("Semua kategori") },
                trailingIcon = if (active == null) {{ Icon(Icons.Default.Check, "Dipilih") }} else null,
                onClick = { onSelect(null); open = false },
                modifier = Modifier.heightIn(min = Space.touch),
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    trailingIcon = if (category.id == selected) {{ Icon(Icons.Default.Check, "Dipilih") }} else null,
                    onClick = { onSelect(category.id); open = false },
                    modifier = Modifier.heightIn(min = Space.touch).testTag("catalog-category-${category.id}"),
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(name: String, detail: String, price: String? = null, onClick: () -> Unit) {
    Panel(
        Modifier.merchantFocus(MerchantStyle.panel, MaterialTheme.colorScheme.primary)
            .clickable(onClickLabel = "Edit $name", onClick = onClick),
        contentPadding = Space.md,
    ) {
        Row(
            Modifier.heightIn(min = MerchantStyle.rowHeight - Space.md * 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (LocalDensity.current.fontScale < 1.6f) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant, shape = MerchantStyle.slot,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (price != null) MerchantMoney(price)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CatalogEditor(
    isProduct: Boolean,
    product: Product?,
    category: Category?,
    categories: List<Category>,
    state: SaveState,
    onSave: (String, String, String?) -> Unit,
    onArchive: () -> Unit,
) = MerchantTheme {
    var name by rememberSaveable { mutableStateOf(product?.name ?: category?.name.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(product?.price?.toString().orEmpty()) }
    var selected by rememberSaveable { mutableStateOf(product?.categoryId) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            if (isProduct) (if (product == null) "Tambah produk" else "Edit produk")
            else (if (category == null) "Tambah kategori" else "Edit kategori"),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = {
            Text(if (isProduct) "Nama produk" else "Nama kategori")
        }, singleLine = true, enabled = !state.busy)
        if (isProduct) {
            OutlinedTextField(
                price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Harga (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !state.busy,
            )
            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            Box {
                val label = categories.find { it.id == selected }?.name
                    ?: if (selected == null) "Tanpa kategori" else "Kategori diarsipkan"
                OutlinedButton({ expanded = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                    Text(label)
                }
                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem({ Text("Tanpa kategori") }, { selected = null; expanded = false })
                    categories.forEach { item ->
                        DropdownMenuItem({ Text(item.name) }, { selected = item.id; expanded = false })
                    }
                }
            }
        }
        if (state.error != null) Text(state.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error)
        MerchantAction(
            if (state.busy) "Menyimpan?" else "Simpan",
            { onSave(name, price, selected) }, Modifier.fillMaxWidth(), enabled = !state.busy,
        )
        if (product != null || category != null) {
            TextButton({ confirmArchive = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = Space.touch)) {
                Text(if (isProduct) "Arsipkan produk" else "Arsipkan kategori", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text(if (isProduct) "Arsipkan produk?" else "Arsipkan kategori?") },
        text = { Text(if (isProduct) "Produk disembunyikan dari katalog dan kasir."
            else "Produk aktif di kategori ini tetap tersedia sebagai produk tanpa kategori.") },
        confirmButton = { TextButton({ confirmArchive = false; onArchive() }) { Text("Arsipkan") } },
        dismissButton = { TextButton({ confirmArchive = false }) { Text("Batal") } },
    )
}
