package com.tovar.barbermanager.server

import java.time.LocalDate
import java.time.LocalTime

/** Reglas de disponibilidad de una cita; lógica pura para poder probarla sin MySQL. */
object Availability {

    data class Horario(
        val diaSemana: Int,
        val inicio: String,
        val fin: String,
        val duracionMinutos: Int
    )

    /** Rango [inicio, fin) expresado en "HH:mm". */
    data class Rango(val inicio: String, val fin: String)

    data class Slot(val horaInicio: String, val horaFin: String)

    /** Convierte "10:00", "10:00:00" o variantes a minutos desde medianoche. */
    fun minutosDe(hora: String): Int {
        val parts = hora.trim().split(":")
        require(parts.size >= 2) { "Hora inválida: $hora" }
        val horas = parts[0].toInt()
        val minutos = parts[1].toInt()
        require(horas in 0..24 && minutos in 0..59 && !(horas == 24 && minutos > 0)) {
            "Hora inválida: $hora"
        }
        return horas * 60 + minutos
    }

    /** Minutos desde medianoche a "HH:mm". */
    fun horaDe(minutos: Int): String {
        val horas = minutos / 60
        val resto = minutos % 60
        return "%02d:%02d".format(horas, resto)
    }

    /** Convención de Android Calendar: 1=domingo … 7=sábado (igual que la columna dia_semana). */
    fun diaSemanaDe(fecha: LocalDate): Int = fecha.dayOfWeek.value % 7 + 1

    /**
     * Genera los turnos libres de un día:
     * [inicio, fin) con paso `duracionMinutos`, quitando bloqueos, citas ocupadas
     * y (si [minutoActual] se indica) los turnos ya pasados de hoy.
     */
    fun slotsDelDia(
        horario: Horario?,
        bloqueos: List<Rango>,
        ocupadas: List<Rango>,
        minutoActual: Int? = null
    ): List<Slot> {
        if (horario == null || horario.duracionMinutos <= 0) return emptyList()
        val inicio = minutosDe(horario.inicio)
        val fin = minutosDe(horario.fin)
        if (fin <= inicio) return emptyList()

        val noDisponibles = bloqueos + ocupadas
        val slots = mutableListOf<Slot>()
        var cursor = inicio
        while (cursor + horario.duracionMinutos <= fin) {
            val slotFin = cursor + horario.duracionMinutos
            val rango = Rango(horaDe(cursor), horaDe(slotFin))
            val libre = noDisponibles.none { seSolapan(rango, it) } &&
                (minutoActual == null || cursor >= minutoActual)
            if (libre) slots += Slot(rango.inicio, rango.fin)
            cursor = slotFin
        }
        return slots
    }

    /** El slot exacto (mismo inicio) que contiene [horaInicio], si existe entre los disponibles. */
    fun buscarSlot(slots: List<Slot>, horaInicio: String): Slot? =
        slots.firstOrNull { it.horaInicio == horaInicio }

    /** Fin de un turno; si cruza medianoche devuelve la hora del día siguiente ("23:45" + 30 = "00:15"). */
    fun finDeSlot(horaInicio: String, duracionMinutos: Int): String =
        horaDe((minutosDe(horaInicio) + duracionMinutos) % (24 * 60))

    fun seSolapan(a: Rango, b: Rango): Boolean {
        val aInicio = minutosDe(a.inicio)
        val aFin = minutosDe(a.fin)
        val bInicio = minutosDe(b.inicio)
        val bFin = minutosDe(b.fin)
        return aInicio < bFin && bInicio < aFin
    }

    /** Rango completo de una fecha (cuando el bloqueo no trae horas). */
    fun rangoDelDia(): Rango = Rango("00:00", "24:00")

    fun esPasada(fecha: LocalDate, horaInicio: String, ahora: LocalDate, ahoraHora: LocalTime): Boolean {
        val inicio = LocalTime.parse(horaInicio)
        return fecha.isBefore(ahora) || (fecha.isEqual(ahora) && inicio.isBefore(ahoraHora))
    }
}
