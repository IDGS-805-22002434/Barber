package com.tovar.barbermanager.ui.schedules

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.BarberScheduleWithName
import com.tovar.barbermanager.databinding.ItemScheduleBinding

class SchedulesAdapter(
    private val onEdit: (BarberScheduleWithName) -> Unit,
    private val onToggleActive: (BarberScheduleWithName) -> Unit
) : ListAdapter<BarberScheduleWithName, SchedulesAdapter.ScheduleViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScheduleViewHolder(
        private val binding: ItemScheduleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BarberScheduleWithName) {
            val schedule = item.schedule
            val dayNames = binding.root.resources.getStringArray(R.array.week_days)
            val dayName = dayNames.getOrElse(schedule.dayOfWeek - 1) { "Día ${schedule.dayOfWeek}" }
            binding.scheduleBarberName.text = item.barberName
            binding.scheduleDayTime.text = binding.root.context.getString(
                R.string.schedule_day_time_format,
                dayName,
                schedule.startTime,
                schedule.endTime
            )
            binding.scheduleSlotDuration.text = binding.root.context.getString(
                R.string.schedule_duration_format,
                schedule.slotDurationMinutes
            )
            binding.scheduleStatus.setText(
                if (schedule.active) R.string.schedule_active else R.string.schedule_inactive
            )
            binding.scheduleToggleButton.setText(
                if (schedule.active) R.string.schedule_deactivate else R.string.schedule_activate
            )
            binding.scheduleEditButton.setOnClickListener { onEdit(item) }
            binding.scheduleToggleButton.setOnClickListener { onToggleActive(item) }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<BarberScheduleWithName>() {
            override fun areItemsTheSame(
                oldItem: BarberScheduleWithName,
                newItem: BarberScheduleWithName
            ): Boolean = oldItem.schedule.id == newItem.schedule.id

            override fun areContentsTheSame(
                oldItem: BarberScheduleWithName,
                newItem: BarberScheduleWithName
            ): Boolean = oldItem == newItem
        }
    }
}
