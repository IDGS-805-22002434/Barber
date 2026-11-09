package com.tovar.barbermanager.ui.appointments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.AppointmentRepository
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.remote.ApiClient
import com.tovar.barbermanager.data.remote.ApiException
import com.tovar.barbermanager.data.remote.CitaDto
import com.tovar.barbermanager.data.remote.EstadoRequest
import com.tovar.barbermanager.data.remote.ReprogramarRequest
import com.tovar.barbermanager.data.remote.SlotDto
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppointmentsUiState(
    val loading: Boolean = true,
    val tab: Int = AppointmentsViewModel.TAB_UPCOMING,
    val items: List<Appointment> = emptyList()
)

class AppointmentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppointmentRepository(AppDatabase.getInstance(application))
    val items: List<CitaDto> = emptyList(),
    val error: String? = null
)

/** Lista de citas (cliente: las suyas; admin: toda la agenda) con cancelar/reprogramar. */
class AppointmentsViewModel(application: Application) : AndroidViewModel(application) {

    private val session = SessionManager(application)

    val isAdmin: Boolean = session.isAdmin

    private val tab = MutableStateFlow(TAB_UPCOMING)

    val uiState: StateFlow<AppointmentsUiState> = combine(
        repository.observeFor(session.currentEmail, isAdmin),
        tab
    ) { all, selected ->
        val now = Dates.nowKey()
        val (upcoming, past) = all.partition {
            it.status == AppointmentStatus.CONFIRMADA && "${it.date} ${it.time}" >= now
        }
        AppointmentsUiState(
            loading = false,
            tab = selected,
            items = if (selected == TAB_UPCOMING) upcoming else past.reversed()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppointmentsUiState())

    sealed interface Event {
        object Cancelled : Event
        object Completed : Event
        object NotAllowed : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun selectTab(index: Int) {
        tab.value = index
    }

    fun cancel(appointment: Appointment) {
        viewModelScope.launch {
            _events.send(
                if (repository.cancel(appointment.id, isAdmin)) Event.Cancelled else Event.NotAllowed
            )
        }
    }

    fun complete(appointment: Appointment) {
        if (!isAdmin) return
        viewModelScope.launch {
            _events.send(
                if (repository.complete(appointment.id)) Event.Completed else Event.NotAllowed
            )
        }
    }

    companion object {
        const val TAB_UPCOMING = 0
        const val TAB_PAST = 1
    private val _uiState = MutableStateFlow(AppointmentsUiState())
    val uiState: StateFlow<AppointmentsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Turnos libres para reprogramar (se cargan bajo demanda desde el diálogo). */
    private val _slots = MutableStateFlow<List<SlotDto>>(emptyList())
    val slots: StateFlow<List<SlotDto>> = _slots.asStateFlow()

    private val _slotsLoading = MutableStateFlow(false)
    val slotsLoading: StateFlow<Boolean> = _slotsLoading.asStateFlow()

    /** Invalida respuestas viejas si se cierra el diálogo o se elige otra fecha. */
    private var slotsRequest = 0

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val citas = ApiClient.call { ApiClient.service().citas() }
                _uiState.update { it.copy(loading = false, items = citas) }
            } catch (error: ApiException) {
                _uiState.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun cancelar(cita: CitaDto) = cambiarEstado(cita, "CANCELADA")

    fun cambiarEstado(cita: CitaDto, estado: String) {
        viewModelScope.launch {
            try {
                ApiClient.call {
                    ApiClient.service().cambiarEstadoCita(cita.id, EstadoRequest(estado))
                }
                _events.tryEmit(
                    getApplication<Application>().getString(
                        when (estado) {
                            "CANCELADA" -> com.tovar.barbermanager.R.string.cita_cancelada
                            "CONFIRMADA" -> com.tovar.barbermanager.R.string.cita_confirmada
                            else -> com.tovar.barbermanager.R.string.cita_estado_actualizado
                        }
                    )
                )
                refresh()
            } catch (error: ApiException) {
                _events.tryEmit(error.message)
            }
        }
    }

    fun limpiarSlots() {
        slotsRequest++
        _slots.value = emptyList()
        _slotsLoading.value = false
    }

    fun cargarSlotsParaReprogramar(barberoId: Long, fecha: String) {
        val request = ++slotsRequest
        _slotsLoading.value = true
        _slots.value = emptyList()
        viewModelScope.launch {
            try {
                val response = ApiClient.call {
                    ApiClient.service().disponibilidad(barberoId, fecha)
                }
                if (request == slotsRequest) _slots.value = response.slots
            } catch (error: ApiException) {
                if (request == slotsRequest) _events.tryEmit(error.message)
            } finally {
                if (request == slotsRequest) _slotsLoading.value = false
            }
        }
    }

    fun reprogramar(cita: CitaDto, fecha: String, horaInicio: String) {
        viewModelScope.launch {
            try {
                ApiClient.call {
                    ApiClient.service().reprogramarCita(
                        cita.id,
                        ReprogramarRequest(fecha = fecha, horaInicio = horaInicio)
                    )
                }
                _events.tryEmit(getApplication<Application>().getString(com.tovar.barbermanager.R.string.cita_reprogramada))
                refresh()
            } catch (error: ApiException) {
                _events.tryEmit(error.message)
            }
        }
    }
}
