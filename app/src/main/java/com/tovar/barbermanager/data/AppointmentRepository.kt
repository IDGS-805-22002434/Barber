package com.tovar.barbermanager.data

import androidx.room.withTransaction
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentHistory
import com.tovar.barbermanager.data.local.AppointmentStatus
import com.tovar.barbermanager.data.local.HistoryAction
import kotlinx.coroutines.flow.Flow

/** Resultado de agendar o reprogramar. */
sealed interface BookingResult {
    data class Success(val appointmentId: Long) : BookingResult

    /** El horario ya no está libre (otro usuario lo tomó, fue bloqueado o ya pasó). */
    object SlotUnavailable : BookingResult

    /** La política de la barbería no permite el cambio, o la cita ya no existe. */
    object NotAllowed : BookingResult
}

/**
 * Único punto de acceso a las citas y a la disponibilidad.
 * Agendar, reprogramar y cancelar se hacen dentro de una transacción de Room:
 * se revisa que el horario siga libre y se guarda en un solo paso, así dos usuarios
 * no pueden reservar el mismo horario del mismo barbero (RNF08).
 */
class AppointmentRepository(private val db: AppDatabase) {

    private val appointments = db.appointmentDao()
    private val schedules = db.scheduleDao()

    /** Cliente: sus citas. Administrador: todas. */
    fun observeFor(email: String?, isAdmin: Boolean): Flow<List<Appointment>> =
        if (isAdmin) appointments.observeAll() else appointments.observeByUser(email.orEmpty())

    suspend fun getById(id: Long): Appointment? = appointments.getById(id)

    suspend fun history(appointmentId: Long): List<AppointmentHistory> =
        appointments.history(appointmentId)

    /** Horarios libres de un barbero en una fecha (RF04, RF04.01, RF04.02). */
    suspend fun freeSlots(barberId: Long, date: String): List<String> =
        computeFreeSlots(barberId, date)

    suspend fun book(appointment: Appointment): BookingResult = db.withTransaction {
        if (appointment.time !in computeFreeSlots(appointment.barberId, appointment.date)) {
            BookingResult.SlotUnavailable
        } else {
            val id = appointments.insert(
                appointment.copy(id = 0L, status = AppointmentStatus.CONFIRMADA)
            )
            BookingResult.Success(id)
        }
    }

    suspend fun reschedule(
        id: Long,
        newDate: String,
        newTime: String,
        isAdmin: Boolean
    ): BookingResult = db.withTransaction {
        val current = appointments.getById(id)
            ?: return@withTransaction BookingResult.NotAllowed
        val allowed = Scheduling.canChange(
            current.status, current.date, current.time, System.currentTimeMillis(), isAdmin
        )
        if (!allowed) return@withTransaction BookingResult.NotAllowed
        if (newTime !in computeFreeSlots(current.barberId, newDate)) {
            return@withTransaction BookingResult.SlotUnavailable
        }
        appointments.updateSchedule(id, newDate, newTime)
        appointments.insertHistory(
            AppointmentHistory(
                appointmentId = id,
                action = HistoryAction.REPROGRAMADA,
                oldDate = current.date,
                oldTime = current.time,
                newDate = newDate,
                newTime = newTime
            )
        )
        BookingResult.Success(id)
    }

    /** Cancela y libera el horario (RF06.04, RF06.05). Devuelve false si no se permite. */
    suspend fun cancel(id: Long, isAdmin: Boolean): Boolean = db.withTransaction {
        val current = appointments.getById(id) ?: return@withTransaction false
        val allowed = Scheduling.canChange(
            current.status, current.date, current.time, System.currentTimeMillis(), isAdmin
        )
        if (!allowed) return@withTransaction false
        appointments.updateStatus(id, AppointmentStatus.CANCELADA)
        appointments.insertHistory(
            AppointmentHistory(
                appointmentId = id,
                action = HistoryAction.CANCELADA,
                oldDate = current.date,
                oldTime = current.time,
                newDate = "",
                newTime = ""
            )
        )
        true
    }

    /** Marca una cita como atendida (solo administrador). */
    suspend fun complete(id: Long): Boolean = db.withTransaction {
        val current = appointments.getById(id) ?: return@withTransaction false
        if (current.status != AppointmentStatus.CONFIRMADA) return@withTransaction false
        appointments.updateStatus(id, AppointmentStatus.COMPLETADA)
        true
    }

    private suspend fun computeFreeSlots(barberId: Long, date: String): List<String> {
        val today = Dates.today()
        if (date < today || date > Dates.plusDays(today, Scheduling.BOOKING_HORIZON_DAYS)) {
            return emptyList()
        }
        val hours = Scheduling.hoursFor(Dates.dayOfWeek(date), schedules.schedule(barberId))
        return Scheduling.freeSlots(
            hours = hours,
            occupied = appointments.occupiedTimes(barberId, date).toSet(),
            blocked = schedules.blockedTimes(barberId, date).toSet(),
            afterMinutes = if (date == today) Dates.nowMinutes() else null
        )
    }
}
