package id.umkmku.pos.domain

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

enum class SettlementState(val label: String) {
    PENDING("Menunggu bukti"), UNRESOLVED("Belum cocok"), POSTED("Tercatat"),
}

data class PayoutEvidence(
    val provider: String = "",
    val merchantId: String = "",
    val reference: String = "",
    val gross: String = "",
    val fee: String = "",
    val net: String = "",
    val payoutTime: String = "",
    val attested: Boolean = false,
) : java.io.Serializable {
    internal fun canonical(): PayoutEvidence = copy(
        provider = provider.trim().uppercase(Locale.ROOT),
        merchantId = merchantId.trim().uppercase(Locale.ROOT),
        reference = reference.trim(),
        gross = canonicalMoney(gross), fee = canonicalMoney(fee), net = canonicalMoney(net),
        payoutTime = payoutTime.trim(),
    )
}

data class SettlementPayment(
    val saleId: String,
    val amount: Long,
    val observedAt: Long,
    val reference: String,
    val merchantId: String,
    val merchantName: String,
    val provider: String?,
    val allocatedTo: String?,
)

data class SettlementRecord(
    val id: String,
    val evidence: PayoutEvidence,
    val paymentIds: Set<String>,
    val state: SettlementState,
    val reason: String?,
    val updatedAt: Long,
    val postedAt: Long?,
    val posting: PayoutPosting?,
)

data class PayoutPosting(val gross: Long, val fee: Long, val net: Long, val effectiveAt: Long) {
    fun lines(): List<LedgerLine> = listOf(
        LedgerLine(LedgerAccount.BANK, net),
        LedgerLine(LedgerAccount.SETTLEMENT_FEE, fee),
        LedgerLine(LedgerAccount.QRIS_CLEARING, Math.negateExact(gross)),
    ).filter { it.amount != 0L }
}

internal data class PayoutAssessment(
    val state: SettlementState,
    val reason: String? = null,
    val posting: PayoutPosting? = null,
)

internal fun assessPayout(
    evidence: PayoutEvidence,
    payments: List<SettlementPayment>,
    zone: ZoneId,
    now: Long,
    openingAt: Long?,
): PayoutAssessment {
    fun pending(message: String) = PayoutAssessment(SettlementState.PENDING, message)
    fun unresolved(message: String) = PayoutAssessment(SettlementState.UNRESOLVED, message)
    if (payments.isEmpty()) return pending("Pilih pembayaran QRIS yang sudah dikonfirmasi.")
    if (payments.any { it.allocatedTo != null }) return unresolved("Pembayaran yang dipilih sudah dicairkan.")
    if (evidence.provider.isBlank() || evidence.merchantId.isBlank() || evidence.reference.isBlank()) {
        return pending("Lengkapi penyedia, NMID dan referensi pencairan dari laporan penyedia.")
    }
    if (evidence.reference.length > 100 || evidence.reference.any { it.isISOControl() }) {
        return unresolved("Referensi pencairan tidak valid.")
    }
    if (payments.any { it.provider != evidence.provider || it.merchantId != evidence.merchantId }) {
        return unresolved("Penyedia atau NMID pada bukti berbeda dari pembayaran yang dipilih.")
    }
    if (listOf(evidence.gross, evidence.fee, evidence.net, evidence.payoutTime).any(String::isBlank)) {
        return pending("Lengkapi bruto, biaya aktual, kredit bank dan waktu pencairan.")
    }
    val gross = payoutMoney(evidence.gross)
    val fee = payoutMoney(evidence.fee)
    val net = payoutMoney(evidence.net)
    if (gross == null || fee == null || net == null || gross == 0L || fee > gross) {
        return unresolved("Nominal bruto, biaya atau kredit bank tidak valid.")
    }
    val selectedTotal = payments.fold(0L) { total, payment -> Math.addExact(total, payment.amount) }
    if (gross != selectedTotal || net != Math.subtractExact(gross, fee)) {
        return unresolved("Bruto harus sama dengan pilihan. Kredit bank harus sama dengan bruto dikurangi biaya.")
    }
    val at = try {
        val date = LocalDateTime.parse(evidence.payoutTime, payoutFormat)
        date.toInstant(zone.rules.getValidOffsets(date).single()).toEpochMilli()
    } catch (_: Exception) {
        return unresolved("Waktu pencairan harus memakai format TTTT-BB-HH JJ:MM.")
    }
    if (at / 60_000 > now / 60_000 || payments.any { it.observedAt / 60_000 > at / 60_000 }) {
        return unresolved("Waktu pencairan berada di luar rentang bukti pembayaran.")
    }
    if (openingAt != null && at < openingAt) {
        return unresolved("Pencairan mendahului saldo awal bank. Periksa sebelum mencatat agar tidak dihitung dua kali.")
    }
    if (!evidence.attested) return pending("Cocokkan laporan penyedia dan kredit aktual pada rekening bank usaha.")
    return PayoutAssessment(SettlementState.PENDING, posting = PayoutPosting(gross, fee, net, at))
}

private val payoutFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

private fun canonicalMoney(raw: String): String = payoutMoney(raw.trim())?.toString() ?: raw.trim()

private fun payoutMoney(value: String): Long? {
    if (value.isEmpty() || value.length > 12 || value.any { it !in '0'..'9' }) return null
    return value.toLongOrNull()?.takeIf { it in 0..MAX_SALE_IDR }
}
