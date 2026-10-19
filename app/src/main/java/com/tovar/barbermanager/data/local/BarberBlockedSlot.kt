package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bloqueo por fecha para vacaciones, descansos o cierres extraordinarios. */
@Entity(
    tableName = "barber_blocked_slots",
    foreignKeys = [
        ForeignKey(
            entity = Barber::class,
            parentColumns = ["id"],
            childColumns = ["barberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["barberId"]), Index(value = ["barberId", "date"])]
)
data class BarberBlockedSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val barberId: Long,
    /** Fecha en ISO-8601: yyyy-MM-dd. */
    val date: String,
    val startTime: String,
    val endTime: String,
    val reason: String = "",
    val active: Boolean = true
)
