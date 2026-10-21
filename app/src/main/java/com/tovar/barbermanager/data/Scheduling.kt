package com.tovar.barbermanager.data

import com.tovar.barbermanager.data.local.AppointmentStatus
import com.tovar.barbermanager.data.local.BarberSchedule
import java.util.Locale

object Scheduling {

    const val SLOT_MINUTES = 30

    const val DEFAULT_START = 10 * 60
    const val DEFAULT_END = 19 * 60

    const val BOOKING_HORIZON_DAYS = 30

    const val MIN_HOURS_TO_CHANGE = 2

    data class DayHours(val active: Boolean, val start: Int, val end: Int)

    fun defaultHours(dayOfWeek: Int): DayHours =
        DayHours(active = dayOfWeek in 1..6, start = DEFAULT_START, end = DEFAULT_END)

    fun hoursFor(dayOfWeek: Int, rows: List<BarberSchedule>): DayHours {
        val row = rows.firstOrNull { it.dayOfWeek == dayOfWeek } ?: return defaultHours(dayOfWeek)
        return DayHours(row.active, row.startMinutes, row.endMinutes)
    }
    fun slotStarts(hours: DayHours): List<Int> {
        if (!hours.active) return emptyList()
        val result = mutableListOf<Int>()
        var t = hours.start
        while (t + SLOT_MINUTES <= hours.end) {
            result.add(t)
            t += SLOT_MINUTES
        }
        return result
    }

    fun freeSlots(
        hours: DayHours,
        occupied: Set<String>,
        blocked: Set<String>,
        afterMinutes: Int? = null
    ): List<String> =
        slotStarts(hours)
            .filter { afterMinutes == null || it > afterMinutes }
            .map { formatMinutes(it) }
            .filter { it !in occupied && it !in blocked }

    fun canChange(
        status: String,
        date: String,
        time: String,
        nowMillis: Long,
        isAdmin: Boolean
    ): Boolean {
        if (status != AppointmentStatus.CONFIRMADA) return false
        val start = Dates.millis(date, time)
        if (start <= nowMillis) return false
        return isAdmin || start - nowMillis >= MIN_HOURS_TO_CHANGE * 3_600_000L
    }

    fun formatMinutes(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    fun parseMinutes(text: String): Int {
        val parts = text.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
