package id.umkmku.pos.data

import androidx.room.withTransaction
import id.umkmku.pos.domain.CartLine
import id.umkmku.pos.domain.CashCheckoutException
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.CashReceipt
import id.umkmku.pos.domain.CashPosting
import id.umkmku.pos.domain.CASH
import id.umkmku.pos.domain.MANUALLY_CONFIRMED
import id.umkmku.pos.domain.PENDING_QRIS
import id.umkmku.pos.domain.PendingQris
import id.umkmku.pos.domain.QRIS
import id.umkmku.pos.domain.QrisReceipt
import id.umkmku.pos.domain.SaleReceipt
import id.umkmku.pos.domain.SaleSnapshot
import id.umkmku.pos.domain.MAX_CART_PRODUCTS
import id.umkmku.pos.domain.MAX_CART_QUANTITY
import id.umkmku.pos.domain.MAX_SALE_IDR
import id.umkmku.pos.domain.PriceReviewRequired
import id.umkmku.pos.domain.Product
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class CashSaleRepository @Inject constructor(private val database: CatalogDatabase) : CashPosting {
    private val dao = database.cashSaleDao()

    fun observeDraft() = dao.observeDraft().map { it?.quote() }
    fun observeSale(id: String) = dao.observeSale(id).map { row ->
        row?.snapshot()
    }
    fun observeHistory() = dao.observeHistory().map { records -> records.map { it.snapshot() } }

    suspend fun changeQuantity(productId: String, delta: Int, expectedDraftId: String? = null) {
        require(delta == -1 || delta == 1)
        database.withTransaction {
            val active = dao.draft()
            if (expectedDraftId != null && active?.sale?.id != expectedDraftId) {
                throw CashCheckoutException("Keranjang ini sudah selesai.")
            }
            val existing = active?.lines?.find { it.productId == productId }
            if (delta < 0 && existing == null) return@withTransaction
            val product = if (delta > 0) dao.activeProducts(listOf(productId)).singleOrNull()
                ?: throw CashCheckoutException("Produk sudah diarsipkan.") else null
            val quantity = (existing?.quantity ?: 0) + delta
            if (quantity !in 0..MAX_CART_QUANTITY) throw CashCheckoutException("Jumlah produk melebihi batas.")
            if (existing == null && (active?.lines?.size ?: 0) >= MAX_CART_PRODUCTS) {
                throw CashCheckoutException("Keranjang sudah mencapai batas produk.")
            }

            val draft = active?.sale ?: SaleRow(UUID.randomUUID().toString(), 1, System.currentTimeMillis())
                .also { dao.insertSale(it) }
            when {
                quantity == 0 -> dao.deleteLine(checkNotNull(existing))
                existing == null -> {
                    checkNotNull(product)
                    dao.insertLine(SaleLineRow(draft.id, product.id, product.name, product.price, quantity))
                }
                else -> dao.updateLines(listOf(existing.copy(quantity = quantity)))
            }
            val updated = checkNotNull(dao.sale(draft.id)).quote()
            if (updated.total > MAX_SALE_IDR) throw CashCheckoutException("Total keranjang melebihi batas.")
        }
    }

    suspend fun reviewPrices(draftId: String) {
        database.withTransaction {
            val row = requireDraft(draftId)
            val current = row.copy(lines = refresh(row)).quote()
            if (current != row.quote()) throw PriceReviewRequired(current)
        }
    }

    suspend fun acceptPrices(proposed: CashDraft) {
        database.withTransaction {
            val row = requireDraft(proposed.id)
            val refreshed = refresh(row)
            val current = row.copy(lines = refreshed).quote()
            if (current != proposed) throw PriceReviewRequired(current)
            validateTotal(current)
            dao.updateLines(refreshed)
        }
    }

    override suspend fun post(expected: CashDraft, tender: Long): CashReceipt = database.withTransaction {
        if (tender !in 1..MAX_SALE_IDR) throw CashCheckoutException("Jumlah tunai di luar batas.")
        val row = dao.sale(expected.id) ?: throw CashCheckoutException("Keranjang tidak ditemukan.")
        // A persisted sale ID is the operation key. Replays must carry the original payload.
        row.receipt()?.let { previous ->
            if (previous !is CashReceipt || previous.tender != tender || previous.draft != expected) {
                throw CashCheckoutException("Penjualan ini sudah tercatat dengan rincian berbeda.")
            }
            return@withTransaction previous
        }
        val total = validatedDraftTotal(row, expected)
        if (tender < total) throw CashCheckoutException("Uang tunai kurang.")

        val receipt = CashReceipt(expected, tender, System.currentTimeMillis())
        dao.postPayment(PaymentRow(expected.id, "cash:${expected.id}", total, tender, receipt.paidAt), CASH)
        check(dao.closeDraft(expected.id) == 1)
        receipt
    }

    // Callers hold the same Room writer transaction through revalidation and posting.
    internal suspend fun validatedDraftTotal(row: SaleRecord, expected: CashDraft): Long {
        if (row.sale.draftSlot != 1 || row.payment != null || row.qrisOrder != null || row.quote() != expected) {
            throw CashCheckoutException("Keranjang berubah. Periksa kembali sebelum membayar.")
        }
        val current = row.copy(lines = refresh(row)).quote()
        if (current != expected) throw PriceReviewRequired(current)
        return validateTotal(current)
    }

    private suspend fun requireDraft(id: String): SaleRecord =
        dao.sale(id)?.takeIf { it.sale.draftSlot == 1 && it.payment == null }
            ?: throw CashCheckoutException("Keranjang ini sudah selesai.")

    private suspend fun refresh(row: SaleRecord): List<SaleLineRow> {
        val products = dao.activeProducts(row.lines.map { it.productId }).associateBy { it.id }
        return row.lines.map { line ->
            val product = products[line.productId]
                ?: throw CashCheckoutException("Produk ${line.nameSnapshot} sudah diarsipkan. Hapus dari keranjang.")
            if (product.price !in 1..999_999_999L) throw CashCheckoutException("Harga produk tidak valid.")
            line.copy(nameSnapshot = product.name, unitIdr = product.price)
        }
    }
}

private fun validateTotal(quote: CashDraft): Long = quote.total.takeIf { it in 1..MAX_SALE_IDR }
    ?: throw CashCheckoutException("Keranjang kosong atau total melebihi batas.")

internal fun SaleRecord.quote() = CashDraft(sale.id, lines.sortedBy { it.productId }.map {
    CartLine(Product(it.productId, it.nameSnapshot, it.unitIdr, null), it.quantity)
})

internal fun SaleRecord.receipt(): SaleReceipt? = payment?.let {
    val draft = quote()
    check(it.totalIdr == draft.total && it.totalIdr in 1..MAX_SALE_IDR)
    when (it.method) {
        CASH -> {
            check(qrisOrder == null && evidence == null)
            val tender = checkNotNull(it.tenderIdr)
            check(tender in it.totalIdr..MAX_SALE_IDR)
            CashReceipt(draft, tender, it.paidAt)
        }
        QRIS -> {
            check(it.tenderIdr == null && qrisOrder?.status == MANUALLY_CONFIRMED)
            val match = checkNotNull(evidence)
            check(match.observedIdr == it.totalIdr)
            QrisReceipt(draft, it.paidAt, match.reference, match.observedAt)
        }
        else -> error("Unknown payment method")
    }
}

private fun SaleRecord.snapshot() = SaleSnapshot(
    quote(), receipt(), qrisOrder?.takeIf { it.status == PENDING_QRIS }?.let {
        check(payment == null && evidence == null)
        PendingQris(it.profileId, it.startedAt)
    },
)

// Shared paid-only journal path. Both callers hold the writer transaction.
internal suspend fun CashSaleDao.postPayment(payment: PaymentRow, debitAccount: String) {
    insertPayment(payment)
    val journalId = "sale:${payment.saleId}"
    insertJournal(SaleJournalRow(journalId, payment.saleId, "SALE"))
    insertJournalLines(listOf(
        SaleJournalLineRow(journalId, debitAccount, payment.totalIdr),
        SaleJournalLineRow(journalId, "SALES", Math.negateExact(payment.totalIdr)),
    ))
}
