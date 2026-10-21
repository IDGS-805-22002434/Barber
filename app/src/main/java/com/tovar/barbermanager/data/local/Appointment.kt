package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Estados de una cita. */
object AppointmentStatus {
    const val CONFIRMADA = "CONFIRMADA"
    const val COMPLETADA = "COMPLETADA"
    const val CANCELADA = "CANCELADA"
}

/**
 * Cita registrada (RF05.02). Guarda el nombre del servicio y del barbero tal como
 * estaban al agendar, para que el comprobante no cambie si luego se editan.
 * Fecha "yyyy-MM-dd" y hora "HH:mm".
 */
@Entity(
    tableName = "appointments",
    indices = [
        Index(value = ["barberId", "date", "time"]),
        Index(value = ["userEmail"])
    ]
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val clientName: String,
    val clientPhone: String,
    val barberId: Long,
    val barberName: String,
    val serviceId: Long,
    val serviceName: String,
    val priceCents: Long,
    val date: String,
    val time: String,
    val status: String = AppointmentStatus.CONFIRMADA,
    val createdAt: Long = System.currentTimeMillis()
)
