package id.umkmku.pos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CashbookRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.CorrectionRepository
import id.umkmku.pos.data.QrisRepository
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.data.SettlementRepository
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.CorrectionInput
import id.umkmku.pos.domain.DailyTotal
import id.umkmku.pos.domain.EditCatalog
import id.umkmku.pos.domain.ExpenseCategory
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.MovementForm
import id.umkmku.pos.domain.MovementKind
import id.umkmku.pos.domain.PayoutEvidence
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.SettlementState
import id.umkmku.pos.domain.financialPeriod
import id.umkmku.pos.domain.parseStaticQris
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CorrectionDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "corrections-${key()}.db"
    private val zone = ZoneId.systemDefault()
    private lateinit var db: CatalogDatabase
    private val sales get() = CashSaleRepository(db)
    private val corrections get() = CorrectionRepository(db)
    private val payouts get() = SettlementRepository(db)
    private val cashbook get() = CashbookRepository(db)

    @Before fun setup() { open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun cashRefundPreservesReceiptAndRequiresExactActualReturn() = runBlocking {
        val original = sales.post(draft(), 20_000)
        val sale = original.draft.id
        val target = "sale:$sale"
        reject { post(target, cash.copy(confirmed = false)) }
        reject { post(target, cash.copy(amount = "14999")) }
        reject { post(target, cash.copy(reason = "  ")) }
        val operation = key()
        val refund = post(target, cash, operation)
        db.close()
        open()
        assertEquals(refund, post(target, cash.copy(amount = "015000"), operation))
        val snapshot = sales.observeHistory().first().single()
        assertEquals(original, snapshot.receipt)
        assertEquals(refund, snapshot.refundJournalId)
        val report = today()
        assertEquals(big(15_000), report.grossSales)
        assertEquals(big(15_000), report.refunds)
        assertEquals(BigInteger.ZERO, report.netSales)
        assertEquals(BigInteger.ZERO, report.total(DailyTotal.CASH_CHANGE))
        assertBalanced()
    }

    @Test fun concurrentRefundsAndRetriesWriteOnce() = runBlocking {
        val sale = sales.post(draft(), 15_000).draft.id
        val target = "sale:$sale"
        val operation = key()
        val candidates = listOf(
            async { runCatching { post(target, cash, operation) } },
            async { runCatching { post(target, cash) } },
        ).awaitAll()
        assertEquals(1, candidates.count { it.isSuccess })
        assertTrue(candidates.single { it.isFailure }.exceptionOrNull() is CashbookException)
        val winner = candidates.single { it.isSuccess }.getOrThrow().removePrefix("correction:")
        val results = List(2) { async { post(target, cash, winner) } }.awaitAll()
        assertEquals(1, results.distinct().size)
        reject { post(target, cash) }
        reject { post(target, cash.copy(reason = "Different"), winner) }
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal WHERE kind = 'REFUND'"))
        assertBalanced()
    }

    @Test fun pendingAndUnsupportedQrisRefundsAreRejected() = runBlocking {
        val id = qris(confirm = false)
        val proof = qrisRefund(LedgerAccount.QRIS_CLEARING)
        reject { post("sale:$id", proof) }
        val confirmed = qris(confirm = true)
        reject { post("sale:$confirmed", proof.copy(merchant = "WRONG_FIXTURE")) }
        reject { post("sale:$confirmed", proof.copy(provider = "WRONG.TEST")) }
        reject { post("sale:$confirmed", qrisRefund(LedgerAccount.BANK)) }
        assertEquals(0L, scalar("SELECT COUNT(*) FROM financial_correction"))
    }

    @Test fun unsettledRefundReducesClearingAndPreventsSettlement() = runBlocking {
        val id = qris(confirm = true)
        val qrisEvidence = sales.observeHistory().first().single().receipt
        post("sale:$id", qrisRefund(LedgerAccount.QRIS_CLEARING))
        val result = payouts.submit(key(), payout(), setOf(id), true, zone)
        assertEquals(SettlementState.UNRESOLVED, result.state)
        assertTrue(payouts.candidates().first().single().refunded)
        assertEquals(qrisEvidence, sales.observeHistory().first().single().receipt)
        assertEquals(BigInteger.ZERO, today().accounts.single { it.account == LedgerAccount.QRIS_CLEARING }.recordedBalance)
        assertBalanced()
    }

    @Test fun settledRefundDebitsBankAndBlocksPayoutReversal() = runBlocking {
        val id = qris(confirm = true)
        val settlement = settle(id)
        reject { post("sale:$id", qrisRefund(LedgerAccount.QRIS_CLEARING)) }
        reject { post("sale:$id", qrisRefund(LedgerAccount.BANK).copy(bankReference = "")) }
        val refund = post("sale:$id", qrisRefund(LedgerAccount.BANK))
        val row = cashbook.observeLedger().first().single { it.id == refund }
        assertEquals(-15_000L, row.amountFor(LedgerAccount.BANK))
        assertEquals(settlement, row.correction?.settlementId)
        reject { reversePayout(settlement) }
        val report = today()
        assertEquals(big(15_000), report.grossSales)
        assertEquals(big(15_000), report.refunds)
        assertEquals(big(1_000), report.total(DailyTotal.SETTLEMENT_FEES))
        assertEquals(big(-1_000), report.total(DailyTotal.BANK_CHANGE))
        assertBalanced()
    }

    @Test fun payoutReversalPreservesAllocationsAndReleasesOnlyActiveClaim() = runBlocking {
        val id = qris(confirm = true)
        val settlement = settle(id)
        val reversal = reversePayout(settlement)
        val original = payouts.history().first().single()
        assertEquals(setOf(id), original.paymentIds)
        assertEquals(reversal, original.reversalJournalId)
        assertNull(payouts.candidates().first().single().allocatedTo)
        reject { reversePayout(settlement) }
        assertEquals(SettlementState.UNRESOLVED, payouts.submit(key(), payout(), setOf(id), true, zone).state)
        assertEquals(BigInteger.ZERO, today().total(DailyTotal.SETTLEMENT_FEES))
        assertEquals(big(15_000), today().accounts.single { it.account == LedgerAccount.QRIS_CLEARING }.recordedBalance)
        val replacement = settle(id, "FIXTURE_PAYOUT_REPLACEMENT")
        reject { post("sale:$id", qrisRefund(LedgerAccount.BANK).copy(bankReference = "FIXTURE_BANK_REVERSAL")) }
        assertEquals(replacement, payouts.candidates().first().single().allocatedTo)
        assertBalanced()
    }

    @Test fun lateReversalFailureRollsBackJournalAndAllocation() = runBlocking {
        val id = qris(confirm = true)
        val settlement = settle(id)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER reject_release BEFORE UPDATE OF postedSaleId ON settlement_payment
            WHEN NEW.postedSaleId IS NULL BEGIN SELECT RAISE(ABORT, 'fixture failure'); END
        """.trimIndent())
        val operation = key()
        assertTrue(runCatching { reversePayout(settlement, operation) }.exceptionOrNull() is android.database.sqlite.SQLiteException)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal WHERE kind = 'REVERSAL'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM financial_correction"))
        assertEquals(settlement, payouts.candidates().first().single().allocatedTo)
        sql.execSQL("DROP TRIGGER reject_release")
        val proof = reversalEvidence()
        assertEquals(post("settlement:$settlement", proof, operation), post("settlement:$settlement", proof, operation))
        assertBalanced()
    }

    @Test fun manualReversalNetsExpenseAndRejectsOpeningOrNestedReversal() = runBlocking {
        val opening = cashbook.post(key(), MovementForm(MovementKind.OPENING, LedgerAccount.CASH, "25000", null, ""))
        val source = cashbook.post(key(), MovementForm(MovementKind.OPERATING_EXPENSE, LedgerAccount.CASH,
            "10000", ExpenseCategory.SUPPLIES, "Fixture expense"))
        val form = CorrectionInput("Pencatatan ganda", true)
        reject { post(opening.id, form) }
        val undo = post(source.id, form)
        reject { post(source.id, form) }
        reject { post(undo, form) }
        val entry = cashbook.observeLedger().first().single { it.id == undo }
        assertEquals(source.id, entry.correction?.originalId)
        assertEquals(BigInteger.ZERO, today().expenses[ExpenseCategory.SUPPLIES])
        assertEquals(big(25_000), today().accounts.single { it.account == LedgerAccount.CASH }.recordedBalance)
        assertBalanced()
    }

    @Test fun populatedV5MigrationKeepsPayoutEvidenceAndHistoricalData() = runBlocking {
        val cash = sales.post(draft(), 20_000)
        val qris = qris(confirm = true)
        val settlement = settle(qris)
        cashbook.post(key(), MovementForm(MovementKind.OTHER_INCOME, LedgerAccount.BANK, "3000", null, "Fixture"))
        val product = cash.draft.lines.single().product.id
        RoomCatalogRepository(db).archiveProduct(product)
        val schema = instrumentation.context.assets.open("id.umkmku.pos.data.CatalogDatabase/5.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        db.openHelper.writableDatabase.apply {
            execSQL("DROP TABLE financial_correction")
            execSQL("UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf(schema.getString("identityHash")))
            version = 5
        }
        db.close()
        open()
        assertEquals(cash, sales.observeHistory().first().single { it.draft.id == cash.draft.id }.receipt)
        assertEquals(settlement, payouts.candidates().first().single().allocatedTo)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM qris_evidence"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM product WHERE archived_at IS NOT NULL"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM financial_movement"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM financial_correction"))
        post("sale:${cash.draft.id}", this@CorrectionDatabaseTest.cash)
        assertBalanced()
    }

    private fun open() { db = Room.databaseBuilder(context, CatalogDatabase::class.java, name).build() }
    private suspend fun draft(): id.umkmku.pos.domain.CashDraft {
        val catalog = RoomCatalogRepository(db)
        val title = "Fixture ${key()}"
        EditCatalog(catalog).product(null, title, "15000", null)
        sales.changeQuantity(catalog.observe().first().products.single { it.name == title }.id, 1)
        return checkNotNull(sales.observeDraft().first())
    }

    private suspend fun qris(confirm: Boolean): String {
        val repo = QrisRepository(db, sales)
        if (repo.activeProfile().first() == null) repo.activate(parseStaticQris(fixturePayload()), true)
        val draft = draft()
        repo.begin(draft, checkNotNull(repo.activeProfile().first()).id)
        if (confirm) repo.confirm(draft.id, QrisEvidenceInput("15000", "FIXTURE_PAY_${key()}", fixtureMerchantTime(), true))
        return draft.id
    }

    private val cash = CorrectionInput("Pembeli mengembalikan pesanan", true, "15000", LedgerAccount.CASH)
    private fun qrisRefund(funding: LedgerAccount) = CorrectionInput(
        "Pengembalian penuh", true, "15000", funding, "INVALID.TEST", "FIXTURE_ONLY", "FIXTURE_REFUND",
        fixtureMerchantTime(), if (funding == LedgerAccount.BANK) "FIXTURE_BANK_REFUND" else "",
    )

    private fun payout(reference: String = "FIXTURE_PAYOUT") =
        PayoutEvidence("INVALID.TEST", "FIXTURE_ONLY", reference, "15000", "1000", "14000", fixtureMerchantTime(), true)

    private suspend fun settle(id: String, reference: String = "FIXTURE_PAYOUT"): String {
        val result = payouts.submit(key(), payout(reference), setOf(id), true, zone)
        assertEquals(SettlementState.POSTED, result.state)
        return result.id
    }

    private suspend fun reversePayout(id: String, operation: String = key()) =
        post("settlement:$id", reversalEvidence(), operation)

    private fun reversalEvidence() =
        CorrectionInput("Pencairan ditarik penyedia", true, "14000", LedgerAccount.BANK, "INVALID.TEST",
            "FIXTURE_ONLY", "FIXTURE_PAYOUT_REVERSAL", fixtureMerchantTime(), "FIXTURE_BANK_REVERSAL")

    private suspend fun post(target: String, form: CorrectionInput, operation: String = key()) =
        corrections.post(operation, target, form, zone)

    private suspend fun reject(block: suspend () -> Any?) {
        assertTrue(runCatching { block() }.exceptionOrNull() is CashbookException)
    }

    private suspend fun today() =
        LocalDate.now(zone).let { financialPeriod(cashbook.observeLedger().first(), it, it.plusDays(1), zone) }

    private fun assertBalanced() {
        assertEquals(0L, scalar("SELECT COUNT(*) FROM (SELECT journalId FROM journal_line GROUP BY journalId HAVING SUM(signedIdr) <> 0)"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
    }

    private fun scalar(sql: String): Long = db.openHelper.readableDatabase.query(sql).use {
        assertTrue(it.moveToFirst())
        it.getLong(0)
    }

    private fun key() = UUID.randomUUID().toString()
    private fun big(value: Long) = BigInteger.valueOf(value)
}
