package com.tovar.barbermanager

import com.tovar.barbermanager.data.ServiceRepository
import com.tovar.barbermanager.data.local.Service
import com.tovar.barbermanager.data.local.ServiceDao
import com.tovar.barbermanager.data.model.ServiceCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifica la lógica del repositorio con un DAO falso:
 * un servicio nuevo (id = 0) debe INSERTARSE, y uno existente ACTUALIZARSE.
 * Cubre el caso del reporte "agrego un servicio y no se registra".
 */
class ServiceRepositoryTest {

    private class FakeServiceDao : ServiceDao {
        val stored = MutableStateFlow<List<Service>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Service>> =
            stored.map { list -> list.sortedBy { it.name.lowercase() } }

        override suspend fun getById(id: Long): Service? =
            stored.value.firstOrNull { it.id == id }

        override suspend fun count(): Int = stored.value.size

        override suspend fun insertAll(services: List<Service>) {
            stored.update { current -> current + services }
        }

        override suspend fun insert(service: Service) {
            val withId = if (service.id == 0L) service.copy(id = nextId++) else service
            stored.update { it + withId }
        }

        override suspend fun update(service: Service) {
            stored.update { list -> list.map { if (it.id == service.id) service else it } }
        }

        override suspend fun setActive(id: Long, active: Boolean) {
            stored.update { list -> list.map { if (it.id == id) it.copy(active = active) else it } }
        }
    }

    @Test
    fun `guardar un servicio nuevo lo inserta en el catalogo`() = runTest {
        val dao = FakeServiceDao()
        val repository = ServiceRepository(dao)

        repository.save(
            Service(name = "Corte prueba", description = "desc", priceCents = 10_000, category = ServiceCategory.CORTES)
        )

        val saved = dao.stored.value.single()
        assertEquals("Corte prueba", saved.name)
        assertEquals(10_000L, saved.priceCents)
        assertNotNull("El servicio nuevo debe tener id generado", saved.id)
    }

    @Test
    fun `guardar un servicio existente lo actualiza sin duplicar`() = runTest {
        val dao = FakeServiceDao()
        val repository = ServiceRepository(dao)
        repository.save(Service(name = "Original", description = "", priceCents = 5_000, category = ServiceCategory.BARBA))
        val id = dao.statedIdOrFirst()

        repository.save(Service(id = id, name = "Editado", description = "", priceCents = 7_500, category = ServiceCategory.BARBA))

        assertEquals(1, dao.stored.value.size)
        val updated = dao.stored.value.single()
        assertEquals("Editado", updated.name)
        assertEquals(7_500L, updated.priceCents)
    }

    @Test
    fun `setActive desactiva sin borrar el servicio`() = runTest {
        val dao = FakeServiceDao()
        val repository = ServiceRepository(dao)
        repository.save(Service(name = "Cejas", description = "", priceCents = 4_000, category = ServiceCategory.CEJAS))
        val id = dao.statedIdOrFirst()

        repository.setActive(id, active = false)

        assertEquals(1, dao.stored.value.size)
        assertEquals(false, dao.stored.value.single().active)
    }

    private fun FakeServiceDao.statedIdOrFirst(): Long = stored.value.first().id
}
