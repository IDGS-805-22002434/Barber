package com.tovar.barbermanager.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.AppointmentStatus

/** Genera el recordatorio de una cita próxima (RF07.02) si la cita sigue vigente. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appointmentId = inputData.getLong(KEY_APPOINTMENT_ID, 0L)
        val kind = inputData.getString(KEY_KIND)
        if (appointmentId == 0L || kind == null) return Result.success()

        val appointment = AppDatabase.getInstance(applicationContext)
            .appointmentDao().getById(appointmentId)
            ?: return Result.success()
        if (appointment.status != AppointmentStatus.CONFIRMADA) return Result.success()
        if (Dates.millis(appointment.date, appointment.time) <= System.currentTimeMillis()) {
            return Result.success()
        }
        AppointmentNotifier(applicationContext).sendReminder(appointment, kind)
        return Result.success()
    }

    companion object {
        const val KEY_APPOINTMENT_ID = "appointment_id"
        const val KEY_KIND = "kind"
    }
}
