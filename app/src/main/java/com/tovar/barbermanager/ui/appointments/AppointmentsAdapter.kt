package com.tovar.barbermanager.ui.appointments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentStatus
import com.tovar.barbermanager.databinding.ItemAppointmentBinding
import com.tovar.barbermanager.ui.util.Format

class AppointmentsAdapter(
    private val isAdmin: Boolean,
    private val onOpen: (Appointment) -> Unit,
    private val onCancel: (Appointment) -> Unit,
    private val onReschedule: (Appointment) -> Unit,
    private val onComplete: (Appointment) -> Unit
) : ListAdapter<Appointment, AppointmentsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAppointmentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Appointment) = with(binding) {
            val context = root.context
            val started = AppointmentUi.isStarted(item)

            serviceName.text = item.serviceName
            barberLine.text = if (isAdmin) {
                context.getString(R.string.appt_client_with_barber, item.clientName, item.barberName)
            } else {
                context.getString(R.string.appt_with_barber, item.barberName)
            }
            dateTime.text = context.getString(
                R.string.appt_date_time, Dates.displayShort(item.date), item.time
            )
            price.text = Format.price(item.priceCents)

            statusBadge.setText(AppointmentUi.statusLabel(item))
            statusBadge.setTextColor(context.getColor(AppointmentUi.statusColor(item)))

            val canChange = Scheduling.canChange(
                item.status, item.date, item.time, System.currentTimeMillis(), isAdmin
            )
            val upcomingConfirmed = item.status == AppointmentStatus.CONFIRMADA && !started
            actionsRow.isVisible = canChange || (isAdmin && upcomingConfirmed)
            cancelButton.isVisible = canChange
            rescheduleButton.isVisible = canChange
            completeButton.isVisible = isAdmin && item.status == AppointmentStatus.CONFIRMADA
            policyNote.isVisible = upcomingConfirmed && !canChange
            policyNote.text = context.getString(
                R.string.appt_policy_note, Scheduling.MIN_HOURS_TO_CHANGE
            )

            root.alpha = if (item.status == AppointmentStatus.CANCELADA) 0.6f else 1f
            root.setOnClickListener { onOpen(item) }
            cancelButton.setOnClickListener { onCancel(item) }
            rescheduleButton.setOnClickListener { onReschedule(item) }
            completeButton.setOnClickListener { onComplete(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Appointment>() {
            override fun areItemsTheSame(old: Appointment, new: Appointment) = old.id == new.id
            override fun areContentsTheSame(old: Appointment, new: Appointment) = old == new
        }
    }
}
