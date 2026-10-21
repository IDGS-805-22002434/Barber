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
import kotlinx.coroutines.launch

data class AppointmentsUiState(
    val loading: Boolean = true,
    val tab: Int = AppointmentsViewModel.TAB_UPCOMING,
    val items: List<Appointment> = emptyList()
)

class AppointmentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppointmentRepository(AppDatabase.getInstance(application))
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
    }
}
