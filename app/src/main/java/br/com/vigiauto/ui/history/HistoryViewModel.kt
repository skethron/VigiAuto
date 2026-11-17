package br.com.vigiauto.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.vigiauto.VigiAutoApp
import br.com.vigiauto.data.repo.VehicleRepository
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.VehicleRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

enum class HistoryFilter(val label: String) {
    ALL("Todas"),
    COMPLETED("Concluídas"),
    NOT_FOUND("Não encontradas"),
    PENDING("Pendentes"),
    FAILED("Falhas"),
    INCOMPLETE("Incompletas")
}

enum class HistorySort(val label: String) {
    LAST_SEEN_DESC("Mais recentes"),
    LAST_SEEN_ASC("Mais antigas"),
    PLATE_ASC("Placa (A-Z)")
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VehicleRepository = (application as VigiAutoApp).container.repository

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(HistorySort.LAST_SEEN_DESC)
    val sort: StateFlow<HistorySort> = _sort.asStateFlow()

    val records: StateFlow<List<VehicleRecord>> =
        combine(repository.records, _search, _filter, _sort) { records, search, filter, sort ->
            val textQuery = search.searchKey()
            val compactQuery = textQuery.compactKey()
            records.asSequence()
                .filter { record ->
                    textQuery.isEmpty() || listOfNotNull(
                        record.normalizedPlate,
                        record.displayPlate,
                        record.brand,
                        record.model,
                        listOfNotNull(record.brand, record.model)
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() }
                    ).any { value ->
                        val key = value.searchKey()
                        key.contains(textQuery) ||
                            (compactQuery.isNotEmpty() && key.compactKey().contains(compactQuery))
                    }
                }
                .filter { record ->
                    when (filter) {
                        HistoryFilter.ALL -> true
                        HistoryFilter.COMPLETED -> record.queryStatus == QueryStatus.COMPLETED
                        HistoryFilter.NOT_FOUND -> record.queryStatus == QueryStatus.NOT_FOUND
                        HistoryFilter.PENDING -> record.queryStatus == QueryStatus.PENDING ||
                            record.queryStatus == QueryStatus.IN_PROGRESS
                        HistoryFilter.FAILED -> record.queryStatus.isFailure
                        HistoryFilter.INCOMPLETE -> record.queryStatus == QueryStatus.INCOMPLETE
                    }
                }
                .sortedWith(
                    when (sort) {
                        HistorySort.LAST_SEEN_DESC -> compareByDescending { it.lastSeenTimestamp }
                        HistorySort.LAST_SEEN_ASC -> compareBy { it.lastSeenTimestamp }
                        HistorySort.PLATE_ASC -> compareBy { it.normalizedPlate }
                    }
                )
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearch(value: String) {
        _search.value = value
    }

    fun setFilter(value: HistoryFilter) {
        _filter.value = value
    }

    fun setSort(value: HistorySort) {
        _sort.value = value
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { repository.deleteRecord(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAllRecords(deletePhotos = true) }
    }
}

private fun String.searchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")
    .trim()

private fun String.compactKey(): String = filter { it.isLetterOrDigit() }
