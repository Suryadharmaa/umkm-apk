package id.umkmku.pos.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SettlementTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val observed = Instant.parse("2026-11-21T05:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-11-21T07:00:00Z").toEpochMilli()
    private val payment = SettlementPayment("fixture", 100000, observed, "FIXTURE", "FIXTURE_ONLY", "Fixture", "INVALID.TEST", null)
    private val proof = PayoutEvidence("INVALID.TEST", "FIXTURE_ONLY", "FIXTURE_PAYOUT", "100000", "1000", "99000", "2026-11-21 13:00", true)

    @Test fun balancedPostingKeepsFeesSeparate() {
        val posting = checkNotNull(assess(proof).posting)
        assertEquals(listOf(
            LedgerLine(LedgerAccount.BANK, 99000),
            LedgerLine(LedgerAccount.SETTLEMENT_FEE, 1000),
            LedgerLine(LedgerAccount.QRIS_CLEARING, -100000),
        ), posting.lines())
        val zeroFee = checkNotNull(assess(proof.copy(fee = "0", net = "100000")).posting)
        assertEquals(2, zeroFee.lines().size)
        assertEquals(0L, zeroFee.lines().sumOf { it.amount })
    }

    @Test fun mismatchAndMissingProofAreDistinctStates() {
        assertEquals(SettlementState.PENDING, assess(proof.copy(attested = false)).state)
        assertNull(assess(proof.copy(attested = false)).posting)
        for (invalid in listOf(
            proof.copy(net = "98000"), proof.copy(gross = "99"), proof.copy(fee = "100001"),
            proof.copy(fee = "1.000"), proof.copy(net = "-1"), proof.copy(payoutTime = "2026-11-21 11:59"),
            proof.copy(payoutTime = "2026-11-21 14:01"),
        )) assertEquals(SettlementState.UNRESOLVED, assess(invalid).state)
        assertEquals(SettlementState.UNRESOLVED, assess(proof, now).state)
    }

    private fun assess(evidence: PayoutEvidence, openingAt: Long? = null) =
        assessPayout(evidence, listOf(payment), zone, now, openingAt)
}
