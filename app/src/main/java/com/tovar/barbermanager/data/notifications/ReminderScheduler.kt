package com.tovar.barbermanager.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Appointment
import java.util.concurrent.TimeUnit

/** Programa y cancela los recordatorios de las citas con WorkManager (sobreviven reinicios). */
object ReminderScheduler {

    fun schedule(context: Context, appointment: Appointment) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val start = Dates.millis(appointment.date, appointment.time)
        // Se reemplaza cualquier recordatorio anterior (por ejemplo, al reprogramar).
        cancel(context, appointment.id)
        ReminderPolicy.reminders(start, System.currentTimeMillis()).forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(reminder.delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        ReminderWorker.KEY_APPOINTMENT_ID to appointment.id,
                        ReminderWorker.KEY_KIND to reminder.kind
                    )
                )
                .build()
            manager.enqueueUniqueWork(
                uniqueName(appointment.id, reminder.kind),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancel(context: Context, appointmentId: Long) {
        val manager = WorkManager.getInstance(context.applicationContext)
        ReminderPolicy.KINDS.forEach { manager.cancelUniqueWork(uniqueName(appointmentId, it)) }
    }

    /** Reprograma los recordatorios de las próximas citas de un usuario al abrir la app. */
    suspend fun syncFor(context: Context, email: String) {
        val db = AppDatabase.getInstance(context.applicationContext)
        db.appointmentDao().upcomingForUser(email, Dates.nowKey()).forEach { schedule(context, it) }
    }

    private fun uniqueName(appointmentId: Long, kind: String) = "recordatorio-$appointmentId-$kind"
}
