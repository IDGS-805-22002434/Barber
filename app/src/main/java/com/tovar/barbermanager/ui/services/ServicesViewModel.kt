package com.tovar.barbermanager.ui.services

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.ServiceRepository
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Service
import com.tovar.barbermanager.data.model.ServiceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SortMode { NAME, PRICE_ASC, PRICE_DESC }

data class ServicesUiState(
    val loading: Boolean = true,
    val items: List<Service> = emptyList(),
    val hasFilters: Boolean = false,
    val totalServices: Int = 0,
    val inactiveCount: Int = 0
)

class ServicesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceRepository(AppDatabase.getInstance(application).serviceDao())

    val isAdmin: Boolean = SessionManager(application).isAdmin

    private val categoryFilter = MutableStateFlow<ServiceCategory?>(null)
    private val searchQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(SortMode.NAME)
    val sort: StateFlow<SortMode> = sortMode

    val uiState: StateFlow<ServicesUiState> =
        combine(categoryFilter, searchQuery, sortMode, repository.observeAll()) { category, query, sort, services ->
            val visible = services
                .asSequence()
                .filter { isAdmin || it.active }
                .filter { category == null || it.category == category }
                .filter {
                    query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
                }
                .sortedWith(comparator(sort))
                .toList()
            ServicesUiState(
                loading = false,
                items = visible,
                hasFilters = category != null || query.isNotBlank(),
                totalServices = if (isAdmin) services.size else visible.size,
                inactiveCount = services.count { !it.active }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServicesUiState())

    fun onCategorySelected(category: ServiceCategory?) {
        categoryFilter.value = category
    }

    fun onSearchChanged(query: String) {
        searchQuery.value = query
    }

    fun onSortChanged(mode: SortMode) {
        sortMode.value = mode
    }

    private fun comparator(sort: SortMode): Comparator<Service> = when (sort) {
        SortMode.NAME -> compareBy { it.name.lowercase() }
        SortMode.PRICE_ASC -> compareBy { it.priceCents }
        SortMode.PRICE_DESC -> compareByDescending { it.priceCents }
    }
}
