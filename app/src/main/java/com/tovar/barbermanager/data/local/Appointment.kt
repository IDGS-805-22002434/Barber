package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/** Estados de una cita. */
object AppointmentStatus {
    const val CONFIRMADA = "CONFIRMADA"
    const val COMPLETADA = "COMPLETADA"
    const val CANCELADA = "CANCELADA"
}

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
    val createdAt: Long = System.currentTimeMillis(),
    val couponCode: String? = null,
    @ColumnInfo(defaultValue = "0") val discountCents: Long = 0L
)
