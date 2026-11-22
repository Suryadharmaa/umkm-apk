package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.CASH
import id.umkmku.pos.domain.QRIS
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.CorrectionDetails
import id.umkmku.pos.domain.CorrectionKind
import id.umkmku.pos.domain.inverse
import id.umkmku.pos.domain.refundLines
import java.math.BigInteger
import id.umkmku.pos.domain.ExpenseCategory
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.LedgerEntry
import id.umkmku.pos.domain.LedgerLine
import id.umkmku.pos.domain.Movement
import id.umkmku.pos.domain.MovementForm
import id.umkmku.pos.domain.MovementKind
import id.umkmku.pos.domain.lines
import id.umkmku.pos.domain.validate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class CashbookRepository @Inject constructor(private val db: CatalogDatabase) {
    private val cashbook = db.cashbookDao()
    private val ledger = db.cashSaleDao()

    fun observeLedger() = cashbook.observeLedger().map { records ->
        val originals = records.filter { it.correction == null }.associate { it.journal.id to it.entry() }
        records.map { record ->
            val correction = record.correction
            if (correction == null) originals.getValue(record.journal.id)
            else record.entry(originals.getValue(correction.originalJournalId))
        }
    }.flowOn(Dispatchers.Default)

    suspend fun post(operationId: String, form: MovementForm): LedgerEntry {
        if (runCatching { UUID.fromString(operationId).toString() }.getOrNull() != operationId) {
            throw CashbookException("ID pencatatan tidak valid.")
        }
        val movement = form.validate()
        val id = "movement:$operationId"
        return db.withTransaction {
            val existing = cashbook.ledgerEntry(id)?.entry()
            if (existing != null) {
                if (existing.movement != movement) throw CashbookException("Mutasi ini sudah tercatat dengan rincian berbeda.")
                return@withTransaction existing
            }

            val account = movement.account.name
            val openingAccount = account.takeIf { movement.kind == MovementKind.OPENING }
            val now = System.currentTimeMillis()
            val effectiveAt = if (openingAccount != null) {
                if (cashbook.opened(openingAccount)) throw CashbookException("Saldo awal akun ini sudah tercatat.")
                minOf(cashbook.earliestMovement(openingAccount) ?: now, now)
            } else now
            val lines = movement.lines()
            ledger.insertJournal(JournalRow(id, null, movement.kind.name))
            cashbook.insertMovement(FinancialMovementRow(
                id, account, openingAccount, movement.amount, movement.category?.name,
                movement.note, effectiveAt, now,
            ))
            ledger.insertJournalLines(lines.map { JournalLineRow(id, it.account.name, it.amount) })
            LedgerEntry(id, effectiveAt, now, movement.amount, movement, null, lines)
        }
    }
}

internal fun JournalRecord.entry(original: LedgerEntry? = null): LedgerEntry {
    val postings = lines.map { LedgerLine(LedgerAccount.valueOf(it.account), it.signedIdr) }
    check(postings.fold(BigInteger.ZERO) { sum, line -> sum.add(BigInteger.valueOf(line.amount)) } == BigInteger.ZERO)
    val saleId = journal.saleId
    correction?.let { row ->
        val source = checkNotNull(original)
        check(source.id == row.originalJournalId && source.correctedBy == journal.id)
        val kind = CorrectionKind.valueOf(row.kind)
        val funding = row.funding?.let(LedgerAccount::valueOf)
        check((funding == LedgerAccount.BANK && (row.evidencedIdr ?: 0) > 0) == (row.bankReference != null))
        when (kind) {
            CorrectionKind.REFUND -> {
                check(journal.kind == "REFUND" && saleId != null && saleId == source.saleId && saleId == row.saleId &&
                    source.amount == row.evidencedIdr && funding != null)
                val cashRefund = source.amountFor(LedgerAccount.CASH) != null
                check(if (cashRefund) funding == LedgerAccount.CASH && row.settlementId == null
                    else (funding == LedgerAccount.BANK && row.settlementId != null) ||
                        (funding == LedgerAccount.QRIS_CLEARING && row.settlementId == null))
                check(source.activeSettlementId == row.settlementId)
                check(if (cashRefund) row.provider == null && row.merchant == null && row.reference == null
                    else row.provider != null && row.merchant != null && row.reference != null)
                check(postings.toSet() == refundLines(funding, source.amount).toSet())
            }
            CorrectionKind.MOVEMENT_REVERSAL, CorrectionKind.SETTLEMENT_REVERSAL -> {
                check(journal.kind == "REVERSAL" && saleId == null && postings.toSet() == inverse(source.lines).toSet())
                check(if (kind == CorrectionKind.MOVEMENT_REVERSAL) source.movement?.kind?.let { it != MovementKind.OPENING } == true
                    else source.payout?.net == row.evidencedIdr && row.settlementId != null &&
                        source.id == "settlement:${row.settlementId}" && funding == LedgerAccount.BANK)
            }
        }
        return LedgerEntry(journal.id, row.effectiveAt, row.recordedAt, source.amount, null, saleId, postings,
            correction = CorrectionDetails(kind, source.id, row.reason, funding, row.provider, row.merchant,
                row.reference, row.settlementId, row.bankReference))
    }
    val movementRow = movement
    val payout = settlement?.posting()
    if (payout != null) {
        check(journal.kind == "SETTLEMENT" && saleId == null && movementRow == null)
        check(postings.toSet() == payout.lines().toSet())
        return LedgerEntry(journal.id, payout.effectiveAt, checkNotNull(settlement?.postedAt),
            payout.gross, null, null, payout.lines(), payout, correctedBy = correctedBy?.journalId)
    }
    if (saleId != null) {
        val sale = checkNotNull(payment)
        check(journal.kind == "SALE" && movementRow == null && sale.saleId == saleId)
        val debit = when (sale.method) {
            CASH -> LedgerAccount.CASH
            QRIS -> LedgerAccount.QRIS_CLEARING
            else -> error("Unknown payment method")
        }
        check(postings.toSet() == setOf(
            LedgerLine(debit, sale.totalIdr), LedgerLine(LedgerAccount.SALES, Math.negateExact(sale.totalIdr)),
        ))
        return LedgerEntry(journal.id, sale.paidAt, sale.paidAt, sale.totalIdr, null, saleId, postings, correctedBy = correctedBy?.journalId,
            activeSettlementId = allocation?.settlementId)
    }
    check(payment == null && movementRow != null)
    val details = Movement(
        MovementKind.valueOf(journal.kind), LedgerAccount.valueOf(movementRow.account),
        movementRow.amountIdr, movementRow.category?.let(ExpenseCategory::valueOf), movementRow.note,
    )
    val expected = details.lines()
    check(postings.toSet() == expected.toSet())
    return LedgerEntry(journal.id, movementRow.effectiveAt, movementRow.recordedAt, details.amount, details, null, expected,
        correctedBy = correctedBy?.journalId)
}
