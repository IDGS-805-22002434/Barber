package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Historial inmutable de reprogramaciones y cancelaciones de una cita. */
@Entity(
    tableName = "appointment_changes",
    foreignKeys = [
        ForeignKey(
            entity = Appointment::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["appointmentId"])]
)
data class AppointmentChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appointmentId: Long,
    val changeType: String,
    val previousDate: String,
    val previousTime: String,
    val newDate: String? = null,
    val newTime: String? = null,
    val reason: String? = null,
    val changedAt: Long = System.currentTimeMillis()
)
