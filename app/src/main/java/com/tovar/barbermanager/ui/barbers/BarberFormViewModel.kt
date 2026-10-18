package com.tovar.barbermanager.ui.barbers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.BarberRepository
import com.tovar.barbermanager.data.SpecialtyRepository
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Barber
import com.tovar.barbermanager.data.local.Specialty
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BarberFormViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = BarberRepository(database.barberDao())
    private val specialtyRepository = SpecialtyRepository(database.specialtyDao())

    private val _formState = MutableStateFlow(FormState())
    val formState: StateFlow<FormState> = _formState.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    fun load(id: Long) {
        if (_formState.value.loaded) return
        viewModelScope.launch {
            val withSpecialties = repository.getWithSpecialties(id)
            val catalog = specialtyRepository.observeAll().first()
            val assignedIds = withSpecialties?.specialties
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
            _formState.value = FormState(
                loaded = true,
                barber = withSpecialties?.barber,
                specialties = catalog.filter { it.active || it.id in assignedIds },
                selectedIds = assignedIds
            )
        }
    }

    fun toggleSpecialty(specialtyId: Long) {
        _formState.update { state ->
            state.copy(
                selectedIds = if (specialtyId in state.selectedIds) {
                    state.selectedIds - specialtyId
                } else {
                    state.selectedIds + specialtyId
                }
            )
        }
    }

    fun save(barber: Barber) {
        val specialtyIds = _formState.value.selectedIds.toList()
        viewModelScope.launch {
            repository.save(barber, specialtyIds)
            _saved.send(Unit)
        }
    }

    data class FormState(
        val loaded: Boolean = false,
        val barber: Barber? = null,
        val specialties: List<Specialty> = emptyList(),
        val selectedIds: Set<Long> = emptySet()
    )

    companion object {
        const val BARBER_ID_NEW = 0L
    }
}
