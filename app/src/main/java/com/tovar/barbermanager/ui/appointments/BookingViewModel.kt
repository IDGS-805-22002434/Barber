package com.tovar.barbermanager.ui.appointments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.AppointmentRepository
import com.tovar.barbermanager.data.BarberRepository
import com.tovar.barbermanager.data.BookingResult
import com.tovar.barbermanager.data.ServiceRepository
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.Barber
import com.tovar.barbermanager.data.local.Service
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val services: List<Service> = emptyList(),
    val barbers: List<Barber> = emptyList(),
    val service: Service? = null,
    val barber: Barber? = null,

    val date: String? = null,
    val slots: List<String> = emptyList(),
    val slotsLoading: Boolean = false,

    val time: String? = null,

    val editing: Appointment? = null,
    val defaultName: String = ""
) {
    val barberId: Long? get() = editing?.barberId ?: barber?.id
}

class BookingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = AppointmentRepository(database)
    private val session = SessionManager(application)

    private val _state = MutableStateFlow(BookingState())
    val state: StateFlow<BookingState> = _state.asStateFlow()

    sealed interface Event {
        data class Saved(val appointmentId: Long) : Event
        object SlotTaken : Event
        object NotAllowed : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var slotsJob: Job? = null
    private var saving = false

    fun load(appointmentId: Long) {
        if (_state.value.loaded) return
        viewModelScope.launch {
            if (appointmentId != NEW) {
                val appointment = repository.getById(appointmentId)
                _state.value = BookingState(
                    loaded = true,
                    notFound = appointment == null,
                    editing = appointment
                )
            } else {
                val services = ServiceRepository(database.serviceDao())
                    .observeAll().first().filter { it.active }
                val barbers = BarberRepository(database.barberDao())
                    .observeActive().first().map { it.barber }
                _state.value = BookingState(
                    loaded = true,
                    services = services,
                    barbers = barbers,
                    defaultName = nameFromEmail(session.currentEmail)
                )
            }
        }
    }

    fun selectService(service: Service) {
        _state.update { it.copy(service = service) }
    }

    fun selectBarber(barber: Barber) {
        _state.update { it.copy(barber = barber, time = null) }
        refreshSlots()
    }

    fun selectDate(date: String) {
        _state.update { it.copy(date = date, time = null) }
        refreshSlots()
    }

    fun selectTime(time: String) {
        _state.update { it.copy(time = time) }
    }

    fun confirm(name: String, phone: String) {
        val current = _state.value
        val date = current.date ?: return
        val time = current.time ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val editing = current.editing
                val result: BookingResult = if (editing != null) {
                    repository.reschedule(editing.id, date, time, session.isAdmin)
                } else {
                    val service = current.service ?: return@launch
                    val barber = current.barber ?: return@launch
                    repository.book(
                        Appointment(
                            userEmail = session.currentEmail.orEmpty(),
                            clientName = name,
                            clientPhone = phone,
                            barberId = barber.id,
                            barberName = barber.name,
                            serviceId = service.id,
                            serviceName = service.name,
                            priceCents = service.priceCents,
                            date = date,
                            time = time
                        )
                    )
                }
                when (result) {
                    is BookingResult.Success -> _events.send(Event.Saved(result.appointmentId))
                    BookingResult.SlotUnavailable -> {
                        _state.update { it.copy(time = null) }
                        refreshSlots()
                        _events.send(Event.SlotTaken)
                    }
                    BookingResult.NotAllowed -> _events.send(Event.NotAllowed)
                }
            } finally {
                saving = false
            }
        }
    }

    private fun refreshSlots() {
        slotsJob?.cancel()
        val barberId = _state.value.barberId
        val date = _state.value.date
        if (barberId == null || date == null) {
            _state.update { it.copy(slots = emptyList(), slotsLoading = false) }
            return
        }
        _state.update { it.copy(slotsLoading = true) }
        slotsJob = viewModelScope.launch {
            val slots = repository.freeSlots(barberId, date)
            _state.update {
                it.copy(
                    slots = slots,
                    slotsLoading = false,
                    time = it.time?.takeIf { chosen -> chosen in slots }
                )
            }
        }
    }

    private fun nameFromEmail(email: String?): String =
        email?.substringBefore('@')
            ?.split('.', '_', '-', '+')
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .orEmpty()

    companion object {
        const val NEW = 0L
    }
}
