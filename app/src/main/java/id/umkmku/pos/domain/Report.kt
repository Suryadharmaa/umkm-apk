package id.umkmku.pos.domain

import java.time.LocalDate
import java.time.ZoneId

enum class BusinessZone(val label: String, id: String) {
    WIB("WIB", "Asia/Jakarta"),
    WITA("WITA", "Asia/Makassar"),
    WIT("WIT", "Asia/Jayapura");

    val zone: ZoneId = ZoneId.of(id)
}

enum class ReportFrequency(val label: String) { DAILY("Harian"), MONTHLY("Bulanan") }

data class ReportFilter(val date: LocalDate, val frequency: ReportFrequency, val businessZone: BusinessZone) {
    val start: LocalDate get() = if (frequency == ReportFrequency.DAILY) date else date.withDayOfMonth(1)
    val end: LocalDate get() = if (frequency == ReportFrequency.DAILY) start.plusDays(1) else start.plusMonths(1)

    fun shift(delta: Long): ReportFilter = copy(
        date = if (frequency == ReportFrequency.DAILY) date.plusDays(delta) else date.plusMonths(delta),
    )
}

data class FinancialReport(val filter: ReportFilter, val period: FinancialPeriod)
