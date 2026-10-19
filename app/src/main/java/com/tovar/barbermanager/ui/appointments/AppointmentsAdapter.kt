package com.tovar.barbermanager.ui.appointments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.databinding.ItemAppointmentBinding

class AppointmentsAdapter(
    private val onCancelClick: (Appointment) -> Unit,
    private val onCompleteClick: (Appointment) -> Unit
) : ListAdapter<Appointment, AppointmentsAdapter.AppointmentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppointmentViewHolder(
        private val binding: ItemAppointmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appointment: Appointment) {
            binding.apply {
                // Asignamos según los IDs reales de item_appointment.xml
                tvServiceName.text = appointment.serviceName
                tvBarberName.text = "Barbero: ${appointment.barberName}"
                tvDateTime.text = "${appointment.date} - ${appointment.time}"
                tvStatus.text = appointment.status

                btnCancel.setOnClickListener { onCancelClick(appointment) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Appointment>() {
        override fun areItemsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
            return oldItem == newItem
        }
    }
}