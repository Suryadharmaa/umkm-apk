package id.umkmku.pos.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.umkmku.pos.domain.Catalog
import id.umkmku.pos.domain.CatalogException
import id.umkmku.pos.domain.CatalogRepository
import id.umkmku.pos.domain.EditCatalog
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogState {
    data object Loading : CatalogState
    data object Error : CatalogState
    data class Ready(val catalog: Catalog) : CatalogState
}

data class SaveState(val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val edits: EditCatalog,
) : ViewModel() {
    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog = _catalog.asStateFlow()
    private val _save = MutableStateFlow(SaveState())
    val save = _save.asStateFlow()
    private var readJob: Job? = null

    init { reload() }

    fun reload() {
        readJob?.cancel()
        _catalog.value = CatalogState.Loading
        readJob = viewModelScope.launch {
            try {
                repository.observe().collect { loaded ->
                    _catalog.value = CatalogState.Ready(loaded)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                _catalog.value = CatalogState.Error
            }
        }
    }

    fun clearError() {
        if (!_save.value.busy) _save.value = SaveState()
    }

    fun saveProduct(id: String?, name: String, price: String, category: String?, onSaved: () -> Unit) =
        submit(onSaved) { edits.product(id, name, price, category) }

    fun saveCategory(id: String?, name: String, onSaved: () -> Unit) =
        submit(onSaved) { edits.category(id, name) }

    fun archiveProduct(id: String, onSaved: () -> Unit) = submit(onSaved) { edits.archiveProduct(id) }

    fun archiveCategory(id: String, onSaved: () -> Unit) = submit(onSaved) { edits.archiveCategory(id) }

    private fun submit(onSaved: () -> Unit, action: suspend () -> Unit) {
        if (_save.value.busy) return
        _save.value = SaveState(busy = true)
        viewModelScope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: CatalogException) {
                _save.value = SaveState(error = failure.message)
                return@launch
            } catch (_: Exception) {
                _save.value = SaveState(error = "Perubahan belum tersimpan. Coba lagi.")
                return@launch
            }
            _save.value = SaveState()
            onSaved()
        }
    }
}
