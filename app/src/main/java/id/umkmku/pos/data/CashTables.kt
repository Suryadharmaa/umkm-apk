package id.umkmku.pos.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Embedded
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sale", indices = [Index(value = ["draftSlot"], unique = true)])
data class SaleRow(@PrimaryKey val id: String, val draftSlot: Int?, val openedAt: Long)

@Entity(
    tableName = "sale_line",
    primaryKeys = ["saleId", "productId"],
    foreignKeys = [
        ForeignKey(entity = SaleRow::class, parentColumns = ["id"], childColumns = ["saleId"]),
        ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"]),
    ],
    indices = [Index("productId")],
)
data class SaleLineRow(
    val saleId: String,
    val productId: String,
    val nameSnapshot: String,
    val unitIdr: Long,
    val quantity: Int,
)

@Entity(
    tableName = "payment",
    indices = [Index(value = ["operationKey"], unique = true)],
    foreignKeys = [ForeignKey(entity = SaleRow::class, parentColumns = ["id"], childColumns = ["saleId"])],
)
data class CashPaymentRow(
    @PrimaryKey val saleId: String,
    val operationKey: String,
    val totalIdr: Long,
    val tenderIdr: Long,
    val paidAt: Long,
)

@Entity(
    tableName = "journal",
    indices = [Index(value = ["saleId", "kind"], unique = true)],
    foreignKeys = [ForeignKey(entity = CashPaymentRow::class, parentColumns = ["saleId"], childColumns = ["saleId"])],
)
data class SaleJournalRow(@PrimaryKey val id: String, val saleId: String, val kind: String)

@Entity(
    tableName = "journal_line",
    primaryKeys = ["journalId", "account"],
    foreignKeys = [ForeignKey(entity = SaleJournalRow::class, parentColumns = ["id"], childColumns = ["journalId"])],
)
data class SaleJournalLineRow(val journalId: String, val account: String, val signedIdr: Long)

data class SaleRecord(
    @Embedded val sale: SaleRow,
    @Relation(parentColumn = "id", entityColumn = "saleId") val lines: List<SaleLineRow>,
    @Relation(parentColumn = "id", entityColumn = "saleId") val payment: CashPaymentRow?,
)

@Dao
interface CashSaleDao {
    @Transaction @Query("SELECT * FROM sale WHERE draftSlot = 1")
    fun observeDraft(): Flow<SaleRecord?>

    @Transaction @Query("SELECT * FROM sale WHERE id = :id")
    fun observeSale(id: String): Flow<SaleRecord?>

    @Transaction @Query("SELECT sale.* FROM sale JOIN payment ON sale.id = payment.saleId ORDER BY payment.paidAt DESC, sale.id DESC")
    fun observeHistory(): Flow<List<SaleRecord>>

    @Transaction @Query("SELECT * FROM sale WHERE id = :id")
    suspend fun sale(id: String): SaleRecord?

    @Transaction @Query("SELECT * FROM sale WHERE draftSlot = 1")
    suspend fun draft(): SaleRecord?

    @Query("SELECT * FROM product WHERE archived_at IS NULL AND id IN (:ids)")
    suspend fun activeProducts(ids: List<String>): List<ProductRow>

    @Query("UPDATE sale SET draftSlot = NULL WHERE id = :id AND draftSlot = 1")
    suspend fun closeDraft(id: String): Int

    @Insert suspend fun insertSale(row: SaleRow)
    @Insert suspend fun insertLine(row: SaleLineRow)
    @Update suspend fun updateLines(rows: List<SaleLineRow>)
    @Delete suspend fun deleteLine(row: SaleLineRow)
    @Insert suspend fun insertPayment(row: CashPaymentRow)
    @Insert suspend fun insertJournal(row: SaleJournalRow)
    @Insert suspend fun insertJournalLines(rows: List<SaleJournalLineRow>)
}
