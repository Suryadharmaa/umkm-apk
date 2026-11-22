package id.umkmku.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.umkmku.pos.domain.BusinessZone
import id.umkmku.pos.domain.DailyTotal
import id.umkmku.pos.domain.ExpenseCategory
import id.umkmku.pos.domain.FinancialReport
import id.umkmku.pos.domain.ReportFilter
import id.umkmku.pos.domain.ReportFrequency
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CashRead
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ReportScreen(state: CashRead<FinancialReport>, onRetry: () -> Unit, onFilter: (ReportFilter) -> Unit) {
    if (state !is CashRead.Ready) {
        CashbookStatus(state, onRetry)
        return
    }
    val (filter, report) = state.value
    val dateStyle = if (filter.frequency == ReportFrequency.DAILY) "d MMMM yyyy" else "MMMM yyyy"
    val title = filter.start.format(DateTimeFormatter.ofPattern(dateStyle, Locale.forLanguageTag("id-ID")))
    val today = LocalDate.now(filter.businessZone.zone)
    LazyColumn(Modifier.testTag("financial-report"), contentPadding = PaddingValues(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ReportFrequency.entries.forEach { option ->
                        FilterChip(option == filter.frequency, { onFilter(filter.copy(frequency = option)) },
                            label = { Text(option.label) }, modifier = Modifier.heightIn(min = Space.touch))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ onFilter(filter.shift(-1)) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Periode sebelumnya") }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton({ onFilter(filter.shift(1)) }, enabled = filter.shift(1).start <= today) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Periode berikutnya")
                    }
                }
                Text("Zona waktu usaha", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    BusinessZone.entries.forEach { option ->
                        FilterChip(option == filter.businessZone, {
                            val newDate = filter.date.coerceAtMost(LocalDate.now(option.zone))
                            onFilter(filter.copy(businessZone = option, date = newDate))
                        }, label = { Text(option.label) }, modifier = Modifier.heightIn(min = Space.touch))
                    }
                }
            }
        }
        item {
            Panel {
                SectionTitle("Penjualan")
                if (!report.hasActivity) Text("Belum ada transaksi tercatat pada periode ini.")
                MoneyLine("Penjualan bruto", rupiah(report.grossSales))
                MoneyLine("Pengembalian dana", rupiah(report.refunds))
                MoneyLine("Penjualan bersih", rupiah(report.netSales))
                MoneyLine(DailyTotal.CASH_SALES.label, rupiah(report.total(DailyTotal.CASH_SALES)))
                MoneyLine(DailyTotal.QRIS_SALES.label, rupiah(report.total(DailyTotal.QRIS_SALES)))
            }
        }
        item {
            Panel {
                SectionTitle("Beban dan penerimaan")
                for (key in listOf(DailyTotal.EXPENSES, DailyTotal.SETTLEMENT_FEES, DailyTotal.OTHER_INCOME,
                    DailyTotal.OWNER_IN, DailyTotal.OWNER_OUT)) {
                    MoneyLine(key.label, rupiah(report.total(key)))
                }
                if (report.expenses.isNotEmpty()) {
                    Text("Beban per kategori", style = MaterialTheme.typography.titleSmall)
                    for (category in ExpenseCategory.entries) {
                        report.expenses[category]?.let { MoneyLine(category.label, rupiah(it)) }
                    }
                }
            }
        }
        item {
            Panel {
                SectionTitle("Saldo pada akhir periode")
                Text("Untuk periode berjalan, saldo memakai transaksi yang sudah tercatat.")
                for (account in report.accounts) {
                    Column(Modifier.fillMaxWidth()) {
                        MoneyLine(account.account.label, account.recordedBalance?.let { rupiah(it) } ?: "Belum diketahui")
                        if (account.recordedBalance == null) MoneyLine("Mutasi tercatat", rupiah(account.recordedChange))
                    }
                }
            }
        }
    }
}
