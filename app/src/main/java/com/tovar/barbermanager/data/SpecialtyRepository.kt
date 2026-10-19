package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.Specialty
import com.tovar.barbermanager.data.local.SpecialtyDao
import kotlinx.coroutines.flow.Flow

class SpecialtyRepository(private val dao: SpecialtyDao) {

    fun observeAll(): Flow<List<Specialty>> = dao.observeAll()

    suspend fun getById(id: Long): Specialty? = dao.getById(id)

    suspend fun save(specialty: Specialty) {
        if (specialty.id == 0L) {
            dao.insert(specialty)
        } else {
            dao.update(specialty)
        }
    }

    /** Evita duplicados en el catálogo (el nombre debe ser único). */
    suspend fun nameExists(name: String, excludingId: Long = 0L): Boolean =
        dao.getAll().any { it.name.equals(name, ignoreCase = true) && it.id != excludingId }
}
