package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.BarberSchedule
import com.tovar.barbermanager.data.local.BlockedSlot
import com.tovar.barbermanager.data.local.ScheduleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Gestión de horarios y bloqueos de un barbero (RF04.03, solo administrador). */
class ScheduleRepository(private val dao: ScheduleDao) {

    /** Los 7 días (1 = lunes … 7 = domingo); los que no tienen renglón usan el horario por defecto. */
    fun observeWeek(barberId: Long): Flow<List<Scheduling.DayHours>> =
        dao.observeSchedule(barberId).map { rows ->
            (1..7).map { day -> Scheduling.hoursFor(day, rows) }
        }

    fun observeBlocks(barberId: Long): Flow<List<BlockedSlot>> =
        dao.observeBlocks(barberId, Dates.today())

    suspend fun saveDay(barberId: Long, dayOfWeek: Int, hours: Scheduling.DayHours) {
        dao.upsert(
            BarberSchedule(
                barberId = barberId,
                dayOfWeek = dayOfWeek,
                startMinutes = hours.start,
                endMinutes = hours.end,
                active = hours.active
            )
        )
    }

    suspend fun block(barberId: Long, date: String, time: String) {
        dao.insertBlock(BlockedSlot(barberId = barberId, date = date, time = time))
    }

    suspend fun unblock(id: Long) {
        dao.deleteBlock(id)
    }
}
