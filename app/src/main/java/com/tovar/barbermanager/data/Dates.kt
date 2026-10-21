package com.tovar.barbermanager.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Fechas como texto ISO (yyyy-MM-dd) y horas como "HH:mm": al compararlas como
 * texto ordenan igual que el tiempo. Usa Calendar (sin java.time) porque minSdk = 24.
 */
object Dates {

    private val mx: Locale = Locale.Builder().setLanguage("es").setRegion("MX").build()

    fun iso(year: Int, month: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun isoOf(cal: Calendar): String =
        iso(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    fun today(): String = isoOf(Calendar.getInstance())

    fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** "2026-10-18 14:30": se compara como texto con "fecha hora" de una cita. */
    fun nowKey(): String = today() + " " + Scheduling.formatMinutes(nowMinutes())

    fun plusDays(iso: String, days: Int): String {
        val cal = calendarOf(iso, 0)
        cal.add(Calendar.DAY_OF_MONTH, days)
        return isoOf(cal)
    }

    /** 1 = lunes … 7 = domingo. */
    fun dayOfWeek(iso: String): Int {
        val dow = calendarOf(iso, 0).get(Calendar.DAY_OF_WEEK) // domingo = 1
        return (dow + 5) % 7 + 1
    }

    fun millis(date: String, time: String): Long =
        calendarOf(date, Scheduling.parseMinutes(time)).timeInMillis

    /** "Domingo 18 de octubre de 2026". */
    fun display(iso: String): String =
        SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", mx)
            .format(calendarOf(iso, 0).time)
            .replaceFirstChar { it.uppercase() }

    /** "Dom 18 oct". */
    fun displayShort(iso: String): String =
        SimpleDateFormat("EEE d MMM", mx)
            .format(calendarOf(iso, 0).time)
            .replace(".", "")
            .replaceFirstChar { it.uppercase() }

    /** El selector de fechas de Material trabaja con medianoche UTC. */
    fun utcMidnightMillis(iso: String): Long {
        val p = iso.split("-")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        return cal.timeInMillis
    }

    fun isoFromUtcMillis(millis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return isoOf(cal)
    }

    private fun calendarOf(iso: String, minutes: Int): Calendar {
        val p = iso.split("-")
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), minutes / 60, minutes % 60, 0)
        return cal
    }
}
