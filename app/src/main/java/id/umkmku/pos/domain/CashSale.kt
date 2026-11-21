package id.umkmku.pos.domain

import javax.inject.Inject

internal const val MAX_SALE_IDR = 999_999_999_999L

data class CashDraft(val id: String, val lines: List<CartLine>) {
    val total: Long get() = cartTotal(lines)
}

data class CashReceipt(val draft: CashDraft, val tender: Long, val paidAt: Long) {
    val change: Long get() = Math.subtractExact(tender, draft.total)
    val cashMovement: Long get() = Math.subtractExact(tender, change)
}

data class CashSale(val draft: CashDraft, val receipt: CashReceipt?)

open class CashCheckoutException(message: String) : IllegalArgumentException(message)
class PriceReviewRequired(val proposed: CashDraft) : CashCheckoutException("Harga produk berubah.")

internal fun parseCashTender(raw: String): Long? {
    val digits = raw.trim()
    if (digits.isEmpty() || digits.length > 12 || digits.any { it !in '0'..'9' }) return null
    return digits.toLongOrNull()?.takeIf { it in 1L..MAX_SALE_IDR }
}

interface CashPosting {
    suspend fun post(expected: CashDraft, tender: Long): CashReceipt
}

class RecordCashSale @Inject constructor(private val repository: CashPosting) {
    suspend operator fun invoke(draft: CashDraft, rawTender: String): CashReceipt {
        val tender = parseCashTender(rawTender)
            ?: throw CashCheckoutException("Masukkan jumlah tunai dalam Rupiah, tanpa titik atau koma.")
        return repository.post(draft, tender)
    }
}
