package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BarberDao {

    /** Solo activos: lo que ve el cliente (RF03). */
    @Transaction
    @Query("SELECT * FROM barbers WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeActiveWithSpecialties(): Flow<List<BarberWithSpecialties>>

    /** Todos: lo que ve el administrador. */
    @Transaction
    @Query("SELECT * FROM barbers ORDER BY name COLLATE NOCASE")
    fun observeAllWithSpecialties(): Flow<List<BarberWithSpecialties>>

    @Transaction
    @Query("SELECT * FROM barbers WHERE id = :id")
    suspend fun getWithSpecialties(id: Long): BarberWithSpecialties?

    @Query("SELECT * FROM barbers")
    suspend fun getAll(): List<Barber>

    @Query("SELECT * FROM barbers WHERE id = :id")
    suspend fun getById(id: Long): Barber?

    @Query("SELECT COUNT(*) FROM barbers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM barber_specialties")
    suspend fun specialtyLinkCount(): Int

    @Insert
    suspend fun insertAll(barbers: List<Barber>)

    /** Devuelve el id generado para el barbero nuevo. */
    @Insert
    suspend fun insert(barber: Barber): Long

    @Update
    suspend fun update(barber: Barber)

    @Insert
    suspend fun insertBarberSpecialties(links: List<BarberSpecialty>)

    @Query("DELETE FROM barber_specialties WHERE barberId = :barberId")
    suspend fun clearSpecialties(barberId: Long)
}
