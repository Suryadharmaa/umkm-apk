package id.umkmku.pos.domain

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CashbookTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")
    private val date = LocalDate.of(2026, 11, 21)
    private val noon = date.atTime(12, 0).atZone(jakarta).toInstant().toEpochMilli()

    @Test fun everyMovementTypeMapsToBalancedAccounts() {
        val expected = mapOf(
            MovementKind.OPENING to (LedgerAccount.CASH to LedgerAccount.OWNER_EQUITY),
            MovementKind.OTHER_INCOME to (LedgerAccount.CASH to LedgerAccount.OTHER_INCOME),
            MovementKind.OPERATING_EXPENSE to (LedgerAccount.OPERATING_EXPENSE to LedgerAccount.CASH),
            MovementKind.OWNER_IN to (LedgerAccount.CASH to LedgerAccount.OWNER_EQUITY),
            MovementKind.OWNER_OUT to (LedgerAccount.OWNER_EQUITY to LedgerAccount.CASH),
            MovementKind.TRANSFER to (LedgerAccount.BANK to LedgerAccount.CASH),
        )
        for ((kind, accounts) in expected) {
            val category = ExpenseCategory.RENT.takeIf { kind == MovementKind.OPERATING_EXPENSE }
            val form = MovementForm(kind, LedgerAccount.CASH, "1000", category, " fixture ")
            val value = form.validate()
            assertEquals(listOf(LedgerLine(accounts.first, 1000), LedgerLine(accounts.second, -1000)), value.lines())
            assertEquals(0L, value.lines().sumOf { it.amount })
            assertEquals("fixture", value.note)
        }
    }

    @Test fun validationAcceptsExplicitZeroOnlyForOpening() {
        val opening = MovementForm(MovementKind.OPENING, LedgerAccount.BANK, "0", null, "")
        assertEquals(0L, opening.validate().amount)
        assertThrows(CashbookException::class.java) { opening.copy(kind = MovementKind.OTHER_INCOME).validate() }
        assertThrows(CashbookException::class.java) { opening.copy(account = LedgerAccount.QRIS_CLEARING).validate() }
        assertThrows(CashbookException::class.java) { opening.copy(note = "x".repeat(161)).validate() }
        assertThrows(CashbookException::class.java) { opening.copy(category = ExpenseCategory.RENT).validate() }
        assertThrows(CashbookException::class.java) {
            opening.copy(kind = MovementKind.OPERATING_EXPENSE, amount = "1").validate()
        }
        for (invalid in listOf("-1", "1.500", "1,50", "+1", "1000000000000", "")) {
            assertNull(movementAmount(invalid, MovementKind.OPENING))
        }
    }

    @Test fun unknownBalanceDoesNotHideRealMovementAndZeroOpeningMakesItKnown() {
        val income = movement(MovementKind.OTHER_INCOME, LedgerAccount.CASH, 4500, noon)
        val unknown = cashbook(listOf(income), date, jakarta)
        assertNull(unknown.accounts[0].recordedBalance)
        assertEquals(BigInteger.valueOf(4500), unknown.accounts[0].recordedChange)
        val open = movement(MovementKind.OPENING, LedgerAccount.CASH, 0, noon)
        val known = cashbook(listOf(income, open), date, jakarta)
        assertEquals(BigInteger.valueOf(4500), known.accounts[0].recordedBalance)
        assertNull(known.accounts[1].recordedBalance)
        val openingOnly = cashbook(listOf(open), date, jakarta)
        assertFalse(openingOnly.hasDailyActivity)
        assertEquals(BigInteger.ZERO, openingOnly.accounts[0].recordedBalance)
    }

    @Test fun dailyRangeIsHalfOpenAndAggregatesWithoutLongOverflow() {
        val first = Instant.parse("2026-11-20T17:00:00Z").toEpochMilli()
        val next = Instant.parse("2026-11-21T17:00:00Z").toEpochMilli()
        val records = listOf(
            movement(MovementKind.OTHER_INCOME, LedgerAccount.CASH, 1, first - 1),
            movement(MovementKind.OTHER_INCOME, LedgerAccount.CASH, Long.MAX_VALUE, first),
            movement(MovementKind.OTHER_INCOME, LedgerAccount.CASH, Long.MAX_VALUE, next - 1),
            movement(MovementKind.OTHER_INCOME, LedgerAccount.CASH, 2, next),
        )
        val jakartaTotals = cashbook(records, date, jakarta).dailyTotals
        val expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)
        assertEquals(expected, jakartaTotals[DailyTotal.OTHER_INCOME])
        assertEquals("Rp18.446.744.073.709.551.614", rupiah(expected))
        val makassar = cashbook(records, date, ZoneId.of("Asia/Makassar")).dailyTotals
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), makassar[DailyTotal.OTHER_INCOME])
    }

    private fun movement(kind: MovementKind, account: LedgerAccount, amount: Long, time: Long): LedgerEntry {
        val move = Movement(kind, account, amount, null, "")
        return LedgerEntry("$kind-$time", time, time, amount, move, null, move.lines())
    }
}
