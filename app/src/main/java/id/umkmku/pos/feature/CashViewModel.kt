package id.umkmku.pos.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.umkmku.pos.data.CashSaleRepository
import id.umkmku.pos.domain.CashCheckoutException
import id.umkmku.pos.domain.CashDraft
import id.umkmku.pos.domain.PriceReviewRequired
import id.umkmku.pos.domain.RecordCashSale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CashRead<out T> {
    data object Loading : CashRead<Nothing>
    data object Failed : CashRead<Nothing>
    data class Ready<T>(val value: T) : CashRead<T>
}

data class CashCommand(
    val busy: Boolean = false,
    val error: String? = null,
    val priceReview: CashDraft? = null,
)

@HiltViewModel
class CashViewModel @Inject constructor(
    private val repository: CashSaleRepository,
    private val recordCashSale: RecordCashSale,
) : ViewModel() {
    private val _command = MutableStateFlow(CashCommand())
    val command = _command.asStateFlow()
    private val retries = MutableStateFlow(0)
    val draft = watch(repository::observeDraft)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CashRead.Loading)

    fun history() = watch(repository::observeHistory)
    fun sale(id: String) = watch { repository.observeSale(id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> watch(source: () -> Flow<T>): Flow<CashRead<T>> = retries.flatMapLatest {
        source().map<T, CashRead<T>> { CashRead.Ready(it) }
            .onStart { emit(CashRead.Loading) }
            .catch { emit(CashRead.Failed) }
    }

    fun retryRead() { retries.value++ }

    fun clearCommand() {
        if (!_command.value.busy) _command.value = CashCommand()
    }

    fun changeQuantity(id: String, delta: Int, draftId: String? = null) =
        submit { repository.changeQuantity(id, delta, draftId) }

    fun reviewPrices(draft: CashDraft) = submit { repository.reviewPrices(draft.id) }
    fun acceptPrices(proposed: CashDraft) = submit { repository.acceptPrices(proposed) }
    fun pay(draft: CashDraft, tender: String) = submit { recordCashSale(draft, tender) }

    private fun submit(action: suspend () -> Unit) {
        if (_command.value.busy) return
        _command.value = CashCommand(busy = true)
        viewModelScope.launch {
            _command.value = try {
                action()
                CashCommand()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: PriceReviewRequired) {
                CashCommand(priceReview = failure.proposed)
            } catch (failure: CashCheckoutException) {
                CashCommand(error = failure.message)
            } catch (_: Exception) {
                CashCommand(error = "Status penyimpanan belum dapat dipastikan. Periksa riwayat sebelum mencoba lagi.")
            }
        }
    }
}
