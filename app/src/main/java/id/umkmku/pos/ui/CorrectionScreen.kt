package id.umkmku.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import id.umkmku.pos.domain.CorrectionInput
import id.umkmku.pos.domain.LedgerAccount
import id.umkmku.pos.domain.LedgerEntry
import id.umkmku.pos.domain.MovementKind
import id.umkmku.pos.domain.rupiah
import id.umkmku.pos.feature.SaveState
import java.time.ZoneId

internal val LedgerEntry.canCorrect: Boolean
    get() = correction == null && (payout != null || saleId != null || movement?.kind?.let { it != MovementKind.OPENING } == true)

@Composable
internal fun CorrectionScreen(entry: LedgerEntry, zone: ZoneId, save: SaveState, onPost: (CorrectionInput) -> Unit) {
    val funding = when {
        entry.payout != null || entry.activeSettlementId != null -> LedgerAccount.BANK
        entry.saleId == null -> null
        entry.amountFor(LedgerAccount.CASH) != null -> LedgerAccount.CASH
        else -> LedgerAccount.QRIS_CLEARING
    }
    val providerProof = funding == LedgerAccount.BANK || funding == LedgerAccount.QRIS_CLEARING
    val expected = entry.payout?.net ?: entry.amount
    val bankDebit = funding == LedgerAccount.BANK && expected > 0
    val attest = when {
        entry.payout != null && bankDebit -> "Saya cek laporan penyedia. Debit bank, dana kembali ke saldo QRIS, dan biaya dibalik sesuai pencairan asli."
        entry.payout != null -> "Saya cek laporan penyedia. Saldo QRIS dan biaya dipulihkan, tanpa debit bank karena kredit awal Rp0."
        funding == LedgerAccount.BANK -> "Saya cek bukti refund penyedia dan debit penuh dari rekening bank usaha."
        funding == LedgerAccount.QRIS_CLEARING -> "Saya cek bukti refund penyedia dan pengurangan penuh pada saldo QRIS belum cair."
        funding == LedgerAccount.CASH -> "Saya sudah menyerahkan uang tunai penuh kepada pembeli."
        else -> "Saya periksa mutasi asli dan setuju mencatat entri kebalikannya."
    }
    var reason by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var occurrence by rememberSaveable { mutableStateOf("") }
    var bankReference by rememberSaveable(funding) { mutableStateOf("") }
    var checked by rememberSaveable(funding) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val enabled = !save.busy
    Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.page).testTag("correction-form"),
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Panel {
            Text(entry.label, style = MaterialTheme.typography.titleMedium)
            Text(rupiah(expected), style = MaterialTheme.typography.displaySmall)
            if (funding != null) Text("Sumber dana · ${funding.label}")
            Text(if (entry.saleId != null) "Catat pengembalian penuh yang sudah dilakukan."
                else "Entri asli tetap tersimpan dan dihubungkan ke entri pembalik.")
        }
        CorrectionField("Alasan", reason, 160, enabled) { reason = it; checked = false }
        if (funding != null) {
            val label = if (funding == LedgerAccount.BANK) "Debit bank aktual (Rp)" else "Nominal pengembalian aktual (Rp)"
            CorrectionField(label, amount, 12, enabled, KeyboardType.Number) { amount = it; checked = false }
        }
        if (providerProof) {
            CorrectionField("Kode penyedia", provider, 100, enabled) { provider = it; checked = false }
            CorrectionField("NMID pada bukti", merchant, 100, enabled) { merchant = it; checked = false }
            CorrectionField("Referensi koreksi penyedia", reference, 100, enabled) { reference = it; checked = false }
            if (bankDebit) CorrectionField("Referensi mutasi bank", bankReference, 100, enabled) { bankReference = it; checked = false }
            CorrectionField("Waktu pada bukti", occurrence, 16, enabled) { occurrence = it; checked = false }
            Text("TTTT-BB-HH JJ:MM · ${zone.id}", style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).toggleable(checked, enabled = enabled,
            role = Role.Checkbox, onValueChange = { checked = it }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, onCheckedChange = null, enabled = enabled)
            Text(attest, Modifier.padding(start = Space.sm))
        }
        save.error?.let {
            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
        }
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
            enabled = enabled && checked && reason.isNotBlank(), onClick = {
                focus.clearFocus()
                onPost(CorrectionInput(reason, checked, amount, funding, provider, merchant, reference, occurrence, bankReference))
            }) {
            Text(if (entry.saleId != null) "Catat pengembalian" else "Catat pembalik")
        }
    }
}

@Composable
private fun CorrectionField(
    label: String, value: String, max: Int, enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text, update: (String) -> Unit,
) {
    OutlinedTextField(value, { update(it.take(max)) }, Modifier.fillMaxWidth(), enabled = enabled,
        singleLine = true, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
