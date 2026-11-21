package id.umkmku.pos

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.CashCheckoutException
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.EditCatalog
import id.umkmku.pos.domain.PriceReviewRequired
import id.umkmku.pos.domain.RecordCashSale
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
class CashCheckoutDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val file = "cash-test-${UUID.randomUUID()}.db"
    private lateinit var db: CatalogDatabase
    private lateinit var cash: CashSaleRepository
    private lateinit var catalog: RoomCatalogRepository
    private lateinit var record: RecordCashSale

    @Before fun setup() { open() }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(file)
    }

    @Test fun exactCashClosesDraftAndCreatesReceipt() = runBlocking {
        val draft = draft()
        val result = record(draft, "15000")
        assertEquals(0L, result.change)
        assertNull(cash.observeDraft().first())
        assertEquals(result, cash.observeHistory().first().single().receipt)
        assertEquals(1L, rows("payment"))
        assertEquals(1L, rows("journal"))
    }

    @Test fun insufficientCashLeavesDraftAndNoPosting() = runBlocking {
        val draft = draft()
        assertEquals("Uang tunai kurang.", rejected<CashCheckoutException> { record(draft, "14999") }.message)
        assertUnposted(draft)
    }

    @Test fun changeAndCashMovementUseNetCash() = runBlocking {
        val receipt = record(draft(), "20000")
        assertEquals(5000L, receipt.change)
        assertEquals(15000L, receipt.cashMovement)
        assertEquals(15000L, scalar("SELECT signedIdr FROM journal_line WHERE account = 'CASH'"))
    }

    @Test fun concurrentDoubleTapReturnsSameReceiptExactlyOnce() = runBlocking {
        val draft = draft()
        val results = List(2) { async { record(draft, "20000") } }.awaitAll()
        assertEquals(results[0], results[1])
        assertEquals(1L, rows("payment"))
        assertEquals(1L, rows("journal"))
        assertEquals(2L, rows("journal_line"))
        rejected<CashCheckoutException> { record(draft, "16000") }
        Unit
    }

    @Test fun retryAfterInterruptionBeforeAndAfterCommitUsesSameDraftKey() = runBlocking {
        val draft = draft()
        reopen()
        assertEquals(draft, cash.observeDraft().first())
        val committed = record(draft, "20000")
        // Simulate loss of the first response before the caller acknowledges it.
        reopen()
        assertEquals(committed, record(draft, "20000"))
        assertEquals(1L, rows("payment"))
        assertEquals(1L, rows("journal"))
    }

    @Test fun priceChangesNeedExplicitAcceptanceAndRevalidateAgain() = runBlocking {
        val draft = draft()
        val id = draft.lines.single().product.id
        EditCatalog(catalog).product(id, "Nasi uduk", "16000", null)
        val offered = rejected<PriceReviewRequired> { record(draft, "20000") }.proposed
        assertUnposted(draft)
        EditCatalog(catalog).product(id, "Nasi uduk", "17000", null)
        val latest = rejected<PriceReviewRequired> { cash.acceptPrices(offered) }.proposed
        assertUnposted(draft)
        cash.acceptPrices(latest)
        val receipt = record(latest, "20000")
        assertEquals(17000L, receipt.draft.total)
        assertEquals(3000L, receipt.change)
    }

    @Test fun relaunchReadsCommittedSnapshotsAfterRepriceAndArchive() = runBlocking {
        val draft = draft()
        val result = record(draft, "20000")
        val id = draft.lines.single().product.id
        EditCatalog(catalog).product(id, "Nama baru", "18000", null)
        EditCatalog(catalog).archiveProduct(id)
        reopen()
        assertEquals(result, cash.observeSale(draft.id).first()?.receipt)
        assertEquals("Nasi uduk", cash.observeHistory().first().single().draft.lines.single().product.name)
        assertEquals(15000L, cash.observeHistory().first().single().draft.total)
    }

    @Test fun ledgerContainsOneBalancedCashDebitAndSalesCredit() = runBlocking {
        record(draft(), "20000")
        assertEquals(1L, rows("journal"))
        assertEquals(0L, scalar("SELECT SUM(signedIdr) FROM journal_line"))
        assertEquals(15000L, scalar("SELECT signedIdr FROM journal_line WHERE account = 'CASH'"))
        assertEquals(-15000L, scalar("SELECT signedIdr FROM journal_line WHERE account = 'SALES'"))
    }

    @Test fun malformedOrOversizedAmountsNeverPost() = runBlocking {
        val draft = draft()
        for (value in listOf("", "0", "15.000", "-15000", "15000,50", "1000000000000", "9223372036854775808")) {
            rejected<CashCheckoutException> { record(draft, value) }
        }
        assertUnposted(draft)
    }

    @Test fun failureOnSecondJournalLineRollsBackAllWritesAndRetrySucceeds() = runBlocking {
        val draft = draft()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER fail_sales_line BEFORE INSERT ON journal_line
            WHEN NEW.account = 'SALES' BEGIN SELECT RAISE(ABORT, 'injected failure'); END
        """.trimIndent())
        rejected<SQLiteException> { record(draft, "20000") }
        assertUnposted(draft)
        sql.execSQL("DROP TRIGGER fail_sales_line")
        assertEquals(15000L, record(draft, "20000").cashMovement)
        assertEquals(2L, rows("journal_line"))
    }

    @Test fun archivedDraftProductCannotBePaidAndCanBeRemoved() = runBlocking {
        val draft = draft()
        val id = draft.lines.single().product.id
        EditCatalog(catalog).archiveProduct(id)
        rejected<CashCheckoutException> { record(draft, "20000") }
        assertUnposted(draft)
        cash.changeQuantity(id, -1, draft.id)
        assertEquals(emptyList<Any>(), cash.observeDraft().first()?.lines)
    }

    @Test fun roomMigratesCatalogVersionOneInPlace() = runBlocking {
        db.close()
        context.deleteDatabase(file)
        val old = instrumentation.context.assets.open("id.umkmku.pos.data.CatalogDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(file), null).use { legacy ->
            val tables = old.getJSONArray("entities")
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                val name = table.getString("tableName")
                legacy.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", name))
                val indices = table.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
                }
            }
            legacy.execSQL("INSERT INTO category VALUES ('food', 'Makanan', 'makanan', NULL)")
            legacy.execSQL("""
                INSERT INTO product (id, name, unit_idr, category_id, archived_at)
                VALUES ('legacy', 'Nasi uduk', 15000, 'food', NULL),
                       ('archived', 'Menu lama', 5000, 'food', 1234)
            """.trimIndent())
            legacy.version = 1
        }
        open()
        assertEquals("legacy", catalog.observe().first().products.single().id)
        assertEquals(1234L, scalar("SELECT archived_at FROM product WHERE id = 'archived'"))
        cash.changeQuantity("legacy", 1)
        assertEquals(15000L, record(checkNotNull(cash.observeDraft().first()), "15000").cashMovement)
    }

    private fun open() {
        db = Room.databaseBuilder(context, CatalogDatabase::class.java, file).build()
        cash = CashSaleRepository(db)
        catalog = RoomCatalogRepository(db)
        record = RecordCashSale(cash)
    }

    private fun reopen() {
        db.close()
        open()
    }

    private suspend fun draft(): CashDraft {
        EditCatalog(catalog).product(null, "Nasi uduk", "15000", null)
        cash.changeQuantity(catalog.observe().first().products.single().id, 1)
        return checkNotNull(cash.observeDraft().first())
    }

    private suspend fun assertUnposted(draft: CashDraft) {
        assertEquals(draft, cash.observeDraft().first())
        assertTrue(cash.observeHistory().first().isEmpty())
        assertEquals(0L, rows("payment"))
        assertEquals(0L, rows("journal"))
        assertEquals(0L, rows("journal_line"))
    }

    private fun rows(table: String): Long = scalar("SELECT COUNT(*) FROM $table")

    private fun scalar(sql: String): Long = db.openHelper.readableDatabase.query(sql).use {
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
