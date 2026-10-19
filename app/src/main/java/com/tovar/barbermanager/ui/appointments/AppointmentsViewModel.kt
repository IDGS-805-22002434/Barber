package com.tovar.barbermanager.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.AppointmentRepository
import com.tovar.barbermanager.data.local.Appointment
import kotlinx.coroutines.launch

class AppointmentsViewModel(
    private val repository: AppointmentRepository
) : ViewModel() {

    val appointments = repository.allAppointments.asLiveData()

    fun updateStatus(id: Long, newStatus: String) {
        viewModelScope.launch {
            repository.updateStatus(id, newStatus)
        }
    }

    fun deleteAppointment(appointment: Appointment) {
        viewModelScope.launch {
            repository.delete(appointment)
        }
    }
}