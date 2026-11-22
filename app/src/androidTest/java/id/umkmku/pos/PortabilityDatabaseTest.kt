package id.umkmku.pos

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.data.CashbookRepository
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.CorrectionRepository
import id.umkmku.pos.data.JournalLineRow
import id.umkmku.pos.data.JournalRow
import id.umkmku.pos.data.PaymentRow
import id.umkmku.pos.data.ProductRow
import id.umkmku.pos.data.QrisProfileRow
import id.umkmku.pos.data.QrisRepository
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.data.SaleLineRow
import id.umkmku.pos.data.SaleRow
import id.umkmku.pos.data.SettlementRepository
import id.umkmku.pos.data.portability.BackupCipher
import id.umkmku.pos.data.portability.CsvKind
import id.umkmku.pos.data.portability.PortabilityRepository
import id.umkmku.pos.data.portability.RestoreNeedsEmptyDatabase
import id.umkmku.pos.data.portability.RestoreRejected
import id.umkmku.pos.data.portability.RestoreResult
import id.umkmku.pos.data.portability.businessIsEmpty
import id.umkmku.pos.data.portability.exportCsv
import id.umkmku.pos.data.portability.writeSnapshot
import id.umkmku.pos.domain.BusinessZone
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.CorrectionInput
import id.umkmku.pos.domain.EditCatalog
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.MovementForm
import id.umkmku.pos.domain.MovementKind
import id.umkmku.pos.domain.PayoutEvidence
import id.umkmku.pos.domain.QrisEvidenceInput
import id.umkmku.pos.domain.ReportFilter
import id.umkmku.pos.domain.ReportFrequency
import id.umkmku.pos.domain.parseStaticQris
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.StringWriter
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortabilityDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databases = mutableListOf<Pair<String, CatalogDatabase>>()
    private val password = "fixture portability only".toCharArray()

    @After fun cleanup() {
        for ((name, db) in databases) {
            db.close()
            context.deleteDatabase(name)
        }
        password.fill('\u0000')
    }

    @Test fun restoresMixedWalSnapshotAndPreservesEvidenceAndReceipts() = runBlocking {
        val source = database()
        val fixture = seedMixed(source)
        val oldHistory = CashSaleRepository(source).observeHistory().first()
        val oldLedger = CashbookRepository(source).observeLedger().first()
        val encrypted = backup(source)
        val repeat = backup(source)
        assertFalse(encrypted.contentEquals(repeat))
        val target = database()
        assertEquals(RestoreResult.REOPEN_VERIFIED, repo(target).restoreBackup(password, true) { encrypted.inputStream() })
        assertEquals(oldHistory, CashSaleRepository(target).observeHistory().first())
        assertEquals(oldLedger, CashbookRepository(target).observeLedger().first())
        assertEquals(fixture, scalar(target, "SELECT COUNT(*) FROM journal"))
        assertEquals(2L, scalar(target, "SELECT COUNT(*) FROM settlement"))
        assertEquals(1L, scalar(target, "SELECT COUNT(*) FROM financial_correction"))
        assertEquals(1L, scalar(target, "SELECT COUNT(*) FROM product WHERE archived_at IS NOT NULL"))
        assertTrue(runCatching { repo(target).restoreBackup(password, true) { repeat.inputStream() } }
            .exceptionOrNull() is RestoreNeedsEmptyDatabase)
        assertEquals(fixture, scalar(target, "SELECT COUNT(*) FROM journal"))
    }

    @Test fun rejectsWrongPasswordCorruptionAndOverwriteWithoutChangingDestination() = runBlocking {
        val source = database()
        seedMixed(source)
        val bytes = backup(source)
        val empty = database()
        val broken = bytes.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        for ((candidate, key) in listOf(bytes to "wrong password".toCharArray(), broken to password,
            bytes.copyOf(bytes.size - 5) to password)) {
            assertTrue(runCatching { repo(empty).restoreBackup(key, true) { candidate.inputStream() } }
                .exceptionOrNull() is RestoreRejected)
            assertTrue(empty.openHelper.writableDatabase.businessIsEmpty())
        }
        assertTrue(runCatching { repo(empty).restoreBackup(password, false) { bytes.inputStream() } }.isFailure)
        empty.catalogDao().insertProduct(ProductRow("original", "Original", 7000, null))
        assertTrue(runCatching { repo(empty).restoreBackup(password, true) { bytes.inputStream() } }
            .exceptionOrNull() is RestoreNeedsEmptyDatabase)
        assertEquals("original", RoomCatalogRepository(empty).observe().first().products.single().id)
    }

    @Test fun abortsInterruptedDocumentWritesReadsAndLateDatabaseImport() = runBlocking {
        val original = database()
        seedMixed(original)
        val repo = repo(original)
        assertTrue(runCatching {
            repo.createBackup(password) { object : OutputStream() { override fun write(b: Int) { throw IOException() } } }
        }.exceptionOrNull() is IOException)
        val bytes = backup(original)
        val destination = database()
        assertTrue(runCatching {
            repo(destination).restoreBackup(password, true) {
                object : ByteArrayInputStream(bytes) {
                    private var first = true
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (!first) throw IOException()
                        first = false
                        return super.read(b, off, minOf(len, 70))
                    }
                }
            }
        }.exceptionOrNull() is RestoreRejected)
        assertTrue(destination.openHelper.writableDatabase.businessIsEmpty())
        val sql = destination.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER fixture_abort BEFORE INSERT ON settlement_payment
            BEGIN SELECT RAISE(ABORT, 'fixture interruption'); END
        """.trimIndent())
        assertTrue(runCatching { repo(destination).restoreBackup(password, true) { bytes.inputStream() } }
            .exceptionOrNull() is RestoreRejected)
        assertTrue(sql.businessIsEmpty())
        sql.execSQL("DROP TRIGGER fixture_abort")
        assertEquals(RestoreResult.REOPEN_VERIFIED, repo(destination).restoreBackup(password, true) { bytes.inputStream() })
    }

    @Test fun rejectsAuthenticatedBrokenJournalAndMissingRelations() = runBlocking {
        val source = database()
        seedMixed(source)
        val target = database()
        val sql = source.openHelper.writableDatabase
        sql.execSQL("UPDATE journal_line SET signedIdr = signedIdr + 1 WHERE account = 'SALES'")
        val unbalanced = backup(source)
        assertTrue(runCatching { repo(target).restoreBackup(password, true) { unbalanced.inputStream() } }
            .exceptionOrNull() is RestoreRejected)
        assertTrue(target.openHelper.writableDatabase.businessIsEmpty())
        sql.execSQL("UPDATE journal_line SET signedIdr = signedIdr - 1 WHERE account = 'SALES'")
        sql.execSQL("UPDATE qris_order SET status = 'MANUALLY_CONFIRMED' WHERE status = 'PENDING_QRIS'")
        val invalid = backup(source)
        assertTrue(runCatching { repo(target).restoreBackup(password, true) { invalid.inputStream() } }
            .exceptionOrNull() is RestoreRejected)
        assertTrue(target.openHelper.writableDatabase.businessIsEmpty())
    }

    @Test fun authenticatedDuplicateRowsCannotPartiallyRestore() = runBlocking {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(BackupCipher.encrypt(password, bytes)).use { stream ->
            stream.writeInt(6)
            stream.writeInt(2)
            repeat(2) {
                for (value in listOf("duplicate", "Fixture", "fixture")) {
                    stream.writeByte(1)
                    val text = value.toByteArray(Charsets.UTF_8)
                    stream.writeInt(text.size)
                    stream.write(text)
                }
                stream.writeByte(0)
            }
            repeat(13) { stream.writeInt(0) }
        }
        val target = database()
        assertTrue(runCatching { repo(target).restoreBackup(password, true) { bytes.toByteArray().inputStream() } }
            .exceptionOrNull() is RestoreRejected)
        assertTrue(target.openHelper.writableDatabase.businessIsEmpty())
    }

    @Test fun supportsVersionFiveLogicalPayloadWithoutDroppingHistory() = runBlocking {
        val source = database()
        val sale = CashSaleRepository(source).post(draft(source, "Historical fixture"), 20000)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(BackupCipher.encrypt(password, bytes)).use {
            source.withTransaction { writeSnapshot(source.openHelper.writableDatabase, it, schema = 5) }
        }
        val target = database()
        assertEquals(RestoreResult.REOPEN_VERIFIED, repo(target).restoreBackup(password, true) { bytes.toByteArray().inputStream() })
        assertEquals(sale, CashSaleRepository(target).observeHistory().first().single().receipt)
        assertEquals(0L, scalar(target, "SELECT COUNT(*) FROM financial_correction"))
    }

    @Test fun csvIncludesPendingRefundedReversedAndExactValues() = runBlocking {
        val source = database()
        seedMixed(source)
        val zone = ZoneId.systemDefault()
        val payouts = SettlementRepository(source)
        val posted = payouts.history().first().single { it.posting != null }
        CorrectionRepository(source).post(key(), "settlement:${posted.id}", CorrectionInput(
            "Fixture reversal", true, "14000", LedgerAccount.BANK, "INVALID.TEST", "FIXTURE_ONLY",
            "FIXTURE_PAYOUT_REVERSAL", fixtureMerchantTime(), "FIXTURE_BANK_REVERSAL",
        ), zone)
        val filter = ReportFilter(LocalDate.now(BusinessZone.WIB.zone), ReportFrequency.MONTHLY, BusinessZone.WIB)
        suspend fun csv(kind: CsvKind) = StringWriter().also { writer ->
            source.withTransaction { source.exportCsv(writer, kind, filter) }
        }.toString()
        assertTrue(csv(CsvKind.SALES).contains("\"PENDING_QRIS\""))
        assertTrue(csv(CsvKind.SALES).contains("\"REFUNDED\""))
        assertTrue(csv(CsvKind.SALES).contains(",15000,20000,"))
        assertTrue(csv(CsvKind.RECEIPT_LINES).contains("\"'=Fixture cash\""))
        assertTrue(csv(CsvKind.REFUNDS).contains("\"CASH\",15000,"))
        assertTrue(csv(CsvKind.SETTLEMENTS).contains("\"REVERSED\",\"FIXTURE_PAYOUT\",15000,1000,14000,"))
        assertTrue(csv(CsvKind.SETTLEMENTS).contains("\"PENDING\""))
        assertTrue(csv(CsvKind.MOVEMENTS).contains("\"REVERSAL\""))
        assertTrue(csv(CsvKind.REPORT).contains("\"Asia/Jakarta\",\"NET_SALES\",15000,"))
    }

    @Test fun streamsThousandsOfSalesAndLargeImagesThroughFile() = runBlocking {
        val source = database()
        val now = System.currentTimeMillis()
        val asset = parseStaticQris(fixturePayload())
        source.withTransaction {
            source.catalogDao().insertProduct(ProductRow("bulk", "Synthetic bulk", 19000, null))
            repeat(6) { index ->
                source.qrisDao().insertProfile(QrisProfileRow("image-$index", asset.merchantId, asset.merchantName,
                    asset.payload, ByteArray(2 * 1024 * 1024) { index.toByte() }, null))
            }
            val dao = source.cashSaleDao()
            repeat(3000) { counter ->
                val id = "bulk-$counter"
                dao.insertSale(SaleRow(id, null, now))
                dao.insertLine(SaleLineRow(id, "bulk", "Synthetic bulk", 19000, 1))
                dao.insertPayment(PaymentRow(id, "cash:$id", 19000, 20000, now))
                dao.insertJournal(JournalRow("sale:$id", id, "SALE"))
                dao.insertJournalLines(listOf(JournalLineRow("sale:$id", "CASH", 19000), JournalLineRow("sale:$id", "SALES", -19000)))
            }
        }
        val file = File.createTempFile("bulk-fixture", ".encrypted", context.cacheDir)
        try {
            repo(source).createBackup(password) { file.outputStream() }
            assertTrue(file.length() > 12L * 1024 * 1024)
            val target = database()
            assertEquals(RestoreResult.REOPEN_VERIFIED, repo(target).restoreBackup(password, true) { file.inputStream() })
            assertEquals(3000L, scalar(target, "SELECT COUNT(*) FROM payment"))
            assertEquals(6L * 2 * 1024 * 1024, scalar(target, "SELECT SUM(length(image)) FROM qris_profile"))
        } finally {
            file.delete()
        }
    }

    private suspend fun seedMixed(db: CatalogDatabase): Long {
        val zone = ZoneId.systemDefault()
        val sales = CashSaleRepository(db)
        val qris = QrisRepository(db, sales)
        val payouts = SettlementRepository(db)
        val cash = sales.post(draft(db, "=Fixture cash"), 20000)
        RoomCatalogRepository(db).archiveProduct(cash.draft.lines.single().product.id)
        CorrectionRepository(db).post(key(), "sale:${cash.draft.id}",
            CorrectionInput("Fixture cash return", true, "15000", LedgerAccount.CASH), zone)
        qris.activate(parseStaticQris(fixturePayload()), true)
        val profile = checkNotNull(qris.activeProfile().first())
        val paid = draft(db, "Fixture paid QRIS")
        qris.begin(paid, profile.id)
        qris.confirm(paid.id, QrisEvidenceInput("15000", "FIXTURE_PAYMENT", fixtureMerchantTime(), true))
        payouts.submit(key(), PayoutEvidence("INVALID.TEST", "FIXTURE_ONLY", "FIXTURE_PAYOUT",
            "15000", "1000", "14000", fixtureMerchantTime(), true), setOf(paid.id), true, zone)
        payouts.submit(key(), PayoutEvidence(reference = "FIXTURE_DRAFT"), emptySet(), false, zone)
        qris.begin(draft(db, "Fixture pending QRIS"), profile.id)
        CashbookRepository(db).post(key(), MovementForm(MovementKind.OTHER_INCOME, LedgerAccount.BANK, "4000", null, "Fixture"))
        return scalar(db, "SELECT COUNT(*) FROM journal")
    }

    private suspend fun draft(db: CatalogDatabase, product: String): CashDraft {
        val catalog = RoomCatalogRepository(db)
        val sales = CashSaleRepository(db)
        EditCatalog(catalog).product(null, product, "15000", null)
        sales.changeQuantity(catalog.observe().first().products.single { it.name == product }.id, 1)
        return checkNotNull(sales.observeDraft().first())
    }

    private fun database(): CatalogDatabase {
        val name = "portability-${key()}.db"
        return Room.databaseBuilder(context, CatalogDatabase::class.java, name).build().also { databases.add(name to it) }
    }

    private fun repo(db: CatalogDatabase) = PortabilityRepository(context, db)
    private fun key() = UUID.randomUUID().toString()
    private suspend fun backup(db: CatalogDatabase) =
        ByteArrayOutputStream().also { bytes -> repo(db).createBackup(password) { bytes } }.toByteArray()

    private fun scalar(db: CatalogDatabase, query: String): Long = db.openHelper.readableDatabase.query(query).use {
        check(it.moveToFirst())
        it.getLong(0)
    }
}
