package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object HistoryAction {
    const val REPROGRAMADA = "REPROGRAMADA"
    const val CANCELADA = "CANCELADA"
}

@Entity(
    tableName = "appointment_history",
    indices = [Index(value = ["appointmentId"])]
)
data class AppointmentHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appointmentId: Long,
    val action: String,
    val oldDate: String,
    val oldTime: String,
    /** Vacíos cuando la acción es una cancelación. */
    val newDate: String,
    val newTime: String,
    val createdAt: Long = System.currentTimeMillis()
)