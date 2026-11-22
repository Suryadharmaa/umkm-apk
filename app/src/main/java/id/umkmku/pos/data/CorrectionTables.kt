package id.umkmku.pos.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "financial_correction",
    indices = [
        Index(value = ["originalJournalId"], unique = true),
        Index("settlementId"),
        Index(value = ["bankReference"], unique = true),
        Index(value = ["saleId"], unique = true),
        Index(value = ["provider", "merchant", "reference"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(entity = JournalRow::class, parentColumns = ["id"], childColumns = ["journalId"]),
        ForeignKey(entity = JournalRow::class, parentColumns = ["id"], childColumns = ["originalJournalId"]),
        ForeignKey(entity = PaymentRow::class, parentColumns = ["saleId"], childColumns = ["saleId"]),
        ForeignKey(entity = SettlementRow::class, parentColumns = ["id"], childColumns = ["settlementId"]),
    ],
)
data class CorrectionRow(
    @PrimaryKey val journalId: String,
    val originalJournalId: String,
    val kind: String,
    val reason: String,
    val funding: String?,
    val provider: String?,
    val merchant: String?,
    val reference: String?,
    val settlementId: String?,
    val evidencedIdr: Long?,
    val evidenceTime: String?,
    val evidenceZone: String?,
    val effectiveAt: Long,
    val recordedAt: Long,
    val saleId: String? = null,
    val bankReference: String? = null,
)

@Dao
interface CorrectionDao {
    @Query("SELECT * FROM financial_correction WHERE journalId = :journalId")
    suspend fun operation(journalId: String): CorrectionRow?

    @Query("SELECT EXISTS(SELECT 1 FROM financial_correction WHERE originalJournalId = :original)")
    suspend fun hasCorrection(original: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM financial_correction WHERE settlementId = :id AND kind = 'REFUND')")
    suspend fun hasDependentRefund(id: String): Boolean

    @Query("""
        SELECT EXISTS(SELECT 1 FROM financial_correction
            WHERE provider = :provider AND merchant = :merchant AND reference = :reference)
    """)
    suspend fun referenceUsed(provider: String, merchant: String, reference: String): Boolean

    @Query("""
        SELECT settlement.* FROM settlement
        JOIN settlement_payment ON settlement_payment.settlementId = settlement.id
        WHERE settlement_payment.postedSaleId = :saleId AND settlement.state = 'POSTED'
    """)
    suspend fun activeSettlement(saleId: String): SettlementRow?

    @Query("SELECT EXISTS(SELECT 1 FROM financial_correction WHERE bankReference = :reference)")
    suspend fun bankReferenceUsed(reference: String): Boolean

    @Query("""
        SELECT MAX(c.effectiveAt) FROM financial_correction c
        JOIN settlement_payment link ON link.settlementId = c.settlementId
        WHERE c.kind = 'SETTLEMENT_REVERSAL' AND link.saleId = :saleId
    """)
    suspend fun latestClearingReturn(saleId: String): Long?

    @Query("UPDATE settlement_payment SET postedSaleId = NULL WHERE settlementId = :id AND postedSaleId IS NOT NULL")
    suspend fun releaseAllocations(id: String): Int

    @Insert suspend fun insert(row: CorrectionRow)
}
