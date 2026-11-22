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
import androidx.room.Upsert
import id.umkmku.pos.domain.PayoutEvidence
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "settlement",
    indices = [Index(value = ["provider", "merchantId", "postedReference"], unique = true),
        Index(value = ["journalId"], unique = true)],
    foreignKeys = [ForeignKey(entity = JournalRow::class, parentColumns = ["id"], childColumns = ["journalId"])],
)
data class SettlementRow(
    @PrimaryKey val id: String,
    @Embedded val evidence: PayoutEvidence,
    val state: String,
    val reason: String?,
    val updatedAt: Long,
    val postedReference: String? = null,
    val journalId: String? = null,
    val postedAt: Long? = null,
    val effectiveAt: Long? = null,
)

@Entity(
    tableName = "settlement_payment",
    primaryKeys = ["settlementId", "saleId"],
    indices = [Index("saleId"), Index(value = ["postedSaleId"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = SettlementRow::class, parentColumns = ["id"], childColumns = ["settlementId"]),
        ForeignKey(entity = QrisEvidenceRow::class, parentColumns = ["saleId"], childColumns = ["saleId"]),
    ],
)
data class SettlementPaymentRow(val settlementId: String, val saleId: String, val postedSaleId: String?)

data class SettlementWithPayments(
    @Embedded val row: SettlementRow,
    @Relation(parentColumn = "id", entityColumn = "settlementId") val payments: List<SettlementPaymentRow>,
    @Relation(parentColumn = "journalId", entityColumn = "originalJournalId") val reversal: CorrectionRow? = null,
)

data class PayoutCandidate(
    val saleId: String,
    val amount: Long,
    val observedAt: Long,
    val reference: String,
    val merchantId: String,
    val merchantName: String,
    val payload: String,
    val allocatedTo: String?,
    val refunded: Boolean,
    val availableAt: Long,
)

private const val candidates = """
    SELECT p.saleId, p.totalIdr AS amount, evidence.observedAt, evidence.reference,
        profile.merchantId, profile.merchantName, profile.payload,
        allocation.settlementId AS allocatedTo, correction.journalId IS NOT NULL AS refunded,
        MAX(evidence.observedAt, COALESCE((
            SELECT MAX(reversal.effectiveAt) FROM financial_correction reversal
            JOIN settlement_payment previous ON previous.settlementId = reversal.settlementId
            WHERE reversal.kind = 'SETTLEMENT_REVERSAL' AND previous.saleId = p.saleId
        ), evidence.observedAt)) AS availableAt
    FROM payment p
    JOIN qris_order q ON q.saleId = p.saleId
    JOIN qris_profile profile ON profile.id = q.profileId
    JOIN qris_evidence evidence ON evidence.saleId = p.saleId AND evidence.merchantId = profile.merchantId
    JOIN journal j ON j.saleId = p.saleId AND j.kind = 'SALE'
    JOIN journal_line l ON l.journalId = j.id AND l.account = 'QRIS_CLEARING' AND l.signedIdr = p.totalIdr
    LEFT JOIN settlement_payment allocation ON allocation.postedSaleId = p.saleId
    LEFT JOIN financial_correction correction ON correction.originalJournalId = j.id
    WHERE p.method = 'QRIS' AND q.status = 'MANUALLY_CONFIRMED'
        AND evidence.observedIdr = p.totalIdr AND q.totalIdr = p.totalIdr
"""

@Dao
interface SettlementDao {
    @Query(candidates + " ORDER BY evidence.observedAt DESC, p.saleId")
    fun observeCandidates(): Flow<List<PayoutCandidate>>

    @Query(candidates + " AND p.saleId IN (:ids)")
    suspend fun selected(ids: List<String>): List<PayoutCandidate>

    @Transaction @Query("SELECT * FROM settlement ORDER BY updatedAt DESC, id")
    fun history(): Flow<List<SettlementWithPayments>>

    @Transaction @Query("SELECT * FROM settlement WHERE id = :id")
    suspend fun settlement(id: String): SettlementWithPayments?

    @Query("SELECT EXISTS(SELECT 1 FROM settlement WHERE provider = :provider AND merchantId = :merchant AND postedReference = :reference)")
    suspend fun referenceUsed(provider: String, merchant: String, reference: String): Boolean

    @Query("SELECT effectiveAt FROM financial_movement WHERE openingAccount = 'BANK'")
    suspend fun bankOpening(): Long?

    @Upsert suspend fun save(row: SettlementRow)
    @Insert suspend fun insertSelections(rows: List<SettlementPaymentRow>)
    @Query("DELETE FROM settlement_payment WHERE settlementId = :id AND postedSaleId IS NULL")
    suspend fun clearDraftSelections(id: String)
}
