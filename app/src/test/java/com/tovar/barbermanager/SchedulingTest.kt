package com.tovar.barbermanager

import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.data.local.AppointmentStatus
import com.tovar.barbermanager.data.local.BarberSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de horarios (RF04) y política de cambios (RF06.04). */
class SchedulingTest {

    private val monday = Scheduling.defaultHours(1)

    @Test
    fun `horario por defecto es lunes a sabado de 10 a 19 en citas de 30 minutos`() {
        val slots = Scheduling.slotStarts(monday)
        assertEquals(18, slots.size)
        assertEquals("10:00", Scheduling.formatMinutes(slots.first()))
        assertEquals("18:30", Scheduling.formatMinutes(slots.last()))
        assertTrue(Scheduling.defaultHours(6).active)
        assertFalse(Scheduling.defaultHours(7).active)
        assertTrue(Scheduling.slotStarts(Scheduling.defaultHours(7)).isEmpty())
    }

    @Test
    fun `la ultima cita debe terminar antes del cierre`() {
        val hours = Scheduling.DayHours(active = true, start = 10 * 60, end = 10 * 60 + 45)
        assertEquals(listOf("10:00"), Scheduling.freeSlots(hours, emptySet(), emptySet()))
    }

    @Test
    fun `no se ofrecen horarios ocupados ni bloqueados`() {
        val free = Scheduling.freeSlots(
            monday,
            occupied = setOf("10:00", "11:30"),
            blocked = setOf("10:30")
        )
        assertFalse("10:00" in free)
        assertFalse("10:30" in free)
        assertFalse("11:30" in free)
        assertTrue("11:00" in free)
        assertEquals(15, free.size)
    }

    @Test
    fun `hoy solo se ofrecen horarios posteriores a la hora actual`() {
        val free = Scheduling.freeSlots(
            monday, emptySet(), emptySet(), afterMinutes = 12 * 60
        )
        assertEquals("12:30", free.first())
    }

    @Test
    fun `el horario guardado del barbero reemplaza al de por defecto`() {
        val rows = listOf(
            BarberSchedule(barberId = 1, dayOfWeek = 1, startMinutes = 9 * 60, endMinutes = 13 * 60),
            BarberSchedule(barberId = 1, dayOfWeek = 2, startMinutes = 10 * 60, endMinutes = 19 * 60, active = false)
        )
        assertEquals(8, Scheduling.slotStarts(Scheduling.hoursFor(1, rows)).size)
        assertTrue(Scheduling.slotStarts(Scheduling.hoursFor(2, rows)).isEmpty())
        // Miércoles no tiene renglón: usa el horario por defecto.
        assertEquals(18, Scheduling.slotStarts(Scheduling.hoursFor(3, rows)).size)
    }

    @Test
    fun `dia de la semana y suma de dias`() {
        assertEquals(1, Dates.dayOfWeek("2026-10-19")) // lunes
        assertEquals(6, Dates.dayOfWeek("2026-10-24")) // sábado
        assertEquals(7, Dates.dayOfWeek("2026-10-18")) // domingo
        assertEquals("2026-11-01", Dates.plusDays("2026-10-31", 1))
    }

    @Test
    fun `politica de cambios exige 2 horas de anticipacion al cliente`() {
        val date = "2026-10-20"
        val time = "10:00"
        val start = Dates.millis(date, time)
        val hour = 3_600_000L

        assertTrue(Scheduling.canChange(AppointmentStatus.CONFIRMADA, date, time, start - 3 * hour, false))
        assertTrue(Scheduling.canChange(AppointmentStatus.CONFIRMADA, date, time, start - 2 * hour, false))
        assertFalse(Scheduling.canChange(AppointmentStatus.CONFIRMADA, date, time, start - hour, false))
        // El administrador puede cambiar hasta que la cita empieza.
        assertTrue(Scheduling.canChange(AppointmentStatus.CONFIRMADA, date, time, start - hour, true))
        assertFalse(Scheduling.canChange(AppointmentStatus.CONFIRMADA, date, time, start + hour, true))
    }

    @Test
    fun `solo las citas confirmadas se pueden cambiar`() {
        val date = "2026-10-20"
        val start = Dates.millis(date, "10:00")
        val now = start - 24 * 3_600_000L
        assertFalse(Scheduling.canChange(AppointmentStatus.CANCELADA, date, "10:00", now, true))
        assertFalse(Scheduling.canChange(AppointmentStatus.COMPLETADA, date, "10:00", now, true))
    }
}
