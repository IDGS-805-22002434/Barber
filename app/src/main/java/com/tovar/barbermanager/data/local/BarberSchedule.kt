package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tovar.barbermanager.data.Scheduling

@Entity(
    tableName = "barber_schedules",
    indices = [Index(value = ["barberId", "dayOfWeek"], unique = true)]
)
data class BarberSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val barberId: Long,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val active: Boolean = true
) {
    val startTime: String
        get() = Scheduling.formatMinutes(startMinutes)

    val endTime: String
        get() = Scheduling.formatMinutes(endMinutes)

    val slotDurationMinutes: Int
        get() = Scheduling.SLOT_MINUTES
}
