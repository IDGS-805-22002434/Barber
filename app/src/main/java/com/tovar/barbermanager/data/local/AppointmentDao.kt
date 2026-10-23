package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    /** Citas de un usuario (RNF07: cada cliente solo ve las suyas). */
    @Query("SELECT * FROM appointments WHERE userEmail = :email ORDER BY date, time")
    fun observeByUser(email: String): Flow<List<Appointment>>

    /** Todas las citas: solo para el administrador. */
    @Query("SELECT * FROM appointments ORDER BY date, time")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: Long): Appointment?

    /** Horas ocupadas: toda cita que no esté cancelada. */
    @Query(
        "SELECT time FROM appointments " +
                "WHERE barberId = :barberId AND date = :date AND status != 'CANCELADA'"
    )
    suspend fun occupiedTimes(barberId: Long, date: String): List<String>

    @Insert
    suspend fun insert(appointment: Appointment): Long

    @Query("UPDATE appointments SET date = :date, time = :time WHERE id = :id")
    suspend fun updateSchedule(id: Long, date: String, time: String)

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Insert
    suspend fun insertHistory(entry: AppointmentHistory)

    @Query("SELECT * FROM appointment_history WHERE appointmentId = :appointmentId ORDER BY createdAt, id")
    suspend fun history(appointmentId: Long): List<AppointmentHistory>
}
