package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.Appointment

/**
 * Avisos que el repositorio de citas emite cuando una operación ya quedó guardada.
 * Así el repositorio no depende de Android: las pruebas lo usan sin notificaciones.
 */
interface AppointmentEvents {
    suspend fun onBooked(appointment: Appointment)
    suspend fun onRescheduled(appointment: Appointment)
    suspend fun onCancelled(appointment: Appointment)
    suspend fun onCompleted(appointment: Appointment)
}
