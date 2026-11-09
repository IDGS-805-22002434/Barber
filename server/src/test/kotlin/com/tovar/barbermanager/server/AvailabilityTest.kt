package com.tovar.barbermanager.server

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvailabilityTest {

    private val horario = Availability.Horario(
        diaSemana = 7,
        inicio = "10:00",
        fin = "14:00",
        duracionMinutos = 30
    )

    @Test
    fun `genera turnos completos del horario`() {
        val slots = Availability.slotsDelDia(horario, emptyList(), emptyList())
        assertEquals(8, slots.size)
        assertEquals("10:00", slots.first().horaInicio)
        assertEquals("14:00", slots.last().horaFin)
        assertEquals("10:30", slots[1].horaInicio)
    }

    @Test
    fun `los bloqueos completos del dia anulan todo`() {
        val slots = Availability.slotsDelDia(
            horario,
            bloqueos = listOf(Availability.rangoDelDia()),
            ocupadas = emptyList()
        )
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `los slots ocupados por citas desaparecen`() {
        val slots = Availability.slotsDelDia(
            horario,
            bloqueos = emptyList(),
            ocupadas = listOf(Availability.Rango("11:00", "12:00"))
        )
        assertEquals(6, slots.size)
        assertTrue(slots.none { it.horaInicio in listOf("11:00", "11:30") })
    }

    @Test
    fun `un bloqueo parcial solo quita lo solapado`() {
        val slots = Availability.slotsDelDia(
            horario,
            bloqueos = listOf(Availability.Rango("12:00", "12:30")),
            ocupadas = emptyList()
        )
        assertEquals(7, slots.size)
        assertTrue(slots.none { it.horaInicio == "12:00" })
    }

    @Test
    fun `no ofrece turnos ya pasados cuando es hoy`() {
        val slots = Availability.slotsDelDia(horario, emptyList(), emptyList(), minutoActual = 11 * 60)
        assertEquals("11:00", slots.first().horaInicio)
        assertEquals(6, slots.size)
    }

    @Test
    fun `sin horario no hay disponibilidad`() {
        assertTrue(Availability.slotsDelDia(null, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `calcula el fin de turno a partir de la duracion`() {
        assertEquals("10:45", Availability.finDeSlot("10:15", 30))
        assertEquals("00:15", Availability.finDeSlot("23:45", 30))
    }

    @Test
    fun `el dia de la semana sigue la convencion de android`() {
        // 2026-10-24 es sábado; Android usa 1=domingo … 7=sábado.
        assertEquals(7, Availability.diaSemanaDe(LocalDate.parse("2026-10-24")))
        assertEquals(1, Availability.diaSemanaDe(LocalDate.parse("2026-10-25"))) // domingo
        assertEquals(2, Availability.diaSemanaDe(LocalDate.parse("2026-10-26"))) // lunes
    }

    @Test
    fun `detecta citas en el pasado`() {
        val hoy = LocalDate.parse("2026-10-20")
        assertTrue(Availability.esPasada(LocalDate.parse("2026-10-19"), "10:00", hoy, java.time.LocalTime.NOON))
        assertTrue(Availability.esPasada(hoy, "11:00", hoy, java.time.LocalTime.NOON))
        assertFalse(Availability.esPasada(hoy, "13:00", hoy, java.time.LocalTime.NOON))
        assertFalse(Availability.esPasada(LocalDate.parse("2026-10-21"), "09:00", hoy, java.time.LocalTime.NOON))
    }

    @Test
    fun `buscarSlot exige la misma hora de inicio`() {
        val slots = Availability.slotsDelDia(horario, emptyList(), emptyList())
        assertNotNull(Availability.buscarSlot(slots, "12:30"))
        assertNull(Availability.buscarSlot(slots, "12:45"))
    }
}
