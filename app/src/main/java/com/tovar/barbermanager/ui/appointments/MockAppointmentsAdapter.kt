package com.tovar.barbermanager.ui.appointments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.databinding.ItemAppointmentBinding

class MockAppointmentsAdapter(
    private val items: List<MockAppointment>,
    private val onCancelClick: (MockAppointment) -> Unit
) : RecyclerView.Adapter<MockAppointmentsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAppointmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppointmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvServiceName.text = item.serviceName
            tvBarberName.text = "Barbero: ${item.barberName}"
            tvDateTime.text = item.dateTime
            tvStatus.text = item.status

            btnCancel.setOnClickListener { onCancelClick(item) }
        }
    }

    override fun getItemCount(): Int = items.size
}