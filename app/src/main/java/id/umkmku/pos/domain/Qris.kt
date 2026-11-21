package id.umkmku.pos.domain

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

internal const val PENDING_QRIS = "PENDING_QRIS"
internal const val MANUALLY_CONFIRMED = "MANUALLY_CONFIRMED"
internal const val MAX_QRIS_IDR = 10_000_000L
internal const val QRIS = "QRIS"
internal const val CASH = "CASH"

@ConsistentCopyVisibility
data class QrisAsset internal constructor(
    val payload: String,
    val merchantName: String,
    val merchantId: String,
    val image: ByteArray? = null,
)

data class QrisProfile(
    val id: String,
    val asset: QrisAsset,
)

data class PendingQris(val profileId: String, val startedAt: Long)

data class QrisReceipt(
    override val draft: CashDraft,
    override val paidAt: Long,
    val reference: String,
    val observedAt: Long,
) : SaleReceipt

data class QrisEvidenceInput(
    val observedAmount: String,
    val reference: String,
    val merchantTime: String,
    val merchantMatched: Boolean,
)

internal data class QrisEvidence(val amount: Long, val reference: String, val observedAt: Long)

internal fun QrisEvidenceInput.validate(zone: ZoneId): QrisEvidence {
    if (!merchantMatched) throw CashCheckoutException("Periksa pembayaran berhasil dan identitas merchant pada aplikasi penerima.")
    val amount = parseCashTender(observedAmount)
        ?: throw CashCheckoutException("Masukkan nominal dari aplikasi merchant dalam Rupiah.")
    val cleaned = reference.trim()
    if (cleaned.isEmpty() || cleaned.length > 100 || cleaned.any { it.isISOControl() }) {
        throw CashCheckoutException("Masukkan nomor referensi dari aplikasi merchant.")
    }
    val observedAt = try {
        val local = LocalDateTime.parse(merchantTime.trim(), merchantTimeFormat)
        val offset = zone.rules.getValidOffsets(local).single()
        local.toInstant(offset).toEpochMilli()
    } catch (_: Exception) {
        throw CashCheckoutException("Masukkan waktu transaksi dengan format TTTT-BB-HH JJ:MM.")
    }
    return QrisEvidence(amount, cleaned, observedAt)
}

private val merchantTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

internal fun parseStaticQris(raw: String): QrisAsset {
    val payload = raw.trim()
    val fields = parseTlv(payload) ?: invalidQris()
    val registry = fields["51"]?.let(::parseTlv)
    val merchantId = registry?.get("02").orEmpty()
    val name = fields["59"].orEmpty()
    val hasRouting = fields.any { (id, data) ->
        id.toInt() in 26..50 && !parseTlv(data)?.get("00").isNullOrBlank()
    }
    if (!payload.startsWith("000201") || (fields["01"] != null && fields["01"] != "11") ||
        fields["53"] != "360" || fields["58"] != "ID" || (54..57).any { fields[it.toString()] != null } ||
        fields["52"]?.let { it.length == 4 && it.all(Char::isDigit) } != true || fields["60"].isNullOrBlank() ||
        registry?.get("00") != "ID.CO.QRIS.WWW" || !hasRouting ||
        merchantId.isBlank() || merchantId.length > 32 || name.isBlank() || name.length > 25 ||
        payload.length < 8 || payload.takeLast(8).take(4) != "6304" ||
        !qrisCrc(payload.dropLast(4)).equals(payload.takeLast(4), ignoreCase = true)
    ) invalidQris()
    return QrisAsset(payload, name, merchantId.uppercase(Locale.ROOT))
}

private fun parseTlv(value: String): Map<String, String>? {
    if (value.isEmpty() || value.length > 2048 || value.any { it !in ' '..'~' }) return null
    val fields = linkedMapOf<String, String>()
    var offset = 0
    while (offset < value.length) {
        if (offset + 4 > value.length) return null
        val id = value.substring(offset, offset + 2)
        val size = value.substring(offset + 2, offset + 4)
        if ((id + size).any { it !in '0'..'9' } || fields.containsKey(id)) return null
        val end = offset + 4 + size.toInt()
        if (end > value.length || end == offset + 4) return null
        fields[id] = value.substring(offset + 4, end)
        offset = end
    }
    return fields
}

internal fun qrisCrc(content: String): String {
    var crc = 0xffff
    for (octet in content.toByteArray(StandardCharsets.UTF_8)) {
        crc = crc xor ((octet.toInt() and 255) shl 8)
        repeat(8) {
            crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xffff
        }
    }
    return crc.toString(16).uppercase(Locale.ROOT).padStart(4, '0')
}

private fun invalidQris(): Nothing = throw CashCheckoutException(
    "QRIS statis tidak dapat dibaca. Gunakan kode merchant dalam Rupiah dari penyedia pembayaran."
)
