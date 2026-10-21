package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


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
)