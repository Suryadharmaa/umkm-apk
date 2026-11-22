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
import id.umkmku.pos.data.SettlementRepository
import id.umkmku.pos.domain.*
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
class SettlementDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "settlement-test-${UUID.randomUUID()}.db"
    private val zone = ZoneId.systemDefault()
    private lateinit var db: CatalogDatabase
    private lateinit var settlements: SettlementRepository
    private lateinit var sales: CashSaleRepository
    private lateinit var catalog: RoomCatalogRepository

    @Before fun setup() { open() }
    @After fun cleanup() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun confirmedPayoutMovesOnlyClearingToBankAndFee() = runBlocking {
        val ids = payments(2)
        val input = proof(gross = "30000", net = "29000")
        val result = submit(id(), input, ids)
        assertEquals(SettlementState.POSTED, result.state)
        assertEquals(ids, result.paymentIds)
        val entries = CashbookRepository(db).observeLedger().first()
        val book = cashbook(entries, LocalDate.now(zone), zone)
        assertEquals(big(30000), book.dailyTotals[DailyTotal.QRIS_SALES])
        assertEquals(big(29000), book.dailyTotals[DailyTotal.QRIS_BANK_CREDIT])
        assertEquals(big(1000), book.dailyTotals[DailyTotal.SETTLEMENT_FEES])
        assertNull(book.dailyTotals[DailyTotal.OTHER_INCOME])
        assertNull(book.dailyTotals[DailyTotal.EXPENSES])
        assertEquals(big(0), book.accounts.single { it.account == LedgerAccount.QRIS_CLEARING }.recordedBalance)
        assertEquals(big(29000), book.accounts.single { it.account == LedgerAccount.BANK }.recordedChange)
        assertEquals(0L, scalar("SELECT SUM(signedIdr) FROM journal_line"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM journal_line WHERE account = 'SALES'"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM qris_evidence"))
    }

    @Test fun pendingAndUnresolvedRecordsSurviveReopenAndCanBeReconciled() = runBlocking {
        val selected = payments()
        val key = id()
        val proof = proof()
        val incomplete = submit(key, proof.copy(reference = "", attested = false), selected, false)
        assertEquals(SettlementState.PENDING, incomplete.state)
        reopen()
        assertEquals(incomplete, settlements.history().first().single())
        val mismatch = submit(key, proof.copy(net = "13000"), selected)
        assertEquals(SettlementState.UNRESOLVED, mismatch.state)
        assertUnposted()
        reopen()
        assertEquals(mismatch, settlements.history().first().single())
        val paid = submit(key, proof, selected)
        assertEquals(SettlementState.POSTED, paid.state)
        reopen()
        assertEquals(paid, submit(key, proof.copy(reference = " ${proof.reference} "), selected))
        assertEquals(1L, settlementJournals())
    }

    @Test fun concurrentOverlappingSettlementsAllocateOnePaymentOnce() = runBlocking {
        val ids = payments()
        val outcomes = List(2) { index ->
            async { submit(id(), proof(reference = "FIXTURE_PAYOUT_$index"), ids) }
        }.awaitAll()
        assertEquals(1, outcomes.count { it.state == SettlementState.POSTED })
        assertEquals(1, outcomes.count { it.state == SettlementState.UNRESOLVED })
        assertEquals(1L, settlementJournals())
        assertEquals(1L, scalar("SELECT COUNT(postedSaleId) FROM settlement_payment"))
    }

    @Test fun samePayoutReferenceWithDisjointPaymentsPostsOnlyOnce() = runBlocking {
        val ids = payments(2).toList()
        val evidence = proof()
        val one = id()
        val two = id()
        val result = listOf(async { submit(one, evidence, setOf(ids[0])) },
            async { submit(two, evidence, setOf(ids[1])) }).awaitAll()
        val prior = result.single { it.state == SettlementState.POSTED }
        assertTrue(result.single { it.state == SettlementState.UNRESOLVED }.reason.orEmpty().contains("Referensi"))
        val retry = List(2) { async { submit(prior.id, evidence, prior.paymentIds) } }.awaitAll()
        assertEquals(listOf(prior, prior), retry)
        val conflicting = runCatching { submit(prior.id, evidence.copy(fee = "200"), prior.paymentIds) }
        assertTrue(conflicting.exceptionOrNull() is CashbookException)
        assertEquals(1L, settlementJournals())
    }

    @Test fun draftSelectionSurvivesLateAllocationFailureAndRetryIsAtomic() = runBlocking {
        val selected = payments()
        val key = id()
        val evidence = proof()
        val draft = submit(key, evidence, selected, false)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER reject_allocation BEFORE INSERT ON settlement_payment
            WHEN NEW.postedSaleId IS NOT NULL BEGIN SELECT RAISE(ABORT, 'injected failure'); END
        """.trimIndent())
        val attempt = runCatching { submit(key, evidence, selected) }
        assertTrue(attempt.exceptionOrNull() is SQLiteException)
        assertUnposted()
        assertEquals(draft, settlements.history().first().single())
        sql.execSQL("DROP TRIGGER reject_allocation")
        assertEquals(SettlementState.POSTED, submit(key, evidence, selected).state)
        assertEquals(1L, settlementJournals())
    }

    @Test fun wrongMerchantProviderAndNonQrisPaymentsNeverPost() = runBlocking {
        val candidate = payments()
        assertEquals(SettlementState.UNRESOLVED, submit(id(), proof().copy(merchantId = "OTHER_FIXTURE"), candidate).state)
        assertEquals(SettlementState.UNRESOLVED, submit(id(), proof().copy(provider = "WRONG.TEST"), candidate).state)
        val cash = sales.post(draft(), 15000)
        assertEquals(SettlementState.UNRESOLVED, submit(id(), proof(), setOf(cash.draft.id)).state)
        val qr = QrisRepository(db, sales)
        val pending = draft()
        qr.begin(pending, checkNotNull(qr.activeProfile().first()).id)
        assertEquals(SettlementState.UNRESOLVED, submit(id(), proof(), setOf(pending.id)).state)
        assertEquals(candidate, settlements.candidates().first().mapTo(mutableSetOf()) { it.saleId })
        assertUnposted()
    }

    @Test fun populatedV4MigrationKeepsSalesEvidenceCashbookAndArchivedIds() = runBlocking {
        db.close()
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("id.umkmku.pos.data.CatalogDatabase/4.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
            val tables = schema.getJSONArray("entities")
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                val tableName = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = table.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
            old.execSQL("INSERT INTO product (id,name,unit_idr,archived_at) VALUES ('archived','Fixture',15000,1234)")
            old.execSQL("INSERT INTO sale VALUES ('cash',NULL,1000), ('qris',NULL,1001)")
            old.execSQL("INSERT INTO sale_line VALUES ('cash','archived','Fixture cash',15000,1), ('qris','archived','Fixture QRIS',15000,1)")
            old.execSQL("INSERT INTO qris_profile VALUES ('profile','FIXTURE_ONLY','Fixture',?,NULL,1)", arrayOf(fixturePayload()))
            old.execSQL("INSERT INTO qris_order VALUES ('qris','profile',15000,1100,'MANUALLY_CONFIRMED')")
            old.execSQL("INSERT INTO qris_evidence VALUES ('qris','FIXTURE_ONLY','FIXTURE_V4',15000,1200)")
            old.execSQL("INSERT INTO payment VALUES ('cash','cash:cash',15000,20000,2000,'CASH'), ('qris','qris:qris',15000,NULL,2001,'QRIS')")
            old.execSQL("INSERT INTO journal VALUES ('sale:cash','cash','SALE'), ('sale:qris','qris','SALE'), ('opening',NULL,'OPENING')")
            old.execSQL("""
                INSERT INTO journal_line VALUES ('sale:cash','CASH',15000), ('sale:cash','SALES',-15000),
                    ('sale:qris','QRIS_CLEARING',15000), ('sale:qris','SALES',-15000),
                    ('opening','BANK',20000), ('opening','OWNER_EQUITY',-20000)
            """.trimIndent())
            old.execSQL("INSERT INTO financial_movement VALUES ('opening','BANK','BANK',20000,NULL,'Fixture opening',1000,1000)")
            old.version = 4
        }
        open()
        assertEquals(1234L, scalar("SELECT archived_at FROM product WHERE id = 'archived'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertEquals(6L, scalar("SELECT COUNT(*) FROM journal_line"))
        assertEquals("FIXTURE_V4", settlements.candidates().first().single().reference)
        assertEquals(5000L, (sales.observeHistory().first().single { it.draft.id == "cash" }.receipt as CashReceipt).change)
        assertEquals(SettlementState.POSTED, submit(id(), proof(), setOf("qris")).state)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        val entries = CashbookRepository(db).observeLedger().first()
        assertEquals(big(34000), cashbook(entries, LocalDate.now(zone), zone)
            .accounts.single { it.account == LedgerAccount.BANK }.recordedBalance)
    }

    private fun open() {
        db = Room.databaseBuilder(context, CatalogDatabase::class.java, name).build()
        settlements = SettlementRepository(db)
        sales = CashSaleRepository(db)
        catalog = RoomCatalogRepository(db)
    }

    private fun reopen() { db.close(); open() }
    private fun big(amount: Long) = BigInteger.valueOf(amount)
    private fun id() = UUID.randomUUID().toString()
    private fun proof(reference: String = "FIXTURE_PAYOUT", gross: String = "15000", net: String = "14000") =
        PayoutEvidence("INVALID.TEST", "FIXTURE_ONLY", reference, gross, "1000", net, fixtureMerchantTime(), true)
    private suspend fun submit(key: String, input: PayoutEvidence, selected: Set<String>, post: Boolean = true) =
        settlements.submit(key, input, selected, post, zone)

    private suspend fun payments(count: Int = 1): Set<String> {
        val qris = QrisRepository(db, sales)
        qris.activate(parseStaticQris(fixturePayload()), true)
        val profile = checkNotNull(qris.activeProfile().first())
        return List(count) { i ->
            val cart = draft()
            qris.begin(cart, profile.id)
            qris.confirm(cart.id, QrisEvidenceInput("15000", "FIXTURE_PAYMENT_$i", fixtureMerchantTime(), true))
            cart.id
        }.toSet()
    }

    private suspend fun draft(): CashDraft {
        val title = "Fixture ${id()}"
        EditCatalog(catalog).product(null, title, "15000", null)
        sales.changeQuantity(catalog.observe().first().products.single { it.name == title }.id, 1)
        return checkNotNull(sales.observeDraft().first())
    }

    private fun assertUnposted() {
        assertEquals(0L, settlementJournals())
        assertEquals(0L, scalar("SELECT COUNT(postedSaleId) FROM settlement_payment"))
    }

    private fun settlementJournals() = scalar("SELECT COUNT(*) FROM journal WHERE kind = 'SETTLEMENT'")
    private fun scalar(sql: String): Long = db.openHelper.readableDatabase.query(sql).use {
        assertTrue(it.moveToFirst())
        it.getLong(0)
    }
}
