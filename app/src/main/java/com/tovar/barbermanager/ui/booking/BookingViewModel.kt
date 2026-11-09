package com.tovar.barbermanager.ui.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.remote.ApiClient
import com.tovar.barbermanager.data.remote.ApiException
import com.tovar.barbermanager.data.remote.BarberoDto
import com.tovar.barbermanager.data.remote.CitaDto
import com.tovar.barbermanager.data.remote.CitaRequest
import com.tovar.barbermanager.data.remote.ServicioDto
import com.tovar.barbermanager.data.remote.SlotDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingUiState(
    val loading: Boolean = true,
    val servicios: List<ServicioDto> = emptyList(),
    val barberos: List<BarberoDto> = emptyList(),
    val servicio: ServicioDto? = null,
    val barbero: BarberoDto? = null,
    val fecha: String? = null,
    val slots: List<SlotDto> = emptyList(),
    val slotsLoading: Boolean = false,
    val slot: SlotDto? = null,
    val notas: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val created: CitaDto? = null
) {
    val canConfirm: Boolean
        get() = !submitting && servicio != null && barbero != null && fecha != null && slot != null
}

class BookingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    /** Invalida respuestas viejas si el usuario cambia de fecha/barbero rápido. */
    private var slotsRequest = 0

    init {
        loadCatalog()
    }

    fun retry() {
        loadCatalog()
        if (_uiState.value.barbero != null && _uiState.value.fecha != null) loadSlots()
    }

    private fun loadCatalog() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val servicios = ApiClient.call { ApiClient.service().servicios() }
                    .filter { it.activo }
                val barberos = ApiClient.call { ApiClient.service().barberos() }
                    .filter { it.activo }
                _uiState.update {
                    it.copy(loading = false, servicios = servicios, barberos = barberos)
                }
            } catch (error: ApiException) {
                _uiState.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun onServicioSelected(servicio: ServicioDto) {
        _uiState.update { it.copy(servicio = servicio, error = null) }
    }

    fun onBarberoSelected(barbero: BarberoDto) {
        val anterior = _uiState.value
        val cambio = anterior.barbero?.id != barbero.id
        _uiState.update {
            it.copy(
                barbero = barbero,
                slots = if (cambio) emptyList() else it.slots,
                slot = if (cambio) null else it.slot,
                error = null
            )
        }
        if (cambio && anterior.fecha != null) loadSlots()
    }

    fun onFechaSelected(fecha: String) {
        val cambio = _uiState.value.fecha != fecha
        _uiState.update {
            it.copy(fecha = fecha, slots = if (cambio) emptyList() else it.slots, slot = null, error = null)
        }
        loadSlots()
    }

    fun onSlotSelected(slot: SlotDto) {
        _uiState.update { it.copy(slot = slot, error = null) }
    }

    fun onNotasChanged(notas: String) {
        _uiState.update { it.copy(notas = notas) }
    }

    fun confirm() {
        val state = _uiState.value
        val servicio = state.servicio ?: return
        val barbero = state.barbero ?: return
        val fecha = state.fecha ?: return
        val slot = state.slot ?: return
        if (state.submitting) return

        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val cita = ApiClient.call {
                    ApiClient.service().crearCita(
                        CitaRequest(
                            barberoId = barbero.id,
                            servicioId = servicio.id,
                            fecha = fecha,
                            horaInicio = slot.horaInicio,
                            notas = state.notas.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                }
                // El turno elegido ya no está libre: recarga los slots antes de volver.
                _uiState.update { it.copy(submitting = false, created = cita) }
                loadSlots()
            } catch (error: ApiException) {
                _uiState.update { it.copy(submitting = false, error = error.message) }
                loadSlots()
            }
        }
    }

    fun consumeCreated() {
        _uiState.update { it.copy(created = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadSlots() {
        val request = ++slotsRequest
        val state = _uiState.value
        val barbero = state.barbero
        val fecha = state.fecha
        if (barbero == null || fecha == null) {
            _uiState.update { it.copy(slotsLoading = false, slots = emptyList(), slot = null) }
            return
        }
        _uiState.update { it.copy(slotsLoading = true, slot = null) }
        viewModelScope.launch {
            try {
                val response = ApiClient.call {
                    ApiClient.service().disponibilidad(barbero.id, fecha)
                }
                if (request == slotsRequest) {
                    _uiState.update { it.copy(slotsLoading = false, slots = response.slots) }
                }
            } catch (error: ApiException) {
                if (request == slotsRequest) {
                    _uiState.update {
                        it.copy(slotsLoading = false, slots = emptyList(), error = error.message)
                    }
                }
            }
        }
    }
}
