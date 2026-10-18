package com.tovar.barbermanager.ui.barbers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.BarberRepository
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.BarberWithSpecialties
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BarbersUiState(
    val loading: Boolean = true,
    val items: List<BarberWithSpecialties> = emptyList()
)

class BarbersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BarberRepository(AppDatabase.getInstance(application).barberDao())

    val isAdmin: Boolean = SessionManager(application).isAdmin

    private val source = if (isAdmin) repository.observeAll() else repository.observeActive()

    val uiState: StateFlow<BarbersUiState> = source
        .map { BarbersUiState(loading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BarbersUiState())
}
