package id.umkmku.pos

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CashbookRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.QrisRepository
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.CashReceipt
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.DailyTotal
import id.umkmku.pos.domain.EditCatalog
import id.umkmku.pos.domain.ExpenseCategory
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.LedgerEntry
import id.umkmku.pos.domain.MovementForm
import id.umkmku.pos.domain.MovementKind
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.QrisReceipt
import id.umkmku.pos.domain.cashbook
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
class CashbookDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val file = "cashbook-test-${UUID.randomUUID()}.db"
    private lateinit var db: CatalogDatabase
    private lateinit var repository: CashbookRepository
    private lateinit var sales: CashSaleRepository
    private lateinit var products: RoomCatalogRepository

    @Before fun setup() { open() }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(file)
    }

    @Test fun openingIsExplicitPerAccountEvenAfterEarlierSales() = runBlocking {
        val receipt = sales.post(draft(), 15000)
        var book = overview()
        assertNull(book.accounts[0].recordedBalance)
        assertNull(book.accounts[1].recordedBalance)
        assertEquals(big(15000), book.accounts[0].recordedChange)
        val opening = post(MovementKind.OPENING, LedgerAccount.CASH, 0)
        assertEquals(receipt.paidAt, opening.effectiveAt)
        assertTrue(opening.recordedAt >= opening.effectiveAt)
        book = overview()
        assertEquals(big(15000), book.accounts[0].recordedBalance)
        assertNull(book.accounts[1].recordedBalance)
        assertEquals(big(15000), book.dailyTotals[DailyTotal.CASH_SALES])
        assertEquals(big(15000), book.dailyTotals[DailyTotal.CASH_CHANGE])
        rejected<CashbookException> { post(MovementKind.OPENING, LedgerAccount.CASH, 10) }
        post(MovementKind.OPENING, LedgerAccount.BANK, 4000)
        assertEquals(big(4000), overview().accounts[1].recordedBalance)
    }

    @Test fun concurrentReplayPostsOnceAndSurvivesDatabaseReopen() = runBlocking {
        val id = operation()
        val input = form(MovementKind.OTHER_INCOME, LedgerAccount.BANK, 5000)
        val calls = listOf(
            async { repository.post(id, input) },
            async { repository.post(id, input.copy(amount = " 5000 ", note = " fixture ")) },
        ).awaitAll()
        assertEquals(calls[0], calls[1])
        reopen()
        assertEquals(calls[0], repository.post(id, input))
        rejected<CashbookException> { repository.post(id, input.copy(amount = "5100")) }
        assertEquals(1L, count("financial_movement"))
        assertEquals(1L, count("journal"))
        assertEquals(2L, count("journal_line"))
        assertEquals(0L, number("SELECT SUM(signedIdr) FROM journal_line"))
    }

    @Test fun competingOpeningOperationsCannotBothInitializeAnAccount() = runBlocking {
        val outcomes = List(2) { async { runCatching { post(MovementKind.OPENING, LedgerAccount.BANK, 0) } } }.awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is CashbookException)
        assertEquals(1L, count("financial_movement"))
    }

    @Test fun rollbackAfterDebitRemovesHeaderMetadataAndAllLines() = runBlocking {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER reject_cashbook_credit BEFORE INSERT ON journal_line
            WHEN NEW.account = 'OTHER_INCOME' BEGIN SELECT RAISE(ABORT, 'injected failure'); END
        """.trimIndent())
        val id = operation()
        val request = form(MovementKind.OTHER_INCOME, LedgerAccount.CASH, 2000)
        rejected<SQLiteException> { repository.post(id, request) }
        listOf("financial_movement", "journal", "journal_line").forEach { assertEquals(0L, count(it)) }
        sql.execSQL("DROP TRIGGER reject_cashbook_credit")
        assertEquals(2000L, repository.post(id, request).amountFor(LedgerAccount.CASH))
        assertEquals(0L, number("SELECT SUM(signedIdr) FROM journal_line"))
    }

    @Test fun transfersInBothDirectionsConserveCombinedFundsAndExcludeRevenue() = runBlocking {
        post(MovementKind.OPENING, LedgerAccount.CASH, 10000)
        post(MovementKind.OPENING, LedgerAccount.BANK, 20000)
        post(MovementKind.TRANSFER, LedgerAccount.CASH, 4500)
        post(MovementKind.TRANSFER, LedgerAccount.BANK, 1000)
        val book = overview()
        assertEquals(big(6500), book.accounts[0].recordedBalance)
        assertEquals(big(23500), book.accounts[1].recordedBalance)
        assertEquals(big(4500), book.dailyTotals[DailyTotal.CASH_TO_BANK])
        assertEquals(big(1000), book.dailyTotals[DailyTotal.BANK_TO_CASH])
        assertEquals(big(-3500), book.dailyTotals[DailyTotal.CASH_CHANGE])
        assertEquals(big(3500), book.dailyTotals[DailyTotal.BANK_CHANGE])
        assertNull(book.dailyTotals[DailyTotal.OTHER_INCOME])
        assertNull(book.dailyTotals[DailyTotal.CASH_SALES])
    }

    @Test fun salesExpensesIncomeAndCapitalCoexistWithoutDoubleCounting() = runBlocking {
        val qris = QrisRepository(db, sales)
        qris.activate(parseStaticQris(fixturePayload()), true)
        val profile = checkNotNull(qris.activeProfile().first())
        val cash = sales.post(draft(), 20000)
        val qr = draft()
        qris.begin(qr, profile.id)
        assertEquals(1, overview().entries.size)
        qris.confirm(qr.id, QrisEvidenceInput("15000", "FIXTURE_CASHBOOK", fixtureMerchantTime(), true))
        val expense = post(MovementKind.OPERATING_EXPENSE, LedgerAccount.CASH, 2500, ExpenseCategory.RENT)
        post(MovementKind.OTHER_INCOME, LedgerAccount.BANK, 4000)
        post(MovementKind.OWNER_IN, LedgerAccount.CASH, 8000)
        post(MovementKind.OWNER_OUT, LedgerAccount.BANK, 1000)
        val book = overview()
        assertEquals(6, book.entries.size)
        assertEquals(1, book.entries.count { it.saleId == cash.draft.id })
        assertEquals(1, book.entries.count { it.saleId == qr.id })
        assertEquals(big(15000), book.dailyTotals[DailyTotal.CASH_SALES])
        assertEquals(big(15000), book.dailyTotals[DailyTotal.QRIS_SALES])
        assertEquals(big(4000), book.dailyTotals[DailyTotal.OTHER_INCOME])
        assertEquals(big(2500), book.dailyTotals[DailyTotal.EXPENSES])
        assertEquals(big(2500), book.dailyExpenses[ExpenseCategory.RENT])
        assertEquals(big(8000), book.dailyTotals[DailyTotal.OWNER_IN])
        assertEquals(big(1000), book.dailyTotals[DailyTotal.OWNER_OUT])
        assertEquals(big(20500), book.accounts[0].recordedChange)
        assertEquals(big(3000), book.accounts[1].recordedChange)
        assertEquals(big(15000), book.accounts[2].recordedBalance)
        assertTrue(book.entries.all { entry -> entry.lines.sumOf { it.amount } == 0L })
        reopen()
        assertEquals(expense, overview().entries.single { it.id == expense.id })
    }

    @Test fun populatedV3MigratesCashConfirmedAndPendingQrisWithExistingJournal() = runBlocking {
        db.close()
        context.deleteDatabase(file)
        val schema = instrumentation.context.assets.open("id.umkmku.pos.data.CatalogDatabase/3.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(file), null).use { old ->
            val tableArray = schema.getJSONArray("entities")
            for (i in 0 until tableArray.length()) {
                val table = tableArray.getJSONObject(i)
                val name = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", name))
                val indices = table.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
                }
            }
            old.execSQL("INSERT INTO product (id,name,unit_idr,archived_at) VALUES ('old-product','Archived',5000,1234)")
            old.execSQL("INSERT INTO qris_profile VALUES ('old-profile','FIXTURE_ONLY','FIXTURE',?,NULL,1)", arrayOf(fixturePayload()))
            for (id in listOf("cash-sale", "qris-sale", "pending-sale")) {
                old.execSQL("INSERT INTO sale VALUES (?,NULL,1000)", arrayOf(id))
                old.execSQL("INSERT INTO sale_line VALUES (?,'old-product','Old snapshot',15000,1)", arrayOf(id))
            }
            old.execSQL("INSERT INTO qris_order VALUES ('qris-sale','old-profile',15000,1200,'MANUALLY_CONFIRMED')")
            old.execSQL("INSERT INTO qris_order VALUES ('pending-sale','old-profile',15000,1300,'PENDING_QRIS')")
            old.execSQL("INSERT INTO qris_evidence VALUES ('qris-sale','FIXTURE_ONLY','FIXTURE_V3',15000,1400)")
            old.execSQL("INSERT INTO payment VALUES ('cash-sale','cash:cash-sale',15000,20000,2000,'CASH')")
            old.execSQL("INSERT INTO payment VALUES ('qris-sale','qris:qris-sale',15000,NULL,2001,'QRIS')")
            old.execSQL("INSERT INTO journal VALUES ('sale:cash-sale','cash-sale','SALE'), ('sale:qris-sale','qris-sale','SALE')")
            old.execSQL("""
                INSERT INTO journal_line VALUES ('sale:cash-sale','CASH',15000), ('sale:cash-sale','SALES',-15000),
                    ('sale:qris-sale','QRIS_CLEARING',15000), ('sale:qris-sale','SALES',-15000)
            """.trimIndent())
            old.version = 3
        }
        open()
        val history = sales.observeHistory().first()
        val cash = history.single { it.receipt is CashReceipt }.receipt as CashReceipt
        val qr = history.single { it.receipt is QrisReceipt }.receipt as QrisReceipt
        assertEquals(5000L, cash.change)
        assertEquals("old-product", cash.draft.lines.single().product.id)
        assertEquals("Old snapshot", qr.draft.lines.single().product.name)
        assertEquals("FIXTURE_V3", qr.reference)
        assertEquals("old-profile", history.single { it.pending != null }.pending?.profileId)
        assertEquals(1234L, number("SELECT archived_at FROM product WHERE id = 'old-product'"))
        assertEquals(4L, count("journal_line"))
        assertEquals(0L, number("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        val entry = post(MovementKind.OTHER_INCOME, LedgerAccount.BANK, 2000)
        assertNull(entry.saleId)
        assertEquals(3, overview().entries.size)
    }

    private fun open() {
        db = Room.databaseBuilder(context, CatalogDatabase::class.java, file).build()
        repository = CashbookRepository(db)
        sales = CashSaleRepository(db)
        products = RoomCatalogRepository(db)
    }

    private fun reopen() { db.close(); open() }

    private suspend fun draft(): CashDraft {
        val name = "Produk fixture ${operation()}"
        EditCatalog(products).product(null, name, "15000", null)
        sales.changeQuantity(products.observe().first().products.single { it.name == name }.id, 1)
        return checkNotNull(sales.observeDraft().first())
    }

    private fun operation() = UUID.randomUUID().toString()
    private fun big(value: Long) = BigInteger.valueOf(value)
    private fun form(kind: MovementKind, account: LedgerAccount, amount: Long, category: ExpenseCategory? = null) =
        MovementForm(kind, account, amount.toString(), category, "fixture")
    private suspend fun post(kind: MovementKind, account: LedgerAccount, amount: Long, category: ExpenseCategory? = null): LedgerEntry =
        repository.post(operation(), form(kind, account, amount, category))

    private suspend fun overview() = cashbook(repository.observeLedger().first(), LocalDate.now(), ZoneId.systemDefault())
    private fun count(table: String) = number("SELECT COUNT(*) FROM $table")
    private fun number(sql: String): Long = db.openHelper.readableDatabase.query(sql).use {
        assertTrue(it.moveToFirst())
        it.getLong(0)
    }

    private inline fun <reified T : Throwable> rejected(action: () -> Unit): T {
        try {
            action()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
