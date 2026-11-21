package id.umkmku.pos.domain

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

data class Category(val id: String, val name: String)
data class Product(val id: String, val name: String, val price: Long, val categoryId: String?)
data class Catalog(val products: List<Product> = emptyList(), val categories: List<Category> = emptyList())

interface CatalogRepository {
    fun observe(): Flow<Catalog>
    suspend fun saveProduct(id: String?, name: String, price: Long, categoryId: String?)
    suspend fun saveCategory(id: String?, name: String, key: String)
    suspend fun archiveProduct(id: String)
    suspend fun archiveCategory(id: String)
}

class CatalogException(message: String) : IllegalArgumentException(message)

class EditCatalog @Inject constructor(private val repository: CatalogRepository) {
    suspend fun product(id: String?, name: String, price: String, categoryId: String?) {
        val amount = parsePrice(price) ?: throw CatalogException("Masukkan harga Rp1–Rp999.999.999.")
        repository.saveProduct(id, validName(name), amount, categoryId)
    }

    suspend fun category(id: String?, name: String) {
        val clean = validName(name)
        repository.saveCategory(id, clean, categoryKey(clean))
    }

    suspend fun archiveProduct(id: String) = repository.archiveProduct(id)
    suspend fun archiveCategory(id: String) = repository.archiveCategory(id)
}

internal fun validName(value: String): String {
    val name = value.trim()
    if (name.isEmpty() || name.length > 80) throw CatalogException("Nama harus berisi 1–80 karakter.")
    return name
}

internal fun categoryKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)

internal fun parsePrice(value: String): Long? {
    val digits = value.trim()
    if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
    return digits.toLongOrNull()?.takeIf { it in 1L..999_999_999L }
}

fun rupiah(value: Long): String = "Rp" + NumberFormat.getIntegerInstance(
    Locale.forLanguageTag("id-ID")
).format(value)

data class CartLine(val product: Product, val quantity: Int) {
    val subtotal: Long get() = Math.multiplyExact(product.price, quantity.toLong())
}

fun cartTotal(lines: List<CartLine>): Long = lines.fold(0L) { sum, line -> Math.addExact(sum, line.subtotal) }

fun cartLines(catalog: Catalog, quantities: Map<String, Int>): List<CartLine> =
    catalog.products.mapNotNull { product ->
        quantities[product.id]?.takeIf { it in 1..9999 }?.let { CartLine(product, it) }
    }
