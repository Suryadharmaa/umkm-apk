package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.Catalog
import id.umkmku.pos.domain.CatalogException
import id.umkmku.pos.domain.CatalogRepository
import id.umkmku.pos.domain.Category
import id.umkmku.pos.domain.Product
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

class RoomCatalogRepository @Inject constructor(private val db: CatalogDatabase) : CatalogRepository {
    private val dao = db.catalogDao()

    override fun observe() = combine(dao.products(), dao.categories()) { products, categories ->
        Catalog(
            products.map { Product(it.id, it.name, it.price, it.categoryId) },
            categories.map { Category(it.id, it.name) },
        )
    }

    override suspend fun saveProduct(id: String?, name: String, price: Long, categoryId: String?) {
        db.withTransaction {
            if (categoryId != null && !dao.activeCategory(categoryId)) {
                throw CatalogException("Kategori sudah diarsipkan. Pilih kategori lain.")
            }
            if (id == null) {
                dao.insertProduct(ProductRow(UUID.randomUUID().toString(), name, price, categoryId))
            } else if (dao.updateProduct(id, name, price, categoryId) != 1) {
                throw CatalogException("Produk sudah diarsipkan.")
            }
        }
    }

    override suspend fun saveCategory(id: String?, name: String, key: String) {
        db.withTransaction {
            if (dao.categoryNameExists(key, id)) throw CatalogException("Nama kategori sudah digunakan.")
            if (id == null) {
                dao.insertCategory(CategoryRow(UUID.randomUUID().toString(), name, key))
            } else if (dao.updateCategory(id, name, key) != 1) {
                throw CatalogException("Kategori sudah diarsipkan.")
            }
        }
    }

    override suspend fun archiveProduct(id: String) {
        dao.archiveProduct(id, System.currentTimeMillis())
    }

    override suspend fun archiveCategory(id: String) {
        db.withTransaction {
            if (dao.archiveCategory(id, System.currentTimeMillis()) == 1) dao.clearCategory(id)
        }
    }
}
