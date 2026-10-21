package com.tovar.barbermanager.ui.appointments

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentStatus
import java.util.Locale

object AppointmentUi {

    fun isStarted(appointment: Appointment): Boolean =
        "${appointment.date} ${appointment.time}" < Dates.nowKey()

    @StringRes
    fun statusLabel(appointment: Appointment): Int = when {
        appointment.status == AppointmentStatus.CANCELADA -> R.string.status_cancelled
        appointment.status == AppointmentStatus.COMPLETADA -> R.string.status_completed
        isStarted(appointment) -> R.string.status_past
        else -> R.string.status_confirmed
    }

    @ColorRes
    fun statusColor(appointment: Appointment): Int = when {
        appointment.status == AppointmentStatus.CANCELADA -> R.color.pole_red
        appointment.status == AppointmentStatus.COMPLETADA -> R.color.text_secondary
        isStarted(appointment) -> R.color.text_secondary
        else -> R.color.brand_primary
    }

    /** "BM-00042" */
    fun folio(id: Long): String = String.format(Locale.US, "BM-%05d", id)
}
