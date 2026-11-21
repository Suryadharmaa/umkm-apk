package id.umkmku.pos.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QrisTest {
    @Test fun crcMatchesCcittFalseKnownVector() {
        assertEquals("29B1", qrisCrc("123456789"))
    }

    @Test fun parserAcceptsStaticRupiahAndRejectsAlteredOrDynamicPayloads() {
        val good = payload()
        val asset = parseStaticQris(good)
        assertEquals("FIXTURE", asset.merchantName)
        assertEquals("FIXTURE_ONLY", asset.merchantId)
        for (invalid in listOf(
            good.replace("FIXTURE", "REPLACED"),
            payload("12"),
            payload(amount = "15"),
            good + "0001",
            "https://invalid.test",
        )) {
            assertThrows(CashCheckoutException::class.java) { parseStaticQris(invalid) }
        }
    }

    @Test fun evidenceRequiresAttestationWholeRupiahReferenceAndStrictLocalTime() {
        val zone = ZoneId.of("Asia/Jakarta")
        val input = QrisEvidenceInput("15000", " Fixture ", "2026-03-02 11:30", true)
        val validated = input.validate(zone)
        assertEquals(15000L, validated.amount)
        assertEquals("Fixture", validated.reference)
        assertEquals(Instant.parse("2026-03-02T04:30:00Z").toEpochMilli(), validated.observedAt)
        for (invalid in listOf(
            input.copy(merchantMatched = false),
            input.copy(observedAmount = "15.000"),
            input.copy(reference = " "),
            input.copy(merchantTime = "2026-02-30 11:30"),
        )) assertThrows(CashCheckoutException::class.java) { invalid.validate(zone) }
    }

    private fun payload(initiation: String = "11", amount: String? = null): String {
        fun tlv(id: String, value: String) = id + value.length.toString().padStart(2, '0') + value
        val data = tlv("00", "01") + tlv("01", initiation) + tlv("26", tlv("00", "INVALID.TEST")) +
            tlv("51", tlv("00", "ID.CO.QRIS.WWW") + tlv("02", "FIXTURE_ONLY")) +
            tlv("52", "0000") + tlv("53", "360") + (amount?.let { tlv("54", it) } ?: "") +
            tlv("58", "ID") + tlv("59", "FIXTURE") + tlv("60", "FIXTURE") + "6304"
        return data + qrisCrc(data)
    }
}
