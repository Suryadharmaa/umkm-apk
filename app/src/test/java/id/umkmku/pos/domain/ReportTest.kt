package id.umkmku.pos.domain

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReportTest {
    @Test fun dailyAndMonthlyWindowsRespectAllThreeBusinessZones() {
        val february = LocalDate.of(2026, 2, 1)
        for (zone in BusinessZone.entries) {
            val midnight = february.atStartOfDay(zone.zone).toInstant()
            val entries = listOf(
                sale("old", midnight.minusMillis(1), 100, LedgerAccount.CASH),
                sale("first", midnight, 200, LedgerAccount.QRIS_CLEARING),
                sale("second", february.plusDays(1).atStartOfDay(zone.zone).toInstant(), 300, LedgerAccount.CASH),
                sale("next", february.plusMonths(1).atStartOfDay(zone.zone).toInstant(), 400, LedgerAccount.CASH),
            )
            assertEquals(big(200), financialPeriod(entries, february, february.plusDays(1), zone.zone).grossSales)
            val monthly = ReportFilter(february.plusDays(14), ReportFrequency.MONTHLY, zone)
            val report = financialPeriod(entries, monthly.start, monthly.end, zone.zone)
            assertEquals(big(500), report.grossSales)
            assertEquals(big(300), report.total(DailyTotal.CASH_SALES))
            assertEquals(big(200), report.total(DailyTotal.QRIS_SALES))
            assertNull(report.accounts.single { it.account == LedgerAccount.CASH }.recordedBalance)
            assertEquals(big(400), report.accounts.single { it.account == LedgerAccount.CASH }.recordedChange)
        }
    }

    @Test fun aLaterRefundDoesNotRewritePriorSalesOrBalances() {
        val zone = BusinessZone.WIB.zone
        val date = LocalDate.of(2026, 2, 1)
        val start = date.atStartOfDay(zone).toInstant()
        val next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sale = sale("sale", start, 100, LedgerAccount.CASH)
        val refund = LedgerEntry("refund", next, next, 100, null, "sale", refundLines(LedgerAccount.CASH, 100),
            correction = CorrectionDetails(CorrectionKind.REFUND, sale.id, "Fixture", LedgerAccount.CASH,
                null, null, null, null, null))
        val transfer = Movement(MovementKind.TRANSFER, LedgerAccount.CASH, 25, null, "Fixture")
        val movement = LedgerEntry("transfer", next, next, 25, transfer, null, transfer.lines())
        val entries = listOf(movement, refund, sale)
        val earlier = financialPeriod(entries, date, date.plusDays(1), zone)
        assertEquals(big(100), earlier.netSales)
        assertEquals(big(100), earlier.accounts.single { it.account == LedgerAccount.CASH }.recordedChange)
        val later = financialPeriod(entries, date.plusDays(1), date.plusDays(2), zone)
        assertEquals(big(0), later.grossSales)
        assertEquals(big(-100), later.netSales)
        assertEquals(big(25), later.accounts.single { it.account == LedgerAccount.BANK }.recordedChange)
        assertFalse(financialPeriod(entries, date.minusDays(1), date, zone).hasActivity)
    }

    private fun sale(id: String, at: Instant, amount: Long, debit: LedgerAccount) = LedgerEntry(
        id, at.toEpochMilli(), at.toEpochMilli(), amount, null, id,
        listOf(LedgerLine(debit, amount), LedgerLine(LedgerAccount.SALES, -amount)),
    )

    private fun big(value: Long) = BigInteger.valueOf(value)
}
