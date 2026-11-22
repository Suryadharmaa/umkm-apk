package id.umkmku.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import id.umkmku.pos.domain.AccountSnapshot
import id.umkmku.pos.domain.CashbookSnapshot
import id.umkmku.pos.domain.DailyTotal
import id.umkmku.pos.domain.ExpenseCategory
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.LedgerEntry
import id.umkmku.pos.domain.MovementForm
import id.umkmku.pos.domain.MovementKind
import id.umkmku.pos.domain.movementAmount
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.CashRead
import id.umkmku.pos.feature.SaveState
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val assetAccounts = listOf(LedgerAccount.CASH, LedgerAccount.BANK, LedgerAccount.QRIS_CLEARING)
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))
private val timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))

@Composable
internal fun CashbookScreen(
    state: CashRead<CashbookSnapshot>,
    today: LocalDate,
    zone: ZoneId,
    onRetry: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onNew: (LedgerAccount?) -> Unit,
    onHistory: () -> Unit,
) {
    if (state !is CashRead.Ready) {
        CashbookStatus(state, onRetry)
        return
    }
    val book = state.value
    LazyColumn(
        Modifier.fillMaxSize().testTag("cashbook"), contentPadding = PaddingValues(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Button(
                { onNew(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
            ) { Text("Catat mutasi") }
            TextButton(onHistory, Modifier.heightIn(min = Space.touch)) { Text("Riwayat mutasi") }
        }
        item { SectionTitle("Saldo tercatat saat ini") }
        items(book.accounts, key = { it.account.name }) { account ->
            AccountPanel(account) { onNew(account.account) }
        }
        item {
            Panel {
                SectionTitle("Ringkasan harian")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ onDate(book.date.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Hari sebelumnya")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dateFormat.format(book.date), style = MaterialTheme.typography.titleMedium)
                        Text(zone.id, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton({ onDate(book.date.plusDays(1)) }, enabled = book.date < today) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Hari berikutnya")
                    }
                }
                if (!book.hasDailyActivity) {
                    Text("Belum ada pergerakan dana untuk tanggal ini.")
                } else {
                    DailyTotal.entries.forEach { key ->
                        book.dailyTotals[key]?.let { value ->
                            val net = key == DailyTotal.CASH_CHANGE || key == DailyTotal.BANK_CHANGE
                            MoneyLine(key.label, if (net) signedRupiah(value) else rupiah(value))
                        }
                    }
                }
            }
        }
        if (book.dailyExpenses.isNotEmpty()) item {
            Panel {
                SectionTitle("Beban per kategori")
                ExpenseCategory.entries.forEach { category ->
                    book.dailyExpenses[category]?.let { MoneyLine(category.label, rupiah(it)) }
                }
            }
        }
    }
}

@Composable
private fun AccountPanel(account: AccountSnapshot, onOpen: () -> Unit) {
    Panel {
        Text(account.account.label, style = MaterialTheme.typography.titleMedium)
        val balance = account.recordedBalance
        if (balance == null) {
            Text("Saldo belum diketahui", style = MaterialTheme.typography.titleMedium)
            MoneyLine("Mutasi tercatat", signedRupiah(account.recordedChange))
            TextButton(onOpen, Modifier.heightIn(min = Space.touch)) { Text("Isi saldo awal ${account.account.label.lowercase(Locale.ROOT)}") }
        } else {
            Text(rupiah(balance), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            if (balance.signum() < 0) {
                Text("Saldo tercatat negatif. Periksa mutasi akun.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun MovementHistory(
    entries: List<LedgerEntry>,
    zone: ZoneId,
    onEntry: (String) -> Unit,
) {
    var account by rememberSaveable { mutableStateOf<LedgerAccount?>(null) }
    val visible = entries.filter { row -> account?.let { row.amountFor(it) != null } ?: true }
    LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilterChip(account == null, { account = null }, label = { Text("Semua akun") }, modifier = Modifier.heightIn(min = Space.touch))
                assetAccounts.forEach { option ->
                    FilterChip(account == option, { account = option }, label = { Text(option.label) },
                        modifier = Modifier.heightIn(min = Space.touch))
                }
            }
        }
        if (visible.isEmpty()) item { MessagePanel("Belum ada mutasi", "Mutasi yang tercatat akan tampil di sini.") }
        items(visible, key = { it.id }) { entry ->
            val delta = account?.let(entry::amountFor)
            val amount = if (delta == null) rupiah(entry.amount) else signedRupiah(BigInteger.valueOf(delta))
            Panel(Modifier.clickable(onClickLabel = "Lihat rincian ${entry.label}") { onEntry(entry.id) }, contentPadding = Space.md) {
                MoneyLine(entry.label, amount)
                val details = entry.movement?.let { it.category?.label ?: it.note.takeIf(String::isNotBlank) }
                if (details != null) Text(details)
                Text(formatTime(entry.effectiveAt, zone), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MovementDetails(entry: LedgerEntry, zone: ZoneId, onReceipt: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Panel {
                SectionTitle(entry.label)
                Text(rupiah(entry.amount), style = MaterialTheme.typography.headlineSmall)
                Text("Waktu pencatatan · ${formatTime(entry.recordedAt, zone)}")
                if (entry.movement?.kind == MovementKind.OPENING) {
                    Text("Saldo sebelum mutasi sejak ${formatTime(entry.effectiveAt, zone)}")
                }
                entry.movement?.category?.let { Text("Kategori · ${it.label}") }
                entry.movement?.note?.takeIf(String::isNotBlank)?.let { Text(it) }
            }
        }
        item {
            Panel {
                SectionTitle("Jurnal")
                entry.lines.forEachIndexed { index, line ->
                    val debit = if (line.amount == 0L) index == 0 else line.amount > 0
                    val side = if (debit) "Debit" else "Kredit"
                    MoneyLine("$side · ${line.account.label}", rupiah(kotlin.math.abs(line.amount)))
                }
            }
        }
        entry.saleId?.let { id ->
            item { TextButton({ onReceipt(id) }) { Text("Lihat struk penjualan") } }
        }
    }
}

@Composable
internal fun CashbookForm(
    opening: LedgerAccount?,
    save: SaveState,
    onSave: (MovementForm) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(if (opening != null) MovementKind.OPENING else MovementKind.OTHER_INCOME) }
    var account by rememberSaveable { mutableStateOf(opening ?: LedgerAccount.CASH) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<ExpenseCategory?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val expense = kind == MovementKind.OPERATING_EXPENSE
    val parsed = movementAmount(amount, kind)
    val focus = LocalFocusManager.current

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        CashbookSelect("Jenis mutasi", kind.label, MovementKind.entries, MovementKind::label, !save.busy) { kind = it }
        Text(if (kind == MovementKind.TRANSFER) "Dari akun" else "Akun", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            assetAccounts.filter { it.isFunding }.forEach { option ->
                FilterChip(account == option, { account = option }, enabled = !save.busy, label = { Text(option.label) },
                    modifier = Modifier.heightIn(min = Space.touch))
            }
        }
        if (kind == MovementKind.OTHER_INCOME) {
            Text("Catat pendapatan di luar penjualan tunai dan QRIS yang sudah masuk jurnal.")
        }
        if (kind == MovementKind.TRANSFER) Text("Ke akun · ${account.otherFunding().label}")
        if (kind == MovementKind.OPENING) {
            Text("Isi saldo akun sebelum mutasi pertama. Saldo ini dicatat satu kali dan dapat bernilai Rp0.")
        }
        if (expense) {
            CashbookSelect("Kategori beban", category?.label ?: "Pilih kategori", ExpenseCategory.entries,
                ExpenseCategory::label, !save.busy) { category = it }
        }
        OutlinedTextField(
            amount, { amount = it.take(13) }, Modifier.fillMaxWidth(), enabled = !save.busy,
            isError = amount.isNotBlank() && parsed == null, singleLine = true,
            label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { if (amount.isNotBlank() && parsed == null) Text("Gunakan Rupiah bulat tanpa titik atau koma.") },
        )
        OutlinedTextField(
            note, { note = it.take(161) }, Modifier.fillMaxWidth(), enabled = !save.busy,
            label = { Text("Catatan (opsional)") }, maxLines = 3, isError = note.length > 160,
            supportingText = { if (note.length > 160) Text("Catatan maksimal 160 karakter.") },
        )
        if (save.error != null) {
            Text(save.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
        }
        Button(
            { focus.clearFocus(); onSave(MovementForm(kind, account, amount, category.takeIf { expense }, note)) },
            enabled = parsed != null && (!expense || category != null) && !save.busy && note.length <= 160,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        ) { Text(if (save.busy) "Menyimpan…" else if (kind == MovementKind.OPENING) "Catat saldo awal" else "Simpan mutasi") }
    }
}

@Composable
private fun <T> CashbookSelect(
    label: String, selection: String, options: List<T>, optionLabel: (T) -> String, enabled: Boolean, choose: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ expanded = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text(selection) }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { value -> DropdownMenuItem({ Text(optionLabel(value)) }, { expanded = false; choose(value) }) }
            }
        }
    }
}

@Composable
internal fun CashbookStatus(state: CashRead<*>, onRetry: () -> Unit) {
    if (state == CashRead.Failed) MessagePanel("Buku kas gagal dimuat", "Coba baca kembali data di perangkat.", "Coba lagi", onRetry)
    else MessagePanel("Memuat buku kas…", "Membaca jurnal di perangkat.")
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun MoneyLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun signedRupiah(amount: BigInteger): String = when (amount.signum()) {
    -1 -> "−${rupiah(amount.abs())}"
    1 -> "+${rupiah(amount)}"
    else -> rupiah(amount)
}

private fun formatTime(time: Long, zone: ZoneId): String = timeFormat.format(Instant.ofEpochMilli(time).atZone(zone))
