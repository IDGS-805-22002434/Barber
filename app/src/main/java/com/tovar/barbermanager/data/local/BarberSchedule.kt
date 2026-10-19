package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Horario semanal de trabajo: dayOfWeek usa Calendar (1=domingo ... 7=sábado). */
@Entity(
    tableName = "barber_schedules",
    foreignKeys = [
        ForeignKey(
            entity = Barber::class,
            parentColumns = ["id"],
            childColumns = ["barberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["barberId"]), Index(value = ["barberId", "dayOfWeek"], unique = true)]
)
data class BarberSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val barberId: Long,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val slotDurationMinutes: Int = 30,
    val active: Boolean = true
)
