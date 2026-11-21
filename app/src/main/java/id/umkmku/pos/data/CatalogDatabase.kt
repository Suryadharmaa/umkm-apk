package id.umkmku.pos.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "category", indices = [Index(value = ["name_key"], unique = true)])
data class CategoryRow(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
)

@Entity(
    tableName = "product",
    foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["category_id"])],
    indices = [Index("category_id")],
)
data class ProductRow(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "unit_idr") val price: Long,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM product WHERE archived_at IS NULL ORDER BY name COLLATE NOCASE, id")
    fun products(): Flow<List<ProductRow>>

    @Query("SELECT * FROM category WHERE archived_at IS NULL ORDER BY name COLLATE NOCASE, id")
    fun categories(): Flow<List<CategoryRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM category WHERE id = :id AND archived_at IS NULL)")
    suspend fun activeCategory(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM category WHERE name_key = :key AND (:id IS NULL OR id != :id))")
    suspend fun categoryNameExists(key: String, id: String?): Boolean

    @Insert suspend fun insertProduct(row: ProductRow)
    @Insert suspend fun insertCategory(row: CategoryRow)

    @Query("UPDATE product SET name = :name, unit_idr = :price, category_id = :category WHERE id = :id AND archived_at IS NULL")
    suspend fun updateProduct(id: String, name: String, price: Long, category: String?): Int

    @Query("UPDATE category SET name = :name, name_key = :key WHERE id = :id AND archived_at IS NULL")
    suspend fun updateCategory(id: String, name: String, key: String): Int

    @Query("UPDATE product SET archived_at = :at WHERE id = :id AND archived_at IS NULL")
    suspend fun archiveProduct(id: String, at: Long): Int

    @Query("UPDATE category SET archived_at = :at WHERE id = :id AND archived_at IS NULL")
    suspend fun archiveCategory(id: String, at: Long): Int

    @Query("UPDATE product SET category_id = NULL WHERE category_id = :id AND archived_at IS NULL")
    suspend fun clearCategory(id: String)
}

@Database(entities = [ProductRow::class, CategoryRow::class], version = 1, exportSchema = true)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
