package id.umkmku.pos.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "financial_movement",
    foreignKeys = [ForeignKey(entity = JournalRow::class, parentColumns = ["id"], childColumns = ["journalId"])],
    indices = [Index(value = ["openingAccount"], unique = true)],
)
data class FinancialMovementRow(
    @PrimaryKey val journalId: String,
    val account: String,
    val openingAccount: String?,
    val amountIdr: Long,
    val category: String?,
    val note: String,
    val effectiveAt: Long,
    val recordedAt: Long,
)

data class JournalRecord(
    @Embedded val journal: JournalRow,
    @Relation(parentColumn = "id", entityColumn = "journalId") val lines: List<JournalLineRow>,
    @Relation(parentColumn = "id", entityColumn = "journalId") val movement: FinancialMovementRow?,
    @Relation(parentColumn = "saleId", entityColumn = "saleId") val payment: PaymentRow?,
)

@Dao
interface CashbookDao {
    @Transaction
    @Query("""
        SELECT journal.* FROM journal
        LEFT JOIN financial_movement ON financial_movement.journalId = journal.id
        LEFT JOIN payment ON payment.saleId = journal.saleId
        ORDER BY COALESCE(financial_movement.effectiveAt, payment.paidAt) DESC,
            CASE WHEN journal.kind = 'OPENING' THEN 1 ELSE 0 END, journal.id DESC
    """)
    fun observeLedger(): Flow<List<JournalRecord>>

    @Transaction @Query("SELECT * FROM journal WHERE id = :id")
    suspend fun ledgerEntry(id: String): JournalRecord?

    @Query("SELECT EXISTS(SELECT 1 FROM financial_movement WHERE openingAccount = :account)")
    suspend fun opened(account: String): Boolean

    @Query("""
        SELECT MIN(COALESCE(financial_movement.effectiveAt, payment.paidAt))
        FROM journal_line JOIN journal ON journal.id = journal_line.journalId
        LEFT JOIN financial_movement ON financial_movement.journalId = journal.id
        LEFT JOIN payment ON payment.saleId = journal.saleId
        WHERE journal_line.account = :account AND journal.kind != 'OPENING'
    """)
    suspend fun earliestMovement(account: String): Long?

    @Insert suspend fun insertMovement(row: FinancialMovementRow)
}
