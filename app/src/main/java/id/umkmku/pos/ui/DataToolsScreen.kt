package id.umkmku.pos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import id.umkmku.pos.data.portability.CsvKind
import id.umkmku.pos.domain.ReportFilter
import id.umkmku.pos.feature.DataToolState
import id.umkmku.pos.feature.DataToolsViewModel
import java.time.LocalDate

@Composable
internal fun DataToolsScreen(
    model: DataToolsViewModel, state: DataToolState, filter: ReportFilter, onReport: () -> Unit,
) {
    var newBackup by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var selection by rememberSaveable { mutableStateOf(CsvKind.SALES) }
    var exportPending by rememberSaveable { mutableStateOf<CsvKind?>(null) }
    var exportFilter by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var expanded by remember { mutableStateOf(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), model::finishBackup)
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val kind = exportPending
        if (uri != null && kind != null) model.export(uri, kind, exportFilter.toReportFilter() ?: filter)
        exportPending = null
        exportFilter = arrayListOf()
    }
    val stamp = LocalDate.now(filter.businessZone.zone)
    val active = !state.busy
    val listState = rememberLazyListState()
    LaunchedEffect(state.busy, state.message) {
        if (state.busy || state.message != null) listState.scrollToItem(0)
    }
    LazyColumn(Modifier.testTag("data-tools"), state = listState, contentPadding = PaddingValues(Space.page),
        verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (state.busy || state.message != null) item {
            Panel(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(state.message ?: "Sedang memproses data…", color = if (state.failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface)
            }
        }
        item {
            Panel {
                SectionTitle("Cadangan terenkripsi")
                Text("Simpan salinan data usaha dengan kata sandi. Simpan kata sandinya di tempat terpisah.")
                Button({ newBackup = true }, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                    Text("Buat cadangan")
                }
            }
        }
        item {
            Panel {
                SectionTitle("Pemulihan data")
                Text("Pulihkan cadangan pada aplikasi yang belum berisi data usaha.")
                OutlinedButton({ pickBackup.launch(arrayOf("*/*")) }, enabled = active,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text("Pilih berkas cadangan") }
            }
        }
        item {
            Panel {
                SectionTitle("Ekspor CSV")
                Text("CSV dapat dibaca tanpa kata sandi. Pilih lokasi yang sesuai untuk data usaha.")
                Column {
                    OutlinedButton({ expanded = true }, enabled = active, modifier = Modifier.heightIn(min = Space.touch)) {
                        Text(selection.label)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        CsvKind.entries.forEach { kind ->
                            DropdownMenuItem(text = { Text(kind.label) }, onClick = { selection = kind; expanded = false })
                        }
                    }
                }
                if (selection == CsvKind.REPORT) {
                    Text("${filter.frequency.label} · ${filter.start} · ${filter.businessZone.label}")
                    TextButton(onReport, enabled = active) { Text("Ubah periode atau zona") }
                } else Text("Zona waktu · ${filter.businessZone.label}")
                Button({
                    exportPending = selection
                    exportFilter = arrayListOf(filter.date.toString(), filter.frequency.name, filter.businessZone.name)
                    saveCsv.launch("UMKM-KU-${selection.fileName}-$stamp.csv")
                }, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text("Simpan CSV") }
            }
        }
    }
    if (newBackup) PasswordPrompt(restoring = false, onClose = { newBackup = false }) { password, _ ->
        newBackup = false
        model.armBackup(password)
        saveBackup.launch("UMKM-KU-$stamp.umkmbackup")
    }
    restoreUri?.let { uri ->
        PasswordPrompt(restoring = true, onClose = { restoreUri = null }) { password, confirmed ->
            restoreUri = null
            model.restore(uri, password, confirmed)
        }
    }
}

@Composable
private fun PasswordPrompt(restoring: Boolean, onClose: () -> Unit, onNext: (CharArray, Boolean) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    val ready = if (restoring) password.isNotEmpty() && confirmed else password.length >= 12 && password == repeat
    val hide = PasswordVisualTransformation()
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password)
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (restoring) "Konfirmasi pemulihan" else "Kata sandi cadangan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(password, { password = it.take(128) }, singleLine = true,
                    label = { Text("Kata sandi") }, visualTransformation = hide, keyboardOptions = keyboard)
                if (restoring) {
                    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).toggleable(confirmed, role = Role.Checkbox) {
                        confirmed = it
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmed, onCheckedChange = null)
                        Text("Pulihkan semua data ke aplikasi kosong.", Modifier.padding(start = Space.sm))
                    }
                } else {
                    OutlinedTextField(repeat, { repeat = it.take(128) }, singleLine = true,
                        label = { Text("Ulangi kata sandi") }, visualTransformation = hide, keyboardOptions = keyboard)
                    Text("Gunakan minimal 12 karakter. Cadangan memerlukan kata sandi ini untuk dibuka.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entered = password.toCharArray()
                password = ""
                repeat = ""
                onNext(entered, confirmed)
            }, enabled = ready) { Text(if (restoring) "Pulihkan data" else "Pilih lokasi") }
        },
        dismissButton = { TextButton(onClose) { Text("Batal") } },
    )
}

private fun List<String>.toReportFilter(): ReportFilter? = runCatching {
    ReportFilter(LocalDate.parse(get(0)), id.umkmku.pos.domain.ReportFrequency.valueOf(get(1)),
        id.umkmku.pos.domain.BusinessZone.valueOf(get(2)))
}.getOrNull()
