package com.tovar.barbermanager

import com.tovar.barbermanager.data.BarberRepository
import com.tovar.barbermanager.data.local.Barber
import com.tovar.barbermanager.data.local.BarberDao
import com.tovar.barbermanager.data.local.BarberSpecialty
import com.tovar.barbermanager.data.local.BarberWithSpecialties
import com.tovar.barbermanager.data.local.Specialty
import com.tovar.barbermanager.data.local.SpecialtyDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el CRUD de barberos y la relación N:N con especialidades (RF03.02 / RF03.03)
 * usando DAOs falsos: insertar, actualizar sin duplicar y reemplazar las especialidades.
 */
class BarberRepositoryTest {

    private class FakeBarberDao : BarberDao {
        val stored = MutableStateFlow<List<Barber>>(emptyList())
        val links = MutableStateFlow<List<BarberSpecialty>>(emptyList())
        private var nextId = 1L

        override fun observeActiveWithSpecialties(): Flow<List<BarberWithSpecialties>> =
            combineWithSpecialties { it.active }

        override fun observeAllWithSpecialties(): Flow<List<BarberWithSpecialties>> =
            combineWithSpecialties { true }

        override suspend fun getWithSpecialties(id: Long): BarberWithSpecialties? {
            val barber = stored.value.firstOrNull { it.id == id } ?: return null
            return barber.toWithSpecialties()
        }

        override suspend fun getAll(): List<Barber> = stored.value

        override suspend fun getById(id: Long): Barber? =
            stored.value.firstOrNull { it.id == id }

        override suspend fun count(): Int = stored.value.size

        override suspend fun specialtyLinkCount(): Int = links.value.size

        override suspend fun insertAll(barbers: List<Barber>) {
            stored.update { current -> current + barbers }
        }

        override suspend fun insert(barber: Barber): Long {
            val id = if (barber.id == 0L) nextId++ else barber.id
            stored.update { it + barber.copy(id = id) }
            return id
        }

        override suspend fun update(barber: Barber) {
            stored.update { list -> list.map { if (it.id == barber.id) barber else it } }
        }

        override suspend fun insertBarberSpecialties(links: List<BarberSpecialty>) {
            this.links.update { it + links }
        }

        override suspend fun clearSpecialties(barberId: Long) {
            links.update { list -> list.filterNot { it.barberId == barberId } }
        }

        private fun combineWithSpecialties(
            predicate: (Barber) -> Boolean
        ): Flow<List<BarberWithSpecialties>> = stored.map { barbers ->
            barbers.filter(predicate).map { it.toWithSpecialties() }
        }

        private fun Barber.toWithSpecialties(): BarberWithSpecialties {
            val ids = links.value.filter { it.barberId == id }.map { it.specialtyId }
            return BarberWithSpecialties(
                barber = this,
                specialties = fakeSpecialties.filter { it.id in ids }
            )
        }
    }

    private class FakeSpecialtyDao : SpecialtyDao {
        val stored = MutableStateFlow<List<Specialty>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Specialty>> =
            stored.map { list -> list.sortedBy { it.name.lowercase() } }

        override suspend fun getAll(): List<Specialty> = stored.value

        override suspend fun getById(id: Long): Specialty? =
            stored.value.firstOrNull { it.id == id }

        override suspend fun count(): Int = stored.value.size

        override suspend fun insertAll(specialties: List<Specialty>) {
            stored.update { current -> current + specialties }
        }

        override suspend fun insert(specialty: Specialty) {
            val id = if (specialty.id == 0L) nextId++ else specialty.id
            stored.update { it + specialty.copy(id = id) }
        }

        override suspend fun update(specialty: Specialty) {
            stored.update { list -> list.map { if (it.id == specialty.id) specialty else it } }
        }
    }

    companion object {
        private val fakeSpecialties = listOf(
            Specialty(id = 1L, name = "Cortes clásicos"),
            Specialty(id = 2L, name = "Barba y navaja"),
            Specialty(id = 3L, name = "Colorimetría")
        )
    }

    @Test
    fun `guardar un barbero nuevo lo inserta y asocia sus especialidades`() = runTest {
        val dao = FakeBarberDao()
        val repository = BarberRepository(dao)

        val id = repository.save(
            Barber(name = "Ana Prueba", yearsExperience = 3),
            specialtyIds = listOf(1L, 2L)
        )

        assertEquals("Ana Prueba", dao.stored.value.single().name)
        assertEquals(2, dao.links.value.size)
        assertTrue(dao.links.value.all { it.barberId == id })
    }

    @Test
    fun `guardar un barbero existente lo actualiza sin duplicar y reemplaza especialidades`() = runTest {
        val dao = FakeBarberDao()
        val repository = BarberRepository(dao)
        val id = repository.save(
            Barber(name = "Original", yearsExperience = 5),
            specialtyIds = listOf(1L)
        )

        repository.save(
            Barber(id = id, name = "Editado", yearsExperience = 9, active = false),
            specialtyIds = listOf(2L, 3L)
        )

        assertEquals(1, dao.stored.value.size)
        val updated = dao.stored.value.single()
        assertEquals("Editado", updated.name)
        assertEquals(9, updated.yearsExperience)
        assertEquals(false, updated.active)
        val linked = dao.links.value.filter { it.barberId == id }.map { it.specialtyId }
        assertEquals(listOf(2L, 3L), linked)
    }

    @Test
    fun `el cliente solo ve barberos activos con sus especialidades`() = runTest {
        val dao = FakeBarberDao()
        val repository = BarberRepository(dao)
        repository.save(Barber(name = "Activo", yearsExperience = 2), listOf(1L))
        repository.save(
            Barber(name = "Inactivo", yearsExperience = 4, active = false),
            listOf(2L)
        )

        val visible = repository.observeActive().first()

        assertEquals(1, visible.size)
        assertEquals("Activo", visible.single().barber.name)
        assertEquals("Cortes clásicos", visible.single().specialties.single().name)
    }
}
