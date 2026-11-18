package com.tovar.barbermanager.data.notifications

import android.content.Context
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.AppointmentEvents
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.NotificationType

/**
 * Genera las notificaciones de cita (RF07, RF07.01, RF07.02) y programa o cancela
 * los recordatorios según cambie la cita.
 */
class AppointmentNotifier(context: Context) : AppointmentEvents {

    private val appContext = context.applicationContext
    private val center = NotificationCenter(appContext)

    override suspend fun onBooked(appointment: Appointment) {
        center.notify(
            appointment.userEmail,
            appointment.id,
            NotificationType.CONFIRMACION,
            appContext.getString(R.string.notif_confirmed_title),
            appContext.getString(
                R.string.notif_confirmed_msg,
                appointment.serviceName,
                appointment.barberName,
                Dates.displayShort(appointment.date),
                appointment.time
            )
        )
        ReminderScheduler.schedule(appContext, appointment)
    }

    override suspend fun onRescheduled(appointment: Appointment) {
        center.notify(
            appointment.userEmail,
            appointment.id,
            NotificationType.REPROGRAMACION,
            appContext.getString(R.string.notif_rescheduled_title),
            appContext.getString(
                R.string.notif_rescheduled_msg,
                appointment.serviceName,
                appointment.barberName,
                Dates.displayShort(appointment.date),
                appointment.time
            )
        )
        ReminderScheduler.schedule(appContext, appointment)
    }

    override suspend fun onCancelled(appointment: Appointment) {
        ReminderScheduler.cancel(appContext, appointment.id)
        center.notify(
            appointment.userEmail,
            appointment.id,
            NotificationType.CANCELACION,
            appContext.getString(R.string.notif_cancelled_title),
            appContext.getString(
                R.string.notif_cancelled_msg,
                appointment.serviceName,
                Dates.displayShort(appointment.date),
                appointment.time
            )
        )
    }

    override suspend fun onCompleted(appointment: Appointment) {
        ReminderScheduler.cancel(appContext, appointment.id)
        center.notify(
            appointment.userEmail,
            appointment.id,
            NotificationType.COMPLETADA,
            appContext.getString(R.string.notif_completed_title),
            appContext.getString(R.string.notif_completed_msg, appointment.serviceName)
        )
    }

    /** Recordatorio de cita próxima; lo llama [ReminderWorker]. */
    suspend fun sendReminder(appointment: Appointment, kind: String) {
        val title = appContext.getString(
            if (kind == ReminderPolicy.KIND_DAY_BEFORE) R.string.notif_reminder_day_title
            else R.string.notif_reminder_hour_title
        )
        center.notify(
            appointment.userEmail,
            appointment.id,
            NotificationType.RECORDATORIO,
            title,
            appContext.getString(
                R.string.notif_reminder_msg,
                appointment.serviceName,
                appointment.barberName,
                Dates.displayShort(appointment.date),
                appointment.time
            )
        )
    }
}
