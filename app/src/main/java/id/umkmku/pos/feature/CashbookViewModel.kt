package id.umkmku.pos.feature

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.umkmku.pos.data.CashbookRepository
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.CashbookSnapshot
import id.umkmku.pos.domain.MovementForm
import id.umkmku.pos.domain.cashbook
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CashbookViewModel @Inject constructor(
    private val repository: CashbookRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate get() = LocalDate.now(zone)
    private val selected = saved.getStateFlow("cashbook-date", today.toString())
    private val retry = MutableStateFlow(0)
    private val _save = MutableStateFlow(SaveState())
    val save = _save.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val book = retry.flatMapLatest {
        combine(repository.observeLedger(), selected) { entries, date ->
            val result: CashRead<CashbookSnapshot> = CashRead.Ready(cashbook(entries, LocalDate.parse(date), zone))
            result
        }.onStart { emit(CashRead.Loading) }
            .catch { emit(CashRead.Failed) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashRead.Loading)

    fun selectDate(date: LocalDate) { saved["cashbook-date"] = date.toString() }
    fun retryRead() { retry.value++ }
    fun clearError() {
        if (!_save.value.busy) _save.value = SaveState()
    }

    fun post(id: String, form: MovementForm, onPosted: (String) -> Unit) {
        if (_save.value.busy) return
        _save.value = SaveState(busy = true)
        viewModelScope.launch {
            val entry = try {
                repository.post(id, form)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: CashbookException) {
                _save.value = SaveState(error = failure.message)
                return@launch
            } catch (_: Exception) {
                _save.value = SaveState(error = "Status penyimpanan belum diketahui. Coba lagi pada formulir ini atau periksa riwayat.")
                return@launch
            }
            _save.value = SaveState()
            onPosted(entry.id)
        }
    }
}
