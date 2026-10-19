package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Proyección usada por el panel de administración para mostrar el nombre del barbero. */
data class BarberScheduleWithName(
    @Embedded val schedule: BarberSchedule,
    val barberName: String
)

@Dao
interface BarberScheduleDao {

    @Query(
        "SELECT barber_schedules.*, barbers.name AS barberName " +
            "FROM barber_schedules INNER JOIN barbers ON barbers.id = barber_schedules.barberId " +
            "ORDER BY barbers.name COLLATE NOCASE, barber_schedules.dayOfWeek"
    )
    fun observeAllWithNames(): Flow<List<BarberScheduleWithName>>

    @Query("SELECT * FROM barbers ORDER BY name COLLATE NOCASE")
    suspend fun getAllBarbers(): List<Barber>

    @Query("SELECT * FROM barber_schedules WHERE barberId = :barberId AND dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getByBarberAndDay(barberId: Long, dayOfWeek: Int): BarberSchedule?

    @Insert
    suspend fun insert(schedule: BarberSchedule): Long

    @Update
    suspend fun update(schedule: BarberSchedule)

    @Query("UPDATE barber_schedules SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
