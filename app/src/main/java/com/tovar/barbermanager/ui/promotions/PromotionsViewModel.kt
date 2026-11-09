package com.tovar.barbermanager.ui.promotions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.PromotionItem
import com.tovar.barbermanager.data.PromotionRepository
import com.tovar.barbermanager.data.local.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PromotionsUiState(
    val loading: Boolean = true,
    val items: List<PromotionItem> = emptyList()
)

class PromotionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PromotionRepository(AppDatabase.getInstance(application))

    val uiState: StateFlow<PromotionsUiState> = repository.observeAvailable()
        .map { PromotionsUiState(loading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromotionsUiState())
}