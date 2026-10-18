package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.Service
import com.tovar.barbermanager.data.local.ServiceDao
import kotlinx.coroutines.flow.Flow

/**
 * Único punto de acceso a los servicios.
 * Cuando exista backend, aquí se sustituyen las llamadas a Room por la API
 * (misma interfaz, sin tocar la UI).
 */
class ServiceRepository(private val dao: ServiceDao) {

    fun observeAll(): Flow<List<Service>> = dao.observeAll()

    suspend fun getById(id: Long): Service? = dao.getById(id)

    suspend fun save(service: Service) {
        if (service.id == 0L) {
            dao.insert(service)
        } else {
            dao.update(service)
        }
    }

    suspend fun setActive(id: Long, active: Boolean) {
        dao.setActive(id, active)
    }
}
