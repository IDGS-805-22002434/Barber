package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentDao
import kotlinx.coroutines.flow.Flow

class AppointmentRepository(private val appointmentDao: AppointmentDao) {

    val allAppointments: Flow<List<Appointment>> = appointmentDao.getAllAppointments()

    suspend fun insert(appointment: Appointment) {
        appointmentDao.insertAppointment(appointment)
    }

    suspend fun updateStatus(id: Long, status: String) {
        appointmentDao.updateStatus(id, status)
    }

    suspend fun delete(appointment: Appointment) {
        appointmentDao.deleteAppointment(appointment)
    }
}