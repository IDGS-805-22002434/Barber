package com.tovar.barbermanager.data.notifications

/** Cuándo se envían los recordatorios de una cita (RF07.02). Lógica pura, fácil de probar. */
object ReminderPolicy {

    const val KIND_DAY_BEFORE = "24H"
    const val KIND_HOUR_BEFORE = "1H"
    val KINDS = listOf(KIND_DAY_BEFORE, KIND_HOUR_BEFORE)

    private const val HOUR_MILLIS = 3_600_000L

    data class Reminder(val kind: String, val delayMillis: Long)

    /** Recordatorios que todavía están por venir: 24 h y 1 h antes del inicio de la cita. */
    fun reminders(startMillis: Long, nowMillis: Long): List<Reminder> {
        val result = mutableListOf<Reminder>()
        val dayDelay = startMillis - 24 * HOUR_MILLIS - nowMillis
        if (dayDelay > 0) result.add(Reminder(KIND_DAY_BEFORE, dayDelay))
        val hourDelay = startMillis - HOUR_MILLIS - nowMillis
        if (hourDelay > 0) result.add(Reminder(KIND_HOUR_BEFORE, hourDelay))
        return result
    }
}
