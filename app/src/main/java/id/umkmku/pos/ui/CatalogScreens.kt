package id.umkmku.pos.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun CatalogScreen(state: CatalogState, onRetry: () -> Unit, edit: (Boolean, String?) -> Unit) {
    var showProducts by rememberSaveable { mutableStateOf(true) }
    Column {
        PrimaryTabRow(selectedTabIndex = if (showProducts) 0 else 1) {
            Tab(selected = showProducts, onClick = { showProducts = true }, text = { Text("Produk") })
            Tab(selected = !showProducts, onClick = { showProducts = false }, text = { Text("Kategori") })
        }
        LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (state !is CatalogState.Ready) {
                item { CatalogStatus(state, onRetry) }
            } else {
                item {
                    Button(onClick = { edit(showProducts, null) }, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                        Icon(Icons.Default.Add, null)
                        Text(if (showProducts) "Tambah produk" else "Tambah kategori", Modifier.padding(start = Space.sm))
                    }
                }
                if (showProducts) {
                    if (state.catalog.products.isEmpty()) item { MessagePanel("Belum ada produk", "Produk aktif tampil di Kasir.") }
                    items(state.catalog.products, key = { it.id }) { product ->
                        val category = state.catalog.categories.find { it.id == product.categoryId }?.name ?: "Tanpa kategori"
                        CatalogRow(product.name, "${rupiah(product.price)} · $category") { edit(true, product.id) }
                    }
                } else {
                    if (state.catalog.categories.isEmpty()) item { MessagePanel("Belum ada kategori", "Kelompokkan produk agar lebih mudah dicari.") }
                    items(state.catalog.categories, key = { it.id }) { category ->
                        val count = state.catalog.products.count { it.categoryId == category.id }
                        CatalogRow(category.name, "$count produk") { edit(false, category.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(name: String, detail: String, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClickLabel = "Edit $name", onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
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
        Button({ onSave(name, price, selected) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
            Text(if (state.busy) "Menyimpan…" else "Simpan")
        }
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
