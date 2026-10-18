package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.Barber
import com.tovar.barbermanager.data.local.BarberDao
import com.tovar.barbermanager.data.local.BarberSpecialty
import com.tovar.barbermanager.data.local.BarberWithSpecialties
import kotlinx.coroutines.flow.Flow

class BarberRepository(private val dao: BarberDao) {

    fun observeActive(): Flow<List<BarberWithSpecialties>> = dao.observeActiveWithSpecialties()

    fun observeAll(): Flow<List<BarberWithSpecialties>> = dao.observeAllWithSpecialties()

    suspend fun getWithSpecialties(id: Long): BarberWithSpecialties? = dao.getWithSpecialties(id)

    /** Guarda el barbero y reemplaza sus especialidades por las indicadas (RF03.03). */
    suspend fun save(barber: Barber, specialtyIds: List<Long>): Long {
        val id = if (barber.id == 0L) {
            dao.insert(barber)
        } else {
            dao.update(barber)
            barber.id
        }
        dao.clearSpecialties(id)
        if (specialtyIds.isNotEmpty()) {
            dao.insertBarberSpecialties(specialtyIds.map { BarberSpecialty(id, it) })
        }
        return id
    }
}
