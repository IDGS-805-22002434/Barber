package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {

    @Query("SELECT * FROM services ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getById(id: Long): Service?

    @Query("SELECT COUNT(*) FROM services")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(services: List<Service>)

    @Insert
    suspend fun insert(service: Service)

    @Update
    suspend fun update(service: Service)

    @Query("UPDATE services SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
