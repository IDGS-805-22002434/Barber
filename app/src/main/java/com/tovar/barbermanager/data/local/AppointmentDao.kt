package com.tovar.barbermanager.data.local

import androidx.room.*

import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    /** Uso administrativo: consulta todas las citas del catálogo local. */
    @Query("SELECT * FROM appointments ORDER BY date DESC, time DESC")
    fun getAllAppointments(): Flow<List<Appointment>>

    /** Uso de cliente: limita las citas al usuario autenticado. */
    @Query("SELECT * FROM appointments WHERE clientUserId = :userId ORDER BY date DESC, time DESC")
    fun observeForClient(userId: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Long): Appointment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment)

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}