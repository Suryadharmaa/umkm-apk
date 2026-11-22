package id.umkmku.pos.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.umkmku.pos.data.SettlementRepository
import id.umkmku.pos.domain.CashbookException
import id.umkmku.pos.domain.PayoutEvidence
import id.umkmku.pos.domain.SettlementPayment
import id.umkmku.pos.domain.SettlementRecord
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettlementSnapshot(val records: List<SettlementRecord>, val payments: List<SettlementPayment>)

@HiltViewModel
class SettlementViewModel @Inject constructor(private val repo: SettlementRepository) : ViewModel() {
    val zone: ZoneId = ZoneId.systemDefault()
    private val retry = MutableStateFlow(0)
    private val _save = MutableStateFlow(SaveState())
    val save = _save.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot = retry.flatMapLatest {
        combine(repo.history(), repo.candidates()) { records, payments ->
            val ready: CashRead<SettlementSnapshot> = CashRead.Ready(SettlementSnapshot(records, payments))
            ready
        }.onStart { emit(CashRead.Loading) }.catch { emit(CashRead.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashRead.Loading)

    fun retryRead() { retry.value++ }
    fun clearError() { if (!_save.value.busy) _save.value = SaveState() }

    fun save(id: String, evidence: PayoutEvidence, selected: Set<String>, post: Boolean) {
        if (_save.value.busy) return
        _save.value = SaveState(busy = true)
        viewModelScope.launch {
            _save.value = try {
                repo.submit(id, evidence, selected, post, zone)
                SaveState()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: CashbookException) {
                SaveState(error = failure.message)
            } catch (_: Exception) {
                SaveState(error = "Status pencairan belum diketahui. Buka riwayat atau coba lagi pada formulir ini.")
            }
        }
    }
}
