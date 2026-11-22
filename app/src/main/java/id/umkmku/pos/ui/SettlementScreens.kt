package id.umkmku.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import id.umkmku.pos.domain.PayoutEvidence
import id.umkmku.pos.domain.SettlementPayment
import id.umkmku.pos.domain.SettlementRecord
import id.umkmku.pos.domain.SettlementState
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.SaveState
import java.time.ZoneId

@Composable
internal fun SettlementHistory(records: List<SettlementRecord>, onNew: () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Button(onNew, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                Text("Tambah pencairan")
            }
        }
        if (records.isEmpty()) item {
            MessagePanel("Belum ada pencairan", "Catat pencairan berdasarkan laporan penyedia dan kredit bank.")
        }
        items(records, key = { it.id }) { record ->
            Panel(Modifier.clickable(onClickLabel = "Buka pencairan") { onOpen(record.id) }) {
                Text(record.statusLabel(), style = MaterialTheme.typography.titleMedium)
                Text(record.evidence.reference.ifBlank { "Referensi belum diisi" })
                record.posting?.let { Text(rupiah(it.net)) }
                record.reason?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun SettlementEditor(
    id: String,
    record: SettlementRecord?,
    payments: List<SettlementPayment>,
    zone: ZoneId,
    save: SaveState,
    onReverse: () -> Unit,
    onReversal: (String) -> Unit,
    onSave: (PayoutEvidence, Set<String>, Boolean) -> Unit,
) {
    if (record?.state == SettlementState.POSTED) {
        SettlementDetails(record, payments, onReverse, onReversal)
        return
    }
    var form by rememberSaveable(id) { mutableStateOf(record?.evidence ?: PayoutEvidence()) }
    var selected by rememberSaveable(id) { mutableStateOf(ArrayList(record?.paymentIds.orEmpty())) }
    val listState = rememberLazyListState()
    var shownUpdate by rememberSaveable(id) { mutableStateOf(record?.updatedAt) }
    LaunchedEffect(record?.updatedAt) {
        if (record != null && record.updatedAt != shownUpdate) {
            shownUpdate = record.updatedAt
            listState.scrollToItem(0)
        }
    }
    val focus = LocalFocusManager.current
    val eligible = payments.filter { (it.allocatedTo == null && !it.refunded) || it.saleId in selected }
    val total = eligible.filter { it.saleId in selected }.fold(0L) { sum, row -> Math.addExact(sum, row.amount) }
    val enabled = !save.busy

    fun update(changed: PayoutEvidence) { form = changed.copy(attested = false) }

    fun submit(post: Boolean) {
        focus.clearFocus()
        onSave(form, selected.toSet(), post)
    }

    LazyColumn(Modifier.testTag("settlement-form"), state = listState, contentPadding = PaddingValues(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        item {
            Panel {
                Text("Bruto pilihan", style = MaterialTheme.typography.labelLarge)
                Text(rupiah(total), style = MaterialTheme.typography.headlineSmall)
                if (record != null) {
                    Text(record.statusLabel(), style = MaterialTheme.typography.titleMedium)
                    record.reason?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }
            }
        }
        item { Text("Pembayaran QRIS terkonfirmasi", style = MaterialTheme.typography.titleMedium) }
        if (eligible.isEmpty()) item { Text("Belum ada pembayaran QRIS yang tersedia untuk pencairan.") }
        items(eligible, key = { it.saleId }) { payment ->
            val checked = payment.saleId in selected
            Panel {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Space.touch).toggleable(
                        value = checked, enabled = enabled && (checked || selected.size < 200),
                        role = Role.Checkbox, onValueChange = {
                            selected = ArrayList(if (it) selected + payment.saleId else selected - payment.saleId)
                            form = form.copy(attested = false)
                        },
                    ), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked, onCheckedChange = null)
                    Column(Modifier.padding(start = Space.sm)) {
                        Text("${payment.reference} · ${rupiah(payment.amount)}", style = MaterialTheme.typography.titleMedium)
                        Text("${payment.provider ?: "Penyedia tidak jelas"} · NMID ${payment.merchantId}")
                        if (payment.allocatedTo != null) Text("Sudah dicairkan")
                        if (payment.refunded) Text("Sudah dikembalikan")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Bukti pencairan penyedia", style = MaterialTheme.typography.titleMedium)
                PayoutField("Kode penyedia", form.provider, enabled) { update(form.copy(provider = it)) }
                PayoutField("NMID pada laporan", form.merchantId, enabled) { update(form.copy(merchantId = it)) }
                PayoutField("Referensi pencairan", form.reference, enabled) { update(form.copy(reference = it)) }
                PayoutField("Bruto laporan (Rp)", form.gross, enabled, true) { update(form.copy(gross = it)) }
                PayoutField("Biaya aktual (Rp)", form.fee, enabled, true) { update(form.copy(fee = it)) }
                PayoutField("Kredit aktual bank (Rp)", form.net, enabled, true) { update(form.copy(net = it)) }
                PayoutField("Waktu pencairan", form.payoutTime, enabled) { update(form.copy(payoutTime = it)) }
                Text("TTTT-BB-HH JJ:MM · ${zone.id}", style = MaterialTheme.typography.labelSmall)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Space.touch)
                        .toggleable(form.attested, enabled = enabled, role = Role.Checkbox) { form = form.copy(attested = it) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(form.attested, onCheckedChange = null)
                    Text("Saya sudah mencocokkan laporan penyedia dengan mutasi rekening bank usaha.", Modifier.padding(start = Space.sm))
                }
                if (save.error != null) Text(save.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error)
                Button({ submit(true) }, enabled = enabled && form.attested,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text("Catat pencairan") }
                OutlinedButton({ submit(false) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text("Simpan untuk ditinjau") }
            }
        }
    }
}

@Composable
private fun PayoutField(label: String, value: String, enabled: Boolean, money: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(
        value, { change(it.take(if (money) 13 else 120)) }, Modifier.fillMaxWidth(),
        enabled = enabled, singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Number else KeyboardType.Text),
    )
}

@Composable
private fun SettlementDetails(
    record: SettlementRecord, payments: List<SettlementPayment>, onReverse: () -> Unit, onReversal: (String) -> Unit,
) {
    val posting = checkNotNull(record.posting)
    LazyColumn(contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Panel {
                Text(if (record.reversalJournalId == null) "Pencairan tercatat" else "Pencairan sudah dibalik", style = MaterialTheme.typography.titleMedium)
                Text(rupiah(posting.net), style = MaterialTheme.typography.displaySmall)
                Text("Bruto · ${rupiah(posting.gross)}")
                Text("Biaya aktual · ${rupiah(posting.fee)}")
                Text("Kredit bank · ${rupiah(posting.net)}")
            }
        }
        item {
            Panel {
                Text(record.evidence.reference, style = MaterialTheme.typography.titleMedium)
                Text("${record.evidence.provider} · NMID ${record.evidence.merchantId}")
                Text("Waktu pencairan · ${record.evidence.payoutTime}")
                Text("Pembayaran dialokasikan · ${record.paymentIds.size}")
            }
        }
        item {
            val reversal = record.reversalJournalId
            androidx.compose.material3.TextButton(if (reversal == null) onReverse else ({ onReversal(reversal) })) {
                Text(if (reversal == null) "Catat pembalik pencairan" else "Lihat jurnal pembalik")
            }
        }
        items(payments.filter { it.saleId in record.paymentIds }, key = { it.saleId }) { payment ->
            Panel {
                Text(payment.reference)
                Text(rupiah(payment.amount))
            }
        }
    }
}

private fun SettlementRecord.statusLabel() =
    when {
        reversalJournalId != null -> "Sudah dibalik"
        state == SettlementState.PENDING && reason == null -> "Siap dicatat"
        else -> state.label
    }
