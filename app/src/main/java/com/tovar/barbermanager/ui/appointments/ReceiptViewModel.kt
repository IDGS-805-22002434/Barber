package com.tovar.barbermanager.ui.appointments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.AppointmentRepository
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReceiptState(
    val loaded: Boolean = false,
    val appointment: Appointment? = null,
    val history: List<AppointmentHistory> = emptyList()
)

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppointmentRepository(AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(ReceiptState())
    val state: StateFlow<ReceiptState> = _state.asStateFlow()

    fun load(appointmentId: Long) {
        if (_state.value.loaded) return
        viewModelScope.launch {
            val appointment = repository.getById(appointmentId)
            val history = if (appointment != null) repository.history(appointmentId) else emptyList()
            _state.value = ReceiptState(loaded = true, appointment = appointment, history = history)
        }
    }
}
