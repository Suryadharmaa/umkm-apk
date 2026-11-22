package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.CASH
import id.umkmku.pos.domain.QRIS
import id.umkmku.pos.domain.CashbookException
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
        records.map { it.entry() }
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

private fun JournalRecord.entry(): LedgerEntry {
    val postings = lines.map { LedgerLine(LedgerAccount.valueOf(it.account), it.signedIdr) }
    val saleId = journal.saleId
    val movementRow = movement
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
        return LedgerEntry(journal.id, sale.paidAt, sale.paidAt, sale.totalIdr, null, saleId, postings)
    }
    check(payment == null && movementRow != null)
    val details = Movement(
        MovementKind.valueOf(journal.kind), LedgerAccount.valueOf(movementRow.account),
        movementRow.amountIdr, movementRow.category?.let(ExpenseCategory::valueOf), movementRow.note,
    )
    val expected = details.lines()
    check(postings.toSet() == expected.toSet())
    return LedgerEntry(journal.id, movementRow.effectiveAt, movementRow.recordedAt, details.amount, details, null, expected)
}
