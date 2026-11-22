package id.umkmku.pos.data.portability

import android.database.Cursor
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.domain.DailyTotal
import id.umkmku.pos.domain.ExpenseCategory
import id.umkmku.pos.domain.FinancialPeriodAccumulator
import id.umkmku.pos.domain.ReportFilter
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class CsvKind(val label: String, val fileName: String) {
    SALES("Penjualan", "penjualan"),
    RECEIPT_LINES("Rincian produk terjual", "rincian-struk"),
    REFUNDS("Pengembalian dana", "refund"),
    SETTLEMENTS("Pencairan QRIS", "pencairan"),
    MOVEMENTS("Mutasi dan jurnal", "mutasi"),
    REPORT("Laporan keuangan", "laporan"),
}

internal suspend fun CatalogDatabase.exportCsv(output: Writer, kind: CsvKind, filter: ReportFilter) {
    val csv = CsvWriter(output)
    val zone = filter.businessZone.zone
    if (kind == CsvKind.REPORT) {
        val accumulator = FinancialPeriodAccumulator(filter.start, filter.end, zone)
        forEachCheckedJournal(accumulator::add)
        val result = accumulator.result()
        csv.row("period_start", "period_end_exclusive", "business_zone", "metric", "amount_idr", "status")
        fun metric(metric: String, amount: Number?, status: String? = null) {
            csv.row(filter.start.toString(), filter.end.toString(), zone.id, metric, amount, status)
        }
        metric("GROSS_SALES", result.grossSales)
        metric("REFUNDS", result.refunds)
        metric("NET_SALES", result.netSales)
        DailyTotal.entries.forEach { metric(it.name, result.total(it)) }
        ExpenseCategory.entries.forEach { metric("EXPENSE_${it.name}", result.expenses[it] ?: 0) }
        result.accounts.forEach {
            metric("ENDING_${it.account.name}", it.recordedBalance, if (it.recordedBalance == null) "UNKNOWN_OPENING" else "RECORDED")
            metric("RECORDED_CHANGE_${it.account.name}", it.recordedChange)
        }
        return
    }
    val definition = when (kind) {
        CsvKind.SALES -> ExportDefinition(
            "sale_id status method opened_at paid_at payment_observed_at provider_reference original_total_idr tender_idr refund_journal_id",
            """
            SELECT s.id, CASE WHEN refund.journalId IS NOT NULL THEN 'REFUNDED' WHEN p.saleId IS NOT NULL THEN 'PAID'
                WHEN q.saleId IS NOT NULL THEN q.status ELSE 'DRAFT' END, COALESCE(p.method, CASE WHEN q.saleId IS NOT NULL THEN 'QRIS' END),
                s.openedAt, p.paidAt, e.observedAt, e.reference, COALESCE(p.totalIdr, q.totalIdr,
                    (SELECT COALESCE(SUM(l.unitIdr * l.quantity),0) FROM sale_line l WHERE l.saleId = s.id)),
                p.tenderIdr, refund.journalId
            FROM sale s LEFT JOIN payment p ON p.saleId = s.id LEFT JOIN qris_order q ON q.saleId = s.id
                LEFT JOIN qris_evidence e ON e.saleId = s.id LEFT JOIN financial_correction refund ON refund.saleId = s.id
            ORDER BY s.openedAt, s.id
            """, setOf(3, 4, 5),
        )
        CsvKind.RECEIPT_LINES -> ExportDefinition(
            "sale_id status product_id product_name_snapshot unit_price_idr quantity subtotal_idr opened_at paid_at",
            """
            SELECT s.id, CASE WHEN r.saleId IS NOT NULL THEN 'REFUNDED' WHEN p.saleId IS NOT NULL THEN 'PAID'
                WHEN q.saleId IS NOT NULL THEN q.status ELSE 'DRAFT' END, l.productId, l.nameSnapshot,
                l.unitIdr, l.quantity, l.unitIdr * l.quantity, s.openedAt, p.paidAt
            FROM sale_line l JOIN sale s ON s.id = l.saleId LEFT JOIN payment p ON p.saleId = s.id
                LEFT JOIN qris_order q ON q.saleId = s.id LEFT JOIN financial_correction r ON r.saleId = s.id
            ORDER BY s.openedAt, s.id, l.productId
            """, setOf(7, 8),
        )
        CsvKind.REFUNDS -> ExportDefinition(
            "refund_journal_id sale_id original_journal_id method amount_idr funding_account settlement_id effective_at recorded_at provider_reference bank_debit_reference reason",
            """
            SELECT c.journalId, c.saleId, c.originalJournalId, p.method, c.evidencedIdr, c.funding, c.settlementId,
                c.effectiveAt, c.recordedAt, c.reference, c.bankReference, c.reason
            FROM financial_correction c JOIN payment p ON p.saleId = c.saleId WHERE c.kind = 'REFUND'
            ORDER BY c.effectiveAt, c.journalId
            """, setOf(7, 8),
        )
        CsvKind.SETTLEMENTS -> ExportDefinition(
            "settlement_id status payout_reference recorded_gross_idr recorded_fee_idr recorded_net_idr draft_gross draft_fee draft_net updated_at effective_at posted_at reason journal_id reversal_journal_id payment_count",
            """
            SELECT s.id, CASE WHEN c.journalId IS NOT NULL THEN 'REVERSED' ELSE s.state END, s.reference,
                CASE WHEN s.state = 'POSTED' THEN CAST(s.gross AS INTEGER) END,
                CASE WHEN s.state = 'POSTED' THEN CAST(s.fee AS INTEGER) END,
                CASE WHEN s.state = 'POSTED' THEN CAST(s.net AS INTEGER) END,
                CASE WHEN s.state <> 'POSTED' THEN s.gross END,
                CASE WHEN s.state <> 'POSTED' THEN s.fee END,
                CASE WHEN s.state <> 'POSTED' THEN s.net END,
                s.updatedAt, s.effectiveAt, s.postedAt, s.reason, s.journalId, c.journalId,
                (SELECT COUNT(*) FROM settlement_payment link WHERE link.settlementId = s.id)
            FROM settlement s LEFT JOIN financial_correction c ON c.originalJournalId = s.journalId
            ORDER BY s.updatedAt, s.id
            """, setOf(9, 10, 11),
        )
        CsvKind.MOVEMENTS -> ExportDefinition(
            "journal_id kind status sale_id original_journal_id correction_journal_id effective_at recorded_at account signed_idr expense_category note",
            """
            SELECT j.id, j.kind, CASE WHEN undo.journalId IS NOT NULL THEN 'REVERSED' ELSE 'POSTED' END,
                j.saleId, fix.originalJournalId, undo.journalId,
                COALESCE(fix.effectiveAt, m.effectiveAt, s.effectiveAt, p.paidAt),
                COALESCE(fix.recordedAt, m.recordedAt, s.postedAt, p.paidAt),
                l.account, l.signedIdr, COALESCE(m.category, source.category), COALESCE(fix.reason, m.note)
            FROM journal j JOIN journal_line l ON l.journalId = j.id
                LEFT JOIN financial_correction fix ON fix.journalId = j.id
                LEFT JOIN financial_correction undo ON undo.originalJournalId = j.id
                LEFT JOIN financial_movement m ON m.journalId = j.id
                LEFT JOIN financial_movement source ON source.journalId = fix.originalJournalId
                LEFT JOIN settlement s ON s.journalId = j.id LEFT JOIN payment p ON p.saleId = j.saleId
            ORDER BY 7, j.id, l.account
            """, setOf(6, 7),
        )
        CsvKind.REPORT -> error("Report is generated from the journal")
    }
    csv.row(*(definition.headers.split(" ") + "business_zone").toTypedArray())
    val coroutine = currentCoroutineContext()
    openHelper.writableDatabase.query(definition.query).use { cursor ->
        while (cursor.moveToNext()) {
            coroutine.ensureActive()
            val row = Array<Any?>(cursor.columnCount + 1) { col ->
                if (col == cursor.columnCount) zone.id else cursor.exportValue(col, definition.timestamps, zone)
            }
            csv.row(*row)
        }
    }
}

private data class ExportDefinition(val headers: String, val query: String, val timestamps: Set<Int> = emptySet())

private fun Cursor.exportValue(col: Int, times: Set<Int>, zone: ZoneId): Any? {
    if (isNull(col)) return null
    if (col in times) return Instant.ofEpochMilli(getLong(col)).atZone(zone).toOffsetDateTime().toString()
    return when (getType(col)) {
        Cursor.FIELD_TYPE_INTEGER -> getLong(col)
        Cursor.FIELD_TYPE_STRING -> getString(col)
        else -> error("Unexpected CSV type")
    }
}
