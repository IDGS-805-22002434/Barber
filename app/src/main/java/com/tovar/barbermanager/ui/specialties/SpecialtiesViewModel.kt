package com.tovar.barbermanager.ui.specialties

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.SpecialtyRepository
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Specialty
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpecialtiesUiState(
    val loading: Boolean = true,
    val items: List<Specialty> = emptyList()
)

class SpecialtiesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpecialtyRepository(AppDatabase.getInstance(application).specialtyDao())

    val isAdmin: Boolean = SessionManager(application).isAdmin

    val uiState: StateFlow<SpecialtiesUiState> = repository.observeAll()
        .map { SpecialtiesUiState(loading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpecialtiesUiState())

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private val _error = Channel<Int>(Channel.BUFFERED)
    val error: Flow<Int> = _error.receiveAsFlow()

    fun save(specialty: Specialty) {
        viewModelScope.launch {
            val name = specialty.name.trim()
            if (name.isEmpty()) {
                _error.send(R.string.error_specialty_name)
                return@launch
            }
            if (repository.nameExists(name, excludingId = specialty.id)) {
                _error.send(R.string.error_specialty_exists)
                return@launch
            }
            repository.save(specialty.copy(name = name))
            _saved.send(Unit)
        }
    }
}
