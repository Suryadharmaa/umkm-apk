package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.PayoutAssessment
import id.umkmku.pos.domain.PayoutEvidence
import id.umkmku.pos.domain.PayoutPosting
import id.umkmku.pos.domain.SettlementPayment
import id.umkmku.pos.domain.SettlementRecord
import id.umkmku.pos.domain.SettlementState
import id.umkmku.pos.domain.assessPayout
import id.umkmku.pos.domain.qrisProvider
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class SettlementRepository @Inject constructor(private val db: CatalogDatabase) {
    private val dao = db.settlementDao()
    private val ledger = db.cashSaleDao()

    fun history() = dao.history().map { rows -> rows.map { it.record() } }
    fun candidates() = dao.observeCandidates().map { rows -> rows.map { it.payment() } }

    suspend fun submit(
        id: String, form: PayoutEvidence, paymentIds: Set<String>, post: Boolean, zone: ZoneId,
    ): SettlementRecord {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id || paymentIds.size > 200) {
            throw CashbookException("Gunakan satu ID pencairan dan maksimal 200 pembayaran.")
        }
        if (listOf(form.provider, form.merchantId, form.reference, form.gross, form.fee, form.net, form.payoutTime)
                .any { it.length > 120 }) throw CashbookException("Rincian pencairan terlalu panjang.")
        val evidence = form.canonical()
        return db.withTransaction {
            val existing = dao.settlement(id)?.record()
            if (existing?.state == SettlementState.POSTED) {
                if (existing.evidence == evidence && existing.paymentIds == paymentIds) return@withTransaction existing
                throw CashbookException("Pencairan ini sudah tercatat dengan rincian berbeda.")
            }
            val now = System.currentTimeMillis()
            val candidates = dao.selected(paymentIds.toList()).map { it.payment() }
            val assessment = when {
                candidates.size != paymentIds.size ->
                    PayoutAssessment(SettlementState.UNRESOLVED, "Pilihan memuat transaksi yang belum dikonfirmasi sebagai pembayaran QRIS.")
                dao.referenceUsed(evidence.provider, evidence.merchantId, evidence.reference) ->
                    PayoutAssessment(SettlementState.UNRESOLVED, "Referensi pencairan ini sudah tercatat untuk akun penyedia.")
                else -> assessPayout(evidence, candidates, zone, now, dao.bankOpening())
            }
            val posting = assessment.posting.takeIf { post }
            val journalId = posting?.let { "settlement:$id" }
            if (journalId != null) {
                ledger.insertJournal(JournalRow(journalId, null, "SETTLEMENT"))
                ledger.insertJournalLines(posting.lines().map { JournalLineRow(journalId, it.account.name, it.amount) })
            }
            val row = SettlementRow(
                id, evidence, if (posting == null) assessment.state.name else SettlementState.POSTED.name,
                assessment.reason, now, evidence.reference.takeIf { posting != null },
                journalId, now.takeIf { posting != null }, posting?.effectiveAt,
            )
            dao.save(row)
            dao.clearDraftSelections(id)
            val selections = candidates.map {
                SettlementPaymentRow(id, it.saleId, it.saleId.takeIf { posting != null })
            }
            dao.insertSelections(selections)
            SettlementWithPayments(row, selections).record()
        }
    }
}

internal fun SettlementRow.posting(): PayoutPosting? = effectiveAt?.let {
    PayoutPosting(evidence.gross.toLong(), evidence.fee.toLong(), evidence.net.toLong(), it)
}

private fun SettlementWithPayments.record() = SettlementRecord(
    row.id, row.evidence, payments.mapTo(mutableSetOf()) { it.saleId }, SettlementState.valueOf(row.state),
    row.reason, row.updatedAt, row.postedAt, row.posting(), reversal?.journalId,
)

private fun PayoutCandidate.payment() = SettlementPayment(
    saleId, amount, observedAt, reference, merchantId, merchantName, qrisProvider(payload), allocatedTo, refunded, availableAt,
)
