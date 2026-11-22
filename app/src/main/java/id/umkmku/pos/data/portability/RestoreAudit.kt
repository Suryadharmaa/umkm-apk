package id.umkmku.pos.data.portability

import androidx.sqlite.db.SupportSQLiteDatabase
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.entry
import id.umkmku.pos.data.receipt
import id.umkmku.pos.domain.CorrectionInput
import id.umkmku.pos.domain.CorrectionKind
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.LedgerEntry
import id.umkmku.pos.domain.requireEvidence
import java.time.ZoneId
import id.umkmku.pos.domain.parseStaticQris
import id.umkmku.pos.domain.qrisProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun CatalogDatabase.auditSnapshot() {
    val sql = openHelper.writableDatabase
    sql.query("PRAGMA integrity_check(1)").use {
        check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
    }
    sql.requireNoRow("PRAGMA foreign_key_check")
    for (query in invariantViolations) sql.requireNoRow(query)
    val coroutine = currentCoroutineContext()
    forEachKeyBatch(sql, "sale") { keys ->
        val sales = cashSaleDao().saleBatch(keys)
        check(sales.size == keys.size)
        for (sale in sales) {
            coroutine.ensureActive()
            if (sale.payment != null) checkNotNull(sale.receipt())
        }
    }
    sql.query("SELECT payload, merchantId, merchantName FROM qris_profile").use {
        while (it.moveToNext()) {
            coroutine.ensureActive()
            val parsed = parseStaticQris(it.getString(0))
            check(parsed.merchantId == it.getString(1) && parsed.merchantName == it.getString(2))
        }
    }
    sql.query("""
        SELECT s.provider, profile.payload
        FROM settlement s JOIN settlement_payment link ON link.settlementId = s.id
        JOIN qris_order o ON o.saleId = link.saleId JOIN qris_profile profile ON profile.id = o.profileId
        WHERE s.state = 'POSTED'
    """).use {
        while (it.moveToNext()) {
            coroutine.ensureActive()
            check(qrisProvider(it.getString(1)) == it.getString(0))
        }
    }
    forEachCheckedJournal { entry, original ->
        if (entry.correction != null) auditCorrection(entry, checkNotNull(original))
    }
}

private suspend fun CatalogDatabase.auditCorrection(entry: LedgerEntry, original: LedgerEntry) {
    val row = checkNotNull(correctionDao().operation(entry.id))
    val kind = CorrectionKind.valueOf(row.kind)
    if (row.provider == null) {
        check(kind == CorrectionKind.MOVEMENT_REVERSAL ||
            (kind == CorrectionKind.REFUND && row.funding == LedgerAccount.CASH.name))
        return
    }
    val input = CorrectionInput(row.reason, true, checkNotNull(row.evidencedIdr).toString(),
        LedgerAccount.valueOf(checkNotNull(row.funding)), row.provider, checkNotNull(row.merchant),
        checkNotNull(row.reference), checkNotNull(row.evidenceTime), row.bankReference.orEmpty())
    val zone = ZoneId.of(checkNotNull(row.evidenceZone))
    val actual = when (kind) {
        CorrectionKind.REFUND -> {
            val saleId = checkNotNull(row.saleId)
            val sale = checkNotNull(cashSaleDao().sale(saleId))
            val order = checkNotNull(sale.qrisOrder)
            val profile = checkNotNull(qrisDao().profile(order.profileId))
            val observed = checkNotNull(sale.evidence)
            val settlement = row.settlementId?.let { checkNotNull(settlementDao().settlement(it)).row }
            input.requireEvidence(qrisProvider(profile.payload), profile.merchantId, observed.reference, original.amount,
                maxOf(observed.observedAt, settlement?.effectiveAt ?: observed.observedAt), row.recordedAt, zone)
        }
        CorrectionKind.SETTLEMENT_REVERSAL -> {
            val settlement = checkNotNull(settlementDao().settlement(checkNotNull(row.settlementId))).row
            val payout = checkNotNull(original.payout)
            input.requireEvidence(settlement.evidence.provider, settlement.evidence.merchantId, settlement.evidence.reference,
                payout.net, payout.effectiveAt, row.recordedAt, zone)
        }
        CorrectionKind.MOVEMENT_REVERSAL -> error("Unexpected provider evidence")
    }
    check(actual == row.effectiveAt)
}

internal suspend fun CatalogDatabase.forEachCheckedJournal(consumer: suspend (LedgerEntry, LedgerEntry?) -> Unit) {
    val sql = openHelper.writableDatabase
    val dao = cashbookDao()
    val coroutine = currentCoroutineContext()
    forEachKeyBatch(sql, "journal") { ids ->
        val records = dao.ledgerBatch(ids)
        check(records.size == ids.size)
        val originalIds = records.mapNotNull { it.correction?.originalJournalId }.distinct()
        val originals = if (originalIds.isEmpty()) emptyMap() else dao.ledgerBatch(originalIds).associate {
            check(it.correction == null)
            it.journal.id to it.entry()
        }
        for (record in records) {
            coroutine.ensureActive()
            val original = record.correction?.let { originals.getValue(it.originalJournalId) }
            consumer(record.entry(original), original)
        }
    }
}

private suspend inline fun forEachKeyBatch(
    sql: SupportSQLiteDatabase, table: String, action: (List<String>) -> Unit,
) {
    val coroutine = currentCoroutineContext()
    var last = ""
    while (true) {
        coroutine.ensureActive()
        val keys = sql.query("SELECT id FROM $table WHERE id > ? ORDER BY id LIMIT 128", arrayOf(last)).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
        if (keys.isEmpty()) return
        action(keys)
        last = keys.last()
    }
}

private fun SupportSQLiteDatabase.requireNoRow(query: String) =
    query(query).use { check(!it.moveToFirst()) }

private val invariantViolations = listOf(
    "SELECT 1 FROM category WHERE id = '' OR name = '' OR name_key = ''",
    "SELECT 1 FROM product WHERE id = '' OR name = '' OR unit_idr NOT BETWEEN 1 AND 999999999",
    "SELECT 1 FROM qris_profile WHERE id = '' OR (activeSlot IS NOT NULL AND activeSlot <> 1) OR length(image) > 2097152",
    """SELECT 1 FROM sale s LEFT JOIN payment p ON p.saleId = s.id LEFT JOIN qris_order q ON q.saleId = s.id
        WHERE s.id = '' OR (s.draftSlot IS NOT NULL AND (s.draftSlot <> 1 OR p.saleId IS NOT NULL OR q.saleId IS NOT NULL))
            OR (s.draftSlot IS NULL AND p.saleId IS NULL AND q.saleId IS NULL)""",
    """SELECT 1 FROM sale_line WHERE quantity NOT BETWEEN 1 AND 9999 OR unitIdr NOT BETWEEN 1 AND 999999999
        OR length(nameSnapshot) NOT BETWEEN 1 AND 80""",
    "SELECT 1 FROM sale_line GROUP BY saleId HAVING COUNT(*) > 200",
    """SELECT 1 FROM payment p LEFT JOIN journal j ON j.saleId = p.saleId AND j.kind = 'SALE'
        WHERE p.totalIdr NOT BETWEEN 1 AND 999999999999 OR j.id IS NULL OR p.method NOT IN ('CASH', 'QRIS')
            OR p.operationKey <> (CASE p.method WHEN 'CASH' THEN 'cash:' ELSE 'qris:' END || p.saleId)""",
    """SELECT 1 FROM qris_order q JOIN qris_profile profile ON profile.id = q.profileId
        LEFT JOIN payment p ON p.saleId = q.saleId LEFT JOIN qris_evidence e ON e.saleId = q.saleId
        WHERE q.totalIdr NOT BETWEEN 1 AND 10000000 OR q.status NOT IN ('PENDING_QRIS', 'MANUALLY_CONFIRMED')
            OR (q.status = 'PENDING_QRIS' AND
                (p.saleId IS NOT NULL OR e.saleId IS NOT NULL OR q.totalIdr <>
                    (SELECT COALESCE(SUM(item.unitIdr * item.quantity), 0) FROM sale_line item WHERE item.saleId = q.saleId)))
            OR (q.status = 'MANUALLY_CONFIRMED' AND (p.saleId IS NULL OR p.method <> 'QRIS' OR
                e.saleId IS NULL OR e.merchantId <> profile.merchantId OR e.observedIdr <> q.totalIdr OR
                p.totalIdr <> q.totalIdr OR e.reference = '' OR
                e.observedAt / 60000 NOT BETWEEN q.startedAt / 60000 AND p.paidAt / 60000))""",
    """SELECT 1 FROM journal j LEFT JOIN financial_correction c ON c.journalId = j.id
        LEFT JOIN settlement s ON s.journalId = j.id LEFT JOIN financial_movement m ON m.journalId = j.id
        WHERE j.id = '' OR (j.kind IN ('REFUND', 'REVERSAL')) <> (c.journalId IS NOT NULL)
            OR (j.kind = 'SETTLEMENT') <> (s.id IS NOT NULL)
            OR (j.kind IN ('OPENING', 'OTHER_INCOME', 'OPERATING_EXPENSE', 'OWNER_IN', 'OWNER_OUT', 'TRANSFER'))
                <> (m.journalId IS NOT NULL)""",
    """SELECT 1 FROM financial_movement m JOIN journal j ON j.id = m.journalId
        WHERE m.account NOT IN ('CASH', 'BANK') OR m.amountIdr NOT BETWEEN 0 AND 999999999999
            OR ((m.openingAccount IS NOT NULL) <> (j.kind = 'OPENING'))
            OR (m.openingAccount IS NOT NULL AND m.openingAccount <> m.account)
            OR (j.kind <> 'OPENING' AND m.amountIdr = 0)
            OR ((m.category IS NOT NULL) <> (j.kind = 'OPERATING_EXPENSE'))
            OR m.effectiveAt > m.recordedAt""",
    """SELECT 1 FROM settlement s LEFT JOIN financial_correction reversal ON reversal.originalJournalId = s.journalId
        WHERE s.id = '' OR s.state NOT IN ('PENDING', 'UNRESOLVED', 'POSTED')
            OR s.attested NOT IN (0, 1)
            OR (s.state <> 'POSTED' AND (s.postedReference IS NOT NULL OR s.journalId IS NOT NULL OR
                s.postedAt IS NOT NULL OR s.effectiveAt IS NOT NULL))
            OR (s.state = 'POSTED' AND (s.attested <> 1 OR s.postedReference IS NULL OR s.postedReference = ''
                OR s.postedReference <> s.reference OR s.journalId IS NULL OR s.postedAt IS NULL OR s.effectiveAt IS NULL
                OR s.effectiveAt > s.postedAt OR CAST(s.gross AS INTEGER) NOT BETWEEN 1 AND 999999999999
                OR s.gross <> CAST(CAST(s.gross AS INTEGER) AS TEXT) OR s.fee <> CAST(CAST(s.fee AS INTEGER) AS TEXT)
                OR s.net <> CAST(CAST(s.net AS INTEGER) AS TEXT) OR CAST(s.fee AS INTEGER) < 0
                OR CAST(s.net AS INTEGER) < 0 OR CAST(s.gross AS INTEGER) <> CAST(s.fee AS INTEGER) + CAST(s.net AS INTEGER)
                OR (SELECT COUNT(*) FROM settlement_payment link WHERE link.settlementId = s.id) NOT BETWEEN 1 AND 200
                OR CAST(s.gross AS INTEGER) <> (SELECT SUM(e.observedIdr) FROM settlement_payment link JOIN qris_evidence e
                    ON e.saleId = link.saleId WHERE link.settlementId = s.id)
                OR (reversal.journalId IS NOT NULL AND reversal.kind <> 'SETTLEMENT_REVERSAL')))""",
    """SELECT 1 FROM settlement_payment link JOIN settlement s ON s.id = link.settlementId
        JOIN qris_evidence e ON e.saleId = link.saleId
        LEFT JOIN financial_correction reversal ON reversal.originalJournalId = s.journalId
        LEFT JOIN financial_correction refund ON refund.saleId = link.saleId
        WHERE (link.postedSaleId IS NOT NULL AND (link.postedSaleId <> link.saleId OR s.state <> 'POSTED'
                OR reversal.journalId IS NOT NULL))
            OR (s.state = 'POSTED' AND (e.merchantId <> s.merchantId OR e.observedAt / 60000 > s.effectiveAt / 60000
                OR (reversal.journalId IS NULL AND link.postedSaleId IS NULL)
                OR (reversal.journalId IS NULL AND refund.journalId IS NOT NULL AND
                    (refund.settlementId IS NULL OR refund.settlementId <> s.id))))""",
    """SELECT 1 FROM financial_correction c
        LEFT JOIN settlement s ON s.id = c.settlementId
        WHERE c.reason = '' OR c.effectiveAt > c.recordedAt OR c.originalJournalId = c.journalId OR
            (c.settlementId IS NOT NULL AND (s.state <> 'POSTED' OR c.effectiveAt < s.effectiveAt)) OR
            ((c.provider IS NULL) <> (c.evidenceTime IS NULL)) OR ((c.provider IS NULL) <> (c.evidenceZone IS NULL)) OR
            (c.provider IS NOT NULL AND (c.reference IS NULL OR c.reference = '' OR c.merchant IS NULL))""",
)
