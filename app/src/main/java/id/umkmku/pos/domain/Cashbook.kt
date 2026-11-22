package id.umkmku.pos.domain

import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

enum class LedgerAccount(val label: String) {
    CASH("Kas tunai"), BANK("Bank"), QRIS_CLEARING("QRIS belum cair"),
    SALES("Penjualan"), OTHER_INCOME("Pendapatan lain"),
    OPERATING_EXPENSE("Beban operasional"), OWNER_EQUITY("Modal pemilik"),
    SETTLEMENT_FEE("Biaya pencairan QRIS"), SALES_RETURNS("Retur penjualan");

    val isFunding: Boolean get() = this == CASH || this == BANK
    fun otherFunding(): LedgerAccount = when (this) {
        CASH -> BANK
        BANK -> CASH
        else -> throw CashbookException("Pilih kas tunai atau bank.")
    }
}

enum class MovementKind(val label: String) {
    OPENING("Saldo awal"),
    OTHER_INCOME("Pendapatan lain"),
    OPERATING_EXPENSE("Beban operasional"),
    OWNER_IN("Setoran modal"),
    OWNER_OUT("Penarikan pemilik"),
    TRANSFER("Transfer antar akun"),
}

enum class ExpenseCategory(val label: String) {
    SUPPLIES("Bahan dan perlengkapan"), RENT("Sewa"), UTILITIES("Utilitas"),
    TRANSPORT("Transportasi"), LABOR("Tenaga kerja"), OTHER("Beban lainnya"),
}

data class MovementForm(
    val kind: MovementKind,
    val account: LedgerAccount,
    val amount: String,
    val category: ExpenseCategory?,
    val note: String,
)

data class Movement(
    val kind: MovementKind,
    val account: LedgerAccount,
    val amount: Long,
    val category: ExpenseCategory?,
    val note: String,
)

data class LedgerLine(val account: LedgerAccount, val amount: Long)

data class LedgerEntry(
    val id: String,
    val effectiveAt: Long,
    val recordedAt: Long,
    val amount: Long,
    val movement: Movement?,
    val saleId: String?,
    val lines: List<LedgerLine>,
    val payout: PayoutPosting? = null,
    val correction: CorrectionDetails? = null,
    val correctedBy: String? = null,
    val activeSettlementId: String? = null,
) {
    fun amountFor(account: LedgerAccount): Long? = lines.firstOrNull { it.account == account }?.amount
    val label: String get() = correction?.kind?.label ?: if (payout != null) "Pencairan QRIS" else movement?.let {
        if (it.kind == MovementKind.TRANSFER) "${it.account.label} â†’ ${it.account.otherFunding().label}" else it.kind.label
    } ?: if (amountFor(LedgerAccount.QRIS_CLEARING) != null) "Penjualan QRIS" else "Penjualan tunai"
}

class CashbookException(message: String) : IllegalArgumentException(message)

internal fun movementAmount(raw: String, kind: MovementKind): Long? {
    val digits = raw.trim()
    if (digits.isEmpty() || digits.length > 12 || digits.any { it !in '0'..'9' }) return null
    val minimum = if (kind == MovementKind.OPENING) 0L else 1L
    return digits.toLongOrNull()?.takeIf { it in minimum..MAX_SALE_IDR }
}

internal fun MovementForm.validate(): Movement {
    if (!account.isFunding) throw CashbookException("Pilih kas tunai atau bank.")
    val idr = movementAmount(amount, kind)
        ?: throw CashbookException("Masukkan nominal Rupiah yang valid tanpa titik atau koma.")
    if ((kind == MovementKind.OPERATING_EXPENSE) != (category != null)) {
        throw CashbookException("Pilih kategori untuk beban operasional.")
    }
    val description = note.trim()
    if (description.length > 160) throw CashbookException("Catatan maksimal 160 karakter.")
    return Movement(kind, account, idr, category, description)
}

internal fun Movement.lines(): List<LedgerLine> {
    val (debit, credit) = when (kind) {
        MovementKind.OPENING, MovementKind.OWNER_IN -> account to LedgerAccount.OWNER_EQUITY
        MovementKind.OTHER_INCOME -> account to LedgerAccount.OTHER_INCOME
        MovementKind.OPERATING_EXPENSE -> LedgerAccount.OPERATING_EXPENSE to account
        MovementKind.OWNER_OUT -> LedgerAccount.OWNER_EQUITY to account
        MovementKind.TRANSFER -> account.otherFunding() to account
    }
    return listOf(LedgerLine(debit, amount), LedgerLine(credit, Math.negateExact(amount)))
}

data class AccountSnapshot(
    val account: LedgerAccount,
    val recordedBalance: BigInteger?,
    val recordedChange: BigInteger,
)

enum class DailyTotal(val label: String) {
    CASH_SALES("Penjualan tunai"), QRIS_SALES("Penjualan QRIS"),
    CASH_REFUNDS("Pengembalian tunai"), QRIS_REFUNDS("Pengembalian QRIS"),
    OTHER_INCOME("Pendapatan lain"), EXPENSES("Beban operasional"),
    OWNER_IN("Setoran modal"), OWNER_OUT("Penarikan pemilik"),
    CASH_TO_BANK("Kas tunai â†’ Bank"), BANK_TO_CASH("Bank â†’ Kas tunai"),
    QRIS_BANK_CREDIT("Kredit bank dari QRIS"), SETTLEMENT_FEES("Biaya pencairan QRIS"),
    CASH_CHANGE("Mutasi bersih kas tunai"), BANK_CHANGE("Mutasi bersih bank"),
}

data class CashbookSnapshot(
    val date: LocalDate,
    val accounts: List<AccountSnapshot>,
    val entries: List<LedgerEntry>,
    val dailyTotals: Map<DailyTotal, BigInteger>,
    val dailyExpenses: Map<ExpenseCategory, BigInteger>,
    val hasDailyActivity: Boolean,
)

internal fun cashbook(entries: List<LedgerEntry>, date: LocalDate, zone: ZoneId): CashbookSnapshot {
    val period = financialPeriod(entries, date, date.plusDays(1), zone)
    return CashbookSnapshot(date, accountBalances(entries), entries, period.totals, period.expenses, period.hasActivity)
}

data class FinancialPeriod(
    val totals: Map<DailyTotal, BigInteger>,
    val expenses: Map<ExpenseCategory, BigInteger>,
    val accounts: List<AccountSnapshot>,
    val hasActivity: Boolean,
) {
    fun total(key: DailyTotal): BigInteger = totals.getOrDefault(key, BigInteger.ZERO)
    val grossSales: BigInteger get() = total(DailyTotal.CASH_SALES) + total(DailyTotal.QRIS_SALES)
    val refunds: BigInteger get() = total(DailyTotal.CASH_REFUNDS) + total(DailyTotal.QRIS_REFUNDS)
    val netSales: BigInteger get() = grossSales - refunds
}

internal fun financialPeriod(
    entries: List<LedgerEntry>, startDate: LocalDate, endDate: LocalDate, zone: ZoneId,
): FinancialPeriod {
    val originals = entries.associateBy { it.id }
    val period = FinancialPeriodAccumulator(startDate, endDate, zone)
    entries.forEach { entry -> period.add(entry, entry.correction?.let { originals.getValue(it.originalId) }) }
    return period.result()
}

internal class FinancialPeriodAccumulator(startDate: LocalDate, endDate: LocalDate, zone: ZoneId) {
    private val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    private val end = endDate.atStartOfDay(zone).toInstant().toEpochMilli()
    private val balances = AccountBalances()
    private val totals = mutableMapOf<DailyTotal, BigInteger>()
    private val expenses = mutableMapOf<ExpenseCategory, BigInteger>()
    private var hasActivity = false

    fun add(entry: LedgerEntry, original: LedgerEntry?) {
        if (entry.effectiveAt >= end) return
        balances.add(entry)
        if (entry.effectiveAt < start || entry.movement?.kind == MovementKind.OPENING) return
        hasActivity = true
        val correction = entry.correction
        if (correction?.kind == CorrectionKind.REFUND) {
            val key = if (correction.funding == LedgerAccount.CASH) DailyTotal.CASH_REFUNDS else DailyTotal.QRIS_REFUNDS
            totals.add(key, entry.amount)
        } else {
            val source = if (correction == null) entry else checkNotNull(original)
            val sign = if (correction == null) 1L else -1L
            val payout = source.payout
            if (payout == null) {
                val signed = Math.multiplyExact(sign, source.amount)
                totals.add(dailyKind(source), signed)
                source.movement?.category?.let { expenses.add(it, signed) }
            } else {
                totals.add(DailyTotal.QRIS_BANK_CREDIT, Math.multiplyExact(sign, payout.net))
                totals.add(DailyTotal.SETTLEMENT_FEES, Math.multiplyExact(sign, payout.fee))
            }
        }
        totals.add(DailyTotal.CASH_CHANGE, entry.amountFor(LedgerAccount.CASH) ?: 0)
        totals.add(DailyTotal.BANK_CHANGE, entry.amountFor(LedgerAccount.BANK) ?: 0)
    }

    fun result(): FinancialPeriod = FinancialPeriod(totals.toMap(), expenses.toMap(), balances.result(), hasActivity)
}

private fun accountBalances(entries: List<LedgerEntry>): List<AccountSnapshot> =
    AccountBalances().apply { entries.forEach(::add) }.result()

private class AccountBalances {
    private val openings = mutableMapOf<LedgerAccount, BigInteger>()
    private val balances = mutableMapOf<LedgerAccount, BigInteger>()

    fun add(entry: LedgerEntry) {
        val movement = entry.movement
        if (movement?.kind == MovementKind.OPENING) {
            openings[movement.account] = BigInteger.valueOf(movement.amount)
        } else {
            for (line in entry.lines) balances.add(line.account, line.amount)
        }
    }

    fun result(): List<AccountSnapshot> = listOf(
        LedgerAccount.CASH, LedgerAccount.BANK, LedgerAccount.QRIS_CLEARING,
    ).map { account ->
        val net = balances.getOrDefault(account, BigInteger.ZERO)
        AccountSnapshot(account, if (account.isFunding) openings[account]?.add(net) else net, net)
    }
}

private fun dailyKind(entry: LedgerEntry): DailyTotal = when (entry.movement?.kind) {
    null -> if (entry.amountFor(LedgerAccount.CASH) != null) DailyTotal.CASH_SALES else DailyTotal.QRIS_SALES
    MovementKind.OTHER_INCOME -> DailyTotal.OTHER_INCOME
    MovementKind.OPERATING_EXPENSE -> DailyTotal.EXPENSES
    MovementKind.OWNER_IN -> DailyTotal.OWNER_IN
    MovementKind.OWNER_OUT -> DailyTotal.OWNER_OUT
    MovementKind.TRANSFER -> if (entry.movement.account == LedgerAccount.CASH) DailyTotal.CASH_TO_BANK else DailyTotal.BANK_TO_CASH
    MovementKind.OPENING -> error("Opening balances have no daily activity total")
}

private fun <K> MutableMap<K, BigInteger>.add(key: K, amount: Long) {
    this[key] = getOrDefault(key, BigInteger.ZERO).add(BigInteger.valueOf(amount))
}
