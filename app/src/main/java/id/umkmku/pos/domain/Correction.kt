package id.umkmku.pos.domain

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

enum class CorrectionKind(val label: String) {
    REFUND("Pengembalian dana"), MOVEMENT_REVERSAL("Pembalik mutasi"),
    SETTLEMENT_REVERSAL("Pembalik pencairan"),
}

data class CorrectionInput(
    val reason: String,
    val confirmed: Boolean,
    val amount: String = "",
    val funding: LedgerAccount? = null,
    val provider: String = "",
    val merchant: String = "",
    val reference: String = "",
    val occurrence: String = "",
    val bankReference: String = "",
)

data class CorrectionDetails(
    val kind: CorrectionKind,
    val originalId: String,
    val reason: String,
    val funding: LedgerAccount?,
    val provider: String?,
    val merchant: String?,
    val reference: String?,
    val settlementId: String?,
    val bankReference: String?,
)

internal fun CorrectionInput.normalized(): CorrectionInput {
    val note = reason.trim()
    if (!confirmed || note.isEmpty() || note.length > 160 || note.any(Char::isISOControl)) {
        throw CashbookException("Isi alasan maksimal 160 karakter dan beri konfirmasi.")
    }
    return copy(
        reason = note, amount = amount.trim(), provider = provider.trim().uppercase(Locale.ROOT),
        merchant = merchant.trim().uppercase(Locale.ROOT), reference = reference.trim(),
        occurrence = occurrence.trim(), bankReference = bankReference.trim(),
    )
}

internal fun CorrectionInput.requireEvidence(
    expectedProvider: String?, expectedMerchant: String, previousReference: String,
    expectedAmount: Long, earliest: Long, now: Long, zone: ZoneId,
): Long {
    if (expectedProvider == null || provider != expectedProvider || merchant != expectedMerchant) {
        throw CashbookException("Penyedia dan NMID harus cocok dengan bukti asli.")
    }
    if (reference.length !in 1..100 || reference.any(Char::isISOControl) || reference == previousReference) {
        throw CashbookException("Masukkan referensi koreksi resmi yang berbeda dari transaksi asli.")
    }
    requireAmount(expectedAmount)
    val hasDebit = funding == LedgerAccount.BANK && expectedAmount > 0
    if ((hasDebit && (bankReference.length !in 1..100 || bankReference.any(Char::isISOControl))) ||
        (!hasDebit && bankReference.isNotEmpty())) {
        throw CashbookException("Masukkan referensi mutasi bank hanya untuk debit bank yang benar-benar terjadi.")
    }
    val at = try {
        val local = LocalDateTime.parse(occurrence, correctionTimeFormat)
        local.toInstant(zone.rules.getValidOffsets(local).single()).toEpochMilli()
    } catch (_: RuntimeException) {
        throw CashbookException("Waktu bukti harus memakai format TTTT-BB-HH JJ:MM.")
    }
    if (at / 60_000 !in earliest / 60_000..now / 60_000) {
        throw CashbookException("Waktu bukti harus antara transaksi terkait dan saat ini.")
    }
    return at
}

internal fun CorrectionInput.requireAmount(expected: Long) {
    if (amount.length !in 1..12 || amount.any { it !in '0'..'9' } || amount.toLongOrNull() != expected) {
        throw CashbookException("Nominal bukti harus sama dengan jumlah penuh yang akan dicatat.")
    }
}

internal fun refundLines(funding: LedgerAccount, amount: Long): List<LedgerLine> =
    listOf(LedgerLine(LedgerAccount.SALES_RETURNS, amount), LedgerLine(funding, Math.negateExact(amount)))

internal fun inverse(lines: List<LedgerLine>) =
    lines.map { it.copy(amount = Math.negateExact(it.amount)) }

private val correctionTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
