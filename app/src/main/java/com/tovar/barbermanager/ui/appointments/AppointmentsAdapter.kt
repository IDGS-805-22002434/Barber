package com.tovar.barbermanager.ui.appointments

import android.graphics.Color
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
import com.tovar.barbermanager.data.remote.CitaDto
import com.tovar.barbermanager.databinding.ItemAppointmentBinding
import com.tovar.barbermanager.ui.util.Format
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AppointmentsAdapter(
    private val isAdmin: Boolean,
    private val onCancelClick: (CitaDto) -> Unit,
    private val onRescheduleClick: (CitaDto) -> Unit,
    private val onStatusClick: (CitaDto) -> Unit
) : ListAdapter<CitaDto, AppointmentsAdapter.AppointmentViewHolder>(DiffCallback) {

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
        fun bind(cita: CitaDto) {
            val context = binding.root.context
            binding.apply {
                tvServiceName.text = cita.nombreServicio
                tvBarberName.text = context.getString(R.string.appointment_barber_format, cita.barberoNombre)
                tvDateTime.text = formatFecha(cita.fecha, cita.horaInicio)
                tvPrice.text = Format.price(cita.precioCentavos)
                tvStatus.text = estadoLabel(context, cita.estado)
                aplicarColorEstado(tvStatus.text.toString())

                val activa = cita.estado !in ESTADOS_FINALES
                btnCancel.isVisible = activa
                btnReschedule.isVisible = activa
                btnStatus.isVisible = isAdmin

                btnCancel.setOnClickListener { onCancelClick(cita) }
                btnReschedule.setOnClickListener { onRescheduleClick(cita) }
                btnStatus.setOnClickListener { onStatusClick(cita) }
            }
            dateTime.text = context.getString(
                R.string.appt_date_time, Dates.displayShort(item.date), item.time
            )
            price.text = Format.price(item.priceCents - item.discountCents)

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

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Appointment>() {
            override fun areItemsTheSame(old: Appointment, new: Appointment) = old.id == new.id
            override fun areContentsTheSame(old: Appointment, new: Appointment) = old == new
        }
    }
        private fun aplicarColorEstado(label: String) {
            val (fondo, texto) = when (label) {
                contextString(R.string.estado_pendiente) -> "#FFF8E1" to "#F57F17"
                contextString(R.string.estado_confirmada) -> "#E8F5E9" to "#1B5E20"
                contextString(R.string.estado_completada) -> "#E3F2FD" to "#0D47A1"
                contextString(R.string.estado_reprogramada) -> "#EDE7F6" to "#4527A0"
                else -> "#FFEBEE" to "#C62828"
            }
            binding.tvStatus.setBackgroundColor(Color.parseColor(fondo))
            binding.tvStatus.setTextColor(Color.parseColor(texto))
        }

        private fun contextString(res: Int): String = binding.root.context.getString(res)
    }

    private fun formatFecha(fecha: String, horaInicio: String): String {
        val fechaLarga = runCatching {
            LocalDate.parse(fecha).format(
                DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale.forLanguageTag("es-MX"))
            )
        }.getOrNull() ?: fecha
        return "$fechaLarga · $horaInicio hrs"
    }

    private fun estadoLabel(context: android.content.Context, estado: String): String =
        when (estado) {
            "PENDIENTE" -> context.getString(R.string.estado_pendiente)
            "CONFIRMADA" -> context.getString(R.string.estado_confirmada)
            "COMPLETADA" -> context.getString(R.string.estado_completada)
            "REPROGRAMADA" -> context.getString(R.string.estado_reprogramada)
            else -> context.getString(R.string.estado_cancelada)
        }

    companion object DiffCallback : DiffUtil.ItemCallback<CitaDto>() {
        private val ESTADOS_FINALES = setOf("COMPLETADA", "CANCELADA")

        override fun areItemsTheSame(oldItem: CitaDto, newItem: CitaDto): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CitaDto, newItem: CitaDto): Boolean =
            oldItem == newItem
    }
}
