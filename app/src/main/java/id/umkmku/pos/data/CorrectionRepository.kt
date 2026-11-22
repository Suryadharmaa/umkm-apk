package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.CASH
import id.umkmku.pos.domain.QRIS
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.CorrectionInput
import id.umkmku.pos.domain.CorrectionKind
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.LedgerLine
import id.umkmku.pos.domain.normalized
import id.umkmku.pos.domain.requireAmount
import id.umkmku.pos.domain.requireEvidence
import id.umkmku.pos.domain.refundLines
import id.umkmku.pos.domain.inverse
import id.umkmku.pos.domain.qrisProvider
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class CorrectionRepository @Inject constructor(private val db: CatalogDatabase) {
    private val ledger = db.cashbookDao()
    private val sales = db.cashSaleDao()
    private val corrections = db.correctionDao()

    suspend fun post(operation: String, target: String, input: CorrectionInput, zone: ZoneId): String {
        if (runCatching { UUID.fromString(operation).toString() }.getOrNull() != operation) {
            throw CashbookException("ID pencatatan tidak valid.")
        }
        val evidence = input.normalized()
        val id = "correction:$operation"
        return db.withTransaction {
            val existing = corrections.operation(id)
            if (existing != null) {
                if (!existing.matches(target, evidence, zone)) {
                    throw CashbookException("ID koreksi sudah digunakan dengan rincian berbeda.")
                }
                return@withTransaction id
            }
            val original = ledger.ledgerEntry(target)
                ?: throw CashbookException("Transaksi asli belum tercatat.")
            if (corrections.hasCorrection(target)) throw CashbookException("Transaksi ini sudah dikoreksi.")
            if (original.correction != null) throw CashbookException("Entri koreksi tidak dapat dibalik.")
            val entry = original.entry()
            val now = System.currentTimeMillis()
            val posting = when {
                original.journal.kind == "SALE" -> {
                    val sale = checkNotNull(original.payment)
                    prepareRefund(id, sale, target, evidence, zone, now)
                }
                entry.payout != null -> prepareSettlementReversal(
                    id, original, evidence, zone, now,
                )
                entry.movement != null && original.journal.kind != "OPENING" -> {
                    if (evidence.hasFinancialEvidence()) throw CashbookException("Periksa kembali rincian pembalik mutasi.")
                    CorrectionPosting(
                        CorrectionRow(id, target, CorrectionKind.MOVEMENT_REVERSAL.name, evidence.reason,
                            null, null, null, null, null, null, null, null, now, now),
                        inverse(entry.lines),
                    )
                }
                else -> throw CashbookException("Jenis transaksi ini tidak dapat dibalik.")
            }
            val row = posting.row
            if (row.provider != null && corrections.referenceUsed(row.provider, checkNotNull(row.merchant), checkNotNull(row.reference))) {
                throw CashbookException("Referensi koreksi sudah pernah digunakan.")
            }
            if (row.bankReference != null && corrections.bankReferenceUsed(row.bankReference)) {
                throw CashbookException("Referensi debit bank sudah digunakan.")
            }
            sales.insertJournal(JournalRow(id, original.journal.saleId.takeIf { row.kind == CorrectionKind.REFUND.name },
                if (row.kind == CorrectionKind.REFUND.name) "REFUND" else "REVERSAL"))
            if (row.bankReference != null && corrections.bankReferenceUsed(row.bankReference)) {
                throw CashbookException("Referensi debit bank sudah digunakan.")
            }
            sales.insertJournalLines(posting.lines.map { JournalLineRow(id, it.account.name, it.amount) })
            corrections.insert(row)
            if (row.kind == CorrectionKind.SETTLEMENT_REVERSAL.name) {
                val settlementId = checkNotNull(row.settlementId)
                val expected = checkNotNull(db.settlementDao().settlement(settlementId)).payments.size
                check(expected > 0 && corrections.releaseAllocations(settlementId) == expected)
            }
            id
        }
    }

    private suspend fun prepareRefund(
        id: String, sale: PaymentRow, target: String, input: CorrectionInput, zone: ZoneId, now: Long,
    ): CorrectionPosting {
        input.requireAmount(sale.totalIdr)
        if (sale.method == CASH) {
            if (input.funding != LedgerAccount.CASH || input.hasProviderEvidence()) {
                throw CashbookException("Konfirmasikan pengembalian tunai penuh dari kas usaha.")
            }
            return CorrectionPosting(
                CorrectionRow(id, target, CorrectionKind.REFUND.name, input.reason, LedgerAccount.CASH.name,
                    null, null, null, null, sale.totalIdr, null, null, now, now, sale.saleId),
                refundLines(LedgerAccount.CASH, sale.totalIdr),
            )
        }
        check(sale.method == QRIS)
        val saleRecord = checkNotNull(sales.sale(sale.saleId))
        val order = saleRecord.qrisOrder
        val received = saleRecord.evidence
        val profile = order?.let { db.qrisDao().profile(it.profileId) }
        if (order?.status != "MANUALLY_CONFIRMED" || order.totalIdr != sale.totalIdr ||
            received?.observedIdr != sale.totalIdr || profile == null || received.merchantId != profile.merchantId) {
            throw CashbookException("Pengembalian QRIS memerlukan pembayaran dan bukti merchant yang terkonfirmasi.")
        }
        val settlement = corrections.activeSettlement(sale.saleId)
        val funding = if (settlement == null) LedgerAccount.QRIS_CLEARING else LedgerAccount.BANK
        if (input.funding != funding) {
            throw CashbookException(if (settlement == null) "Bukti harus menunjukkan pengurangan saldo QRIS belum cair."
                else "Pembayaran sudah dicairkan. Gunakan bukti debit dari rekening bank usaha.")
        }
        val at = input.requireEvidence(
            qrisProvider(profile.payload), profile.merchantId, received.reference, sale.totalIdr,
            maxOf(received.observedAt, settlement?.effectiveAt ?: received.observedAt,
                corrections.latestClearingReturn(sale.saleId) ?: received.observedAt), now, zone,
        )
        checkOpening(funding, at)
        return CorrectionPosting(
            CorrectionRow(id, target, CorrectionKind.REFUND.name, input.reason, funding.name, input.provider,
                input.merchant, input.reference, settlement?.id, sale.totalIdr, input.occurrence, zone.id, at, now, sale.saleId, input.bankReference.ifEmpty { null }),
            refundLines(funding, sale.totalIdr),
        )
    }

    private suspend fun prepareSettlementReversal(
        id: String, original: JournalRecord, input: CorrectionInput, zone: ZoneId, now: Long,
    ): CorrectionPosting {
        val settlement = checkNotNull(original.settlement)
        val payout = checkNotNull(settlement.posting())
        if (corrections.hasDependentRefund(settlement.id)) {
            throw CashbookException("Ada pengembalian dari bank yang bergantung pada pencairan ini. Lakukan rekonsiliasi manual.")
        }
        if (input.funding != LedgerAccount.BANK) throw CashbookException("Periksa bukti koreksi pada rekening bank usaha.")
        val at = input.requireEvidence(settlement.evidence.provider, settlement.evidence.merchantId,
            settlement.evidence.reference, payout.net, payout.effectiveAt, now, zone)
        checkOpening(LedgerAccount.BANK, at)
        return CorrectionPosting(
            CorrectionRow(id, original.journal.id, CorrectionKind.SETTLEMENT_REVERSAL.name, input.reason,
                LedgerAccount.BANK.name, input.provider, input.merchant, input.reference, settlement.id,
                payout.net, input.occurrence, zone.id, at, now, bankReference = input.bankReference.ifEmpty { null }),
            inverse(payout.lines()),
        )
    }

    private suspend fun checkOpening(funding: LedgerAccount, at: Long) {
        val opening = if (funding == LedgerAccount.BANK) db.settlementDao().bankOpening() else null
        if (opening != null && at < opening) {
            throw CashbookException("Bukti mendahului saldo awal bank. Rekonsiliasi saldo awal terlebih dahulu.")
        }
    }
}

private data class CorrectionPosting(val row: CorrectionRow, val lines: List<LedgerLine>)

private fun CorrectionInput.hasProviderEvidence() =
    provider.isNotEmpty() || merchant.isNotEmpty() || reference.isNotEmpty() || occurrence.isNotEmpty() || bankReference.isNotEmpty()

private fun CorrectionInput.hasFinancialEvidence() = hasProviderEvidence() || amount.isNotEmpty() || funding != null

private fun CorrectionRow.matches(target: String, input: CorrectionInput, zone: ZoneId) =
    originalJournalId == target && reason == input.reason && funding == input.funding?.name &&
        provider.orEmpty() == input.provider && merchant.orEmpty() == input.merchant &&
        reference.orEmpty() == input.reference && bankReference.orEmpty() == input.bankReference && evidenceTime.orEmpty() == input.occurrence &&
        (evidenceZone == null || evidenceZone == zone.id) &&
        (if (evidencedIdr == null) input.amount.isEmpty() else
            runCatching { input.requireAmount(checkNotNull(evidencedIdr)) }.isSuccess)
