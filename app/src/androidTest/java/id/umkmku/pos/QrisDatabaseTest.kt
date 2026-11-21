package id.umkmku.pos

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.QrisRepository
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.data.decodeQrisImage
import id.umkmku.pos.data.qrisBitmap
import id.umkmku.pos.domain.CashCheckoutException
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.CashReceipt
import id.umkmku.pos.domain.EditCatalog
import id.umkmku.pos.domain.PriceReviewRequired
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.QrisReceipt
import id.umkmku.pos.domain.parseStaticQris
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
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
class QrisDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val file = "qris-test-${UUID.randomUUID()}.db"
    private lateinit var db: CatalogDatabase
    private lateinit var sales: CashSaleRepository
    private lateinit var products: RoomCatalogRepository
    private lateinit var qris: QrisRepository

    @Before fun setup() { open() }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(file)
    }

    @Test fun pendingSurvivesReopenAndAllowsAnotherCashSale() = runBlocking {
        val draft = begin()
        assertNull(sales.observeDraft().first())
        assertUnposted(draft)
        reopen()
        val pending = checkNotNull(sales.observeSale(draft.id).first()?.pending)
        assertNotNull(qris.profile(pending.profileId).first())
        val cashDraft = draft()
        assertNotEquals(draft.id, cashDraft.id)
        sales.post(cashDraft, 20000)
        val history = sales.observeHistory().first()
        assertEquals(2, history.size)
        assertTrue(history.any { it.draft.id == draft.id && it.pending == pending && it.receipt == null })
        assertTrue(history.any { it.receipt is CashReceipt })
    }

    @Test fun exactEvidencePostsOnlyClearingAndRevenue() = runBlocking {
        val draft = begin()
        val receipt = qris.confirm(draft.id, evidence())
        assertEquals(15000L, receipt.draft.total)
        assertEquals("MANUALLY_CONFIRMED", status(draft.id))
        assertEquals(receipt, sales.observeHistory().first().single().receipt)
        assertEquals(1L, count("payment"))
        assertEquals(1L, count("journal"))
        assertEquals(2L, count("journal_line"))
        assertEquals(15000L, number("SELECT signedIdr FROM journal_line WHERE account = 'QRIS_CLEARING'"))
        assertEquals(-15000L, number("SELECT signedIdr FROM journal_line WHERE account = 'SALES'"))
        assertEquals(0L, number("SELECT SUM(signedIdr) FROM journal_line"))
        assertEquals(0L, number("SELECT COUNT(*) FROM journal_line WHERE account IN ('CASH','BANK')"))
        assertEquals(15000L, number("SELECT observedIdr FROM qris_evidence"))
    }

    @Test fun mismatchesAndMissingMerchantAttestationRemainPending() = runBlocking {
        val draft = begin()
        val inputs = listOf(
            evidence().copy(merchantMatched = false),
            evidence().copy(observedAmount = "14999"),
            evidence().copy(observedAmount = "15001"),
            evidence().copy(reference = ""),
            evidence().copy(merchantTime = fixtureMerchantTime(LocalDateTime.now().minusHours(2))),
            evidence().copy(merchantTime = fixtureMerchantTime(LocalDateTime.now().plusHours(2))),
        )
        inputs.forEach { rejected<CashCheckoutException> { qris.confirm(draft.id, it) } }
        assertUnposted(draft)
    }

    @Test fun concurrentConfirmOfSameOrderIsIdempotentAcrossReopen() = runBlocking {
        val draft = begin()
        val input = evidence()
        val outcomes = List(2) { async { qris.confirm(draft.id, input) } }.awaitAll()
        assertEquals(outcomes[0], outcomes[1])
        reopen()
        assertEquals(outcomes[0], qris.confirm(draft.id, input))
        rejected<CashCheckoutException> { qris.confirm(draft.id, input.copy(reference = "FIXTURE_ONLY_OTHER")) }
        assertEquals(1L, count("qris_evidence"))
        assertEquals(1L, count("payment"))
        assertEquals(1L, count("journal"))
    }

    @Test fun duplicateReferenceRacesAcrossProfileReplacementsAndPendingOrders() = runBlocking {
        val first = begin()
        val original = sales.observeSale(first.id).first()?.pending?.profileId
        qris.activate(parseStaticQris(fixturePayload("FIXTURE PROFIL KEDUA")), true)
        val second = beginWithActive()
        assertNotEquals(original, sales.observeSale(second.id).first()?.pending?.profileId)
        assertEquals("FIXTURE UJI NONBAYAR", qris.profile(checkNotNull(original)).first()?.asset?.merchantName)
        val input = evidence()
        val outcomes = listOf(
            async { runCatching { qris.confirm(first.id, input) } },
            async { runCatching { qris.confirm(second.id, input.copy(reference = " ${input.reference} ")) } },
        ).awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is CashCheckoutException)
        assertEquals(1L, count("qris_evidence"))
        assertEquals(1L, count("payment"))
        assertEquals(2L, count("journal_line"))
        val history = sales.observeHistory().first()
        assertEquals(1, history.count { it.pending != null })
        assertEquals(1, history.count { it.receipt is QrisReceipt })
    }

    @Test fun confirmationRollsBackEvidenceStatusPaymentAndJournalOnFailure() = runBlocking {
        val draft = begin()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER fail_qris_post BEFORE INSERT ON journal_line
            WHEN NEW.account = 'SALES' BEGIN SELECT RAISE(ABORT, 'injected failure'); END
        """.trimIndent())
        val input = evidence()
        rejected<SQLiteException> { qris.confirm(draft.id, input) }
        assertUnposted(draft)
        sql.execSQL("DROP TRIGGER fail_qris_post")
        assertEquals(15000L, qris.confirm(draft.id, input).draft.total)
        assertEquals(1L, count("journal"))
    }

    @Test fun startingRevalidatesPriceAndRetriesOnePendingOrder() = runBlocking {
        val profile = activate()
        val old = draft()
        val product = old.lines.single().product
        EditCatalog(products).product(product.id, product.name, "17000", null)
        val refreshed = rejected<PriceReviewRequired> { qris.begin(old, profile) }.proposed
        assertEquals(0L, count("qris_order"))
        sales.acceptPrices(refreshed)
        qris.begin(refreshed, profile)
        qris.begin(refreshed, profile)
        rejected<CashCheckoutException> { sales.post(refreshed, 20000) }
        assertEquals(1L, count("qris_order"))
        assertUnposted(refreshed)
    }

    @Test fun sourceAttestationAndDeactivationPreserveOldPendingProfile() = runBlocking {
        val asset = parseStaticQris(fixturePayload())
        rejected<CashCheckoutException> { qris.activate(asset, false) }
        assertNull(qris.activeProfile().first())
        val first = begin()
        val original = checkNotNull(sales.observeSale(first.id).first()?.pending?.profileId)
        qris.deactivate()
        assertNull(qris.activeProfile().first())
        val second = draft()
        rejected<CashCheckoutException> { qris.begin(second, original) }
        assertEquals(first.id, qris.confirm(first.id, evidence()).draft.id)
        assertNotNull(qris.profile(original).first())
    }

    @Test fun imageImportRoundTripsExactPayloadAndKeepsOriginalBytes() = runBlocking {
        val asset = parseStaticQris(fixturePayload())
        val image = checkNotNull(qrisBitmap(asset))
        val png = ByteArrayOutputStream().also { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            .toByteArray()
        image.recycle()
        val decoded = decodeQrisImage(png)
        assertEquals(asset.payload, decoded.payload)
        assertArrayEquals(png, decoded.image)
        qris.activate(decoded, true)
        reopen()
        assertArrayEquals(png, qris.activeProfile().first()?.asset?.image)
        rejected<CashCheckoutException> { decodeQrisImage(byteArrayOf(1, 2, 3)) }
        Unit
    }

    @Test fun imageImportRejectsMultipleCodes() {
        val first = qrisBitmap(parseStaticQris(fixturePayload()))
        val second = qrisBitmap(parseStaticQris(fixturePayload("FIXTURE SECOND QR")))
        val sheet = Bitmap.createBitmap(first.width + second.width, first.height, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(Color.WHITE)
            drawBitmap(first, 0f, 0f, null)
            drawBitmap(second, first.width.toFloat(), 0f, null)
        }
        val png = ByteArrayOutputStream().also { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        first.recycle()
        second.recycle()
        sheet.recycle()
        rejected<CashCheckoutException> { decodeQrisImage(png.toByteArray()) }
    }

    @Test fun cashAndQrisCannotBothClaimSameDraft() = runBlocking {
        val profile = activate()
        val draft = draft()
        val results = listOf(
            async { runCatching { sales.post(draft, 15000); Unit } },
            async { runCatching { qris.begin(draft, profile) } },
        ).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1L, count("payment") + count("qris_order"))
        assertEquals(count("payment"), count("journal"))
        assertNull(sales.observeDraft().first())
    }

    @Test fun qrisCeilingKeepsOverLimitSaleAsDraft() = runBlocking {
        val profile = activate()
        val draft = draft("10000001")
        rejected<CashCheckoutException> { qris.begin(draft, profile) }
        assertEquals(draft, sales.observeDraft().first())
        assertEquals(0L, count("qris_order"))
    }

    @Test fun migrationFromV2PreservesCashPaymentJournalAndArchivedCatalogId() = runBlocking {
        db.close()
        context.deleteDatabase(file)
        val schema = instrumentation.context.assets.open("id.umkmku.pos.data.CatalogDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(file), null).use { old ->
            val tables = schema.getJSONArray("entities")
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                val name = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", name))
                val indices = table.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
                }
            }
            old.execSQL("INSERT INTO product (id, name, unit_idr, archived_at) VALUES ('old-product', 'Nama lama', 5000, 1234)")
            old.execSQL("INSERT INTO sale VALUES ('old-sale', NULL, 1000)")
            old.execSQL("INSERT INTO sale_line VALUES ('old-sale','old-product','Snapshot awal',15000,1)")
            old.execSQL("INSERT INTO payment VALUES ('old-sale','cash:old-sale',15000,20000,2000)")
            old.execSQL("INSERT INTO journal VALUES ('sale:old-sale','old-sale','SALE')")
            old.execSQL("INSERT INTO journal_line VALUES ('sale:old-sale','CASH',15000), ('sale:old-sale','SALES',-15000)")
            old.version = 2
        }
        open()
        val history = sales.observeHistory().first().single()
        val receipt = history.receipt as CashReceipt
        assertEquals("old-sale", receipt.draft.id)
        assertEquals("old-product", receipt.draft.lines.single().product.id)
        assertEquals("Snapshot awal", receipt.draft.lines.single().product.name)
        assertEquals(2000L, receipt.paidAt)
        assertEquals(5000L, receipt.change)
        assertEquals(1234L, number("SELECT archived_at FROM product WHERE id = 'old-product'"))
        assertEquals(15000L, number("SELECT signedIdr FROM journal_line WHERE account = 'CASH'"))
        assertEquals(0L, number("SELECT SUM(signedIdr) FROM journal_line"))
        assertEquals(0L, number("SELECT COUNT(*) FROM pragma_foreign_key_check"))
    }

    private fun open() {
        db = Room.databaseBuilder(context, CatalogDatabase::class.java, file).build()
        sales = CashSaleRepository(db)
        products = RoomCatalogRepository(db)
        qris = QrisRepository(db, sales)
    }

    private fun reopen() {
        db.close()
        open()
    }

    private suspend fun activate(): String {
        qris.activate(parseStaticQris(fixturePayload()), true)
        return checkNotNull(qris.activeProfile().first()).id
    }

    private suspend fun draft(amount: String = "15000"): CashDraft {
        val name = "Barang uji ${UUID.randomUUID()}"
        EditCatalog(products).product(null, name, amount, null)
        val id = products.observe().first().products.single { it.name == name }.id
        sales.changeQuantity(id, 1)
        return checkNotNull(sales.observeDraft().first())
    }

    private suspend fun begin(): CashDraft {
        activate()
        return beginWithActive()
    }

    private suspend fun beginWithActive(): CashDraft {
        val active = checkNotNull(qris.activeProfile().first())
        return draft().also { qris.begin(it, active.id) }
    }

    private fun evidence() = QrisEvidenceInput("15000", "FIXTURE_ONLY_REF", fixtureMerchantTime(), true)

    private suspend fun assertUnposted(draft: CashDraft) {
        val stored = checkNotNull(sales.observeSale(draft.id).first())
        assertEquals(draft, stored.draft)
        assertNotNull(stored.pending)
        assertNull(stored.receipt)
        assertEquals("PENDING_QRIS", status(draft.id))
        for (table in listOf("payment", "qris_evidence", "journal", "journal_line")) assertEquals(0L, count(table))
    }

    private fun status(id: String): String = db.openHelper.readableDatabase
        .query("SELECT status FROM qris_order WHERE saleId = ?", arrayOf(id)).use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }
    private fun count(table: String): Long = number("SELECT COUNT(*) FROM $table")
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
