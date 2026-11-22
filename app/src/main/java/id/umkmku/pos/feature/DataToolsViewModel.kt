package id.umkmku.pos.feature

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.umkmku.pos.data.portability.CsvKind
import id.umkmku.pos.data.portability.PortabilityRepository
import id.umkmku.pos.data.portability.RestoreNeedsEmptyDatabase
import id.umkmku.pos.data.portability.RestoreRejected
import id.umkmku.pos.data.portability.RestoreResult
import id.umkmku.pos.domain.ReportFilter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DataToolState(val busy: Boolean = false, val message: String? = null, val failed: Boolean = false)

@HiltViewModel
internal class DataToolsViewModel @Inject constructor(private val repository: PortabilityRepository) : ViewModel() {
    private var pendingPassword: CharArray? = null
    private val _state = MutableStateFlow(DataToolState())
    val state = _state.asStateFlow()

    fun armBackup(password: CharArray) {
        pendingPassword?.fill('\u0000')
        pendingPassword = password
        _state.value = DataToolState()
    }

    fun finishBackup(uri: Uri?) {
        val password = pendingPassword
        pendingPassword = null
        if (uri == null) {
            password?.fill('\u0000')
            return
        }
        if (password == null) {
            _state.value = DataToolState(message = "Ulangi pembuatan cadangan dan masukkan kata sandi.", failed = true)
            return
        }
        launch(password) {
            repository.createBackup(uri, password)
            DataToolState(message = "Cadangan terenkripsi tersimpan.")
        }
    }

    fun restore(uri: Uri, password: CharArray, confirmed: Boolean) = launch(password) {
        when (repository.restoreBackup(uri, password, confirmed)) {
            RestoreResult.REOPEN_VERIFIED -> DataToolState(message = "Pemulihan selesai. Basis data berhasil dibuka ulang dan diverifikasi.")
            RestoreResult.IMPORTED_REOPEN_UNVERIFIED -> DataToolState(
                message = "Data telah dipulihkan. Buka ulang aplikasi dan periksa riwayat sebelum melanjutkan.", failed = true,
            )
        }
    }

    fun export(uri: Uri, kind: CsvKind, filter: ReportFilter) = launch {
        repository.export(uri, kind, filter)
        DataToolState(message = "CSV tersimpan.")
    }

    private fun launch(password: CharArray? = null, work: suspend () -> DataToolState) {
        if (_state.value.busy) {
            password?.fill('\u0000')
            return
        }
        _state.value = DataToolState(busy = true)
        viewModelScope.launch {
            _state.value = try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RestoreNeedsEmptyDatabase) {
                DataToolState(message = "Pemulihan memerlukan aplikasi kosong. Data usaha saat ini tetap tersimpan.", failed = true)
            } catch (_: RestoreRejected) {
                DataToolState(message = "Cadangan ditolak. Periksa kata sandi, versi dan keutuhan berkas. Data usaha tetap tersimpan.", failed = true)
            } catch (_: Exception) {
                DataToolState(message = "Berkas belum selesai. Periksa ruang penyimpanan dan pilih lokasi yang dapat ditulis.", failed = true)
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    override fun onCleared() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }
}
