package id.umkmku.pos.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.umkmku.pos.data.CorrectionRepository
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.CorrectionInput
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CorrectionViewModel @Inject constructor(private val repository: CorrectionRepository) : ViewModel() {
    private val _save = MutableStateFlow(SaveState())
    val save = _save.asStateFlow()

    fun clearError() { if (!_save.value.busy) _save.value = SaveState() }

    fun post(operation: String, target: String, form: CorrectionInput, zone: ZoneId, onPosted: (String) -> Unit) {
        if (_save.value.busy) return
        _save.value = SaveState(busy = true)
        viewModelScope.launch {
            val id = try {
                repository.post(operation, target, form, zone)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: CashbookException) {
                _save.value = SaveState(error = failure.message)
                return@launch
            } catch (_: Exception) {
                _save.value = SaveState(error = "Periksa riwayat sebelum mencoba kembali pada formulir ini.")
                return@launch
            }
            _save.value = SaveState()
            onPosted(id)
        }
    }
}
