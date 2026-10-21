package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM barber_schedules WHERE barberId = :barberId")
    suspend fun schedule(barberId: Long): List<BarberSchedule>

    @Query("SELECT * FROM barber_schedules WHERE barberId = :barberId")
    fun observeSchedule(barberId: Long): Flow<List<BarberSchedule>>

    /** Reemplaza el renglón del mismo barbero y día (índice único). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BarberSchedule)

    @Query("SELECT time FROM blocked_slots WHERE barberId = :barberId AND date = :date")
    suspend fun blockedTimes(barberId: Long, date: String): List<String>

    @Query(
        "SELECT * FROM blocked_slots WHERE barberId = :barberId AND date >= :fromDate " +
                "ORDER BY date, time"
    )
    fun observeBlocks(barberId: Long, fromDate: String): Flow<List<BlockedSlot>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlock(slot: BlockedSlot): Long

    @Query("DELETE FROM blocked_slots WHERE id = :id")
    suspend fun deleteBlock(id: Long)
}
