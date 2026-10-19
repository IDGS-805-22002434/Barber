package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecialtyDao {

    @Query("SELECT * FROM specialties ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Specialty>>

    @Query("SELECT * FROM specialties ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Specialty>

    @Query("SELECT * FROM specialties WHERE id = :id")
    suspend fun getById(id: Long): Specialty?

    @Query("SELECT COUNT(*) FROM specialties")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(specialties: List<Specialty>)

    @Insert
    suspend fun insert(specialty: Specialty)

    @Update
    suspend fun update(specialty: Specialty)
}
