package com.tovar.barbermanager.ui.barbers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.BarberWithSpecialties
import com.tovar.barbermanager.databinding.ItemBarberBinding

class BarberAdapter(
    private val isAdmin: Boolean = false,
    private val onClick: (BarberWithSpecialties) -> Unit = {}
) : ListAdapter<BarberWithSpecialties, BarberAdapter.BarberViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarberViewHolder {
        val binding = ItemBarberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BarberViewHolder(binding, isAdmin, onClick)
    }

    override fun onBindViewHolder(holder: BarberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BarberViewHolder(
        private val binding: ItemBarberBinding,
        private val isAdmin: Boolean,
        private val onClick: (BarberWithSpecialties) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BarberWithSpecialties) = with(binding) {
            val barber = item.barber
            barberName.text = barber.name
            val specialtyNames = item.specialties
                .filter { it.active }
                .joinToString(" · ") { it.name }
            barberSpecialty.isVisible = specialtyNames.isNotEmpty()
            barberSpecialty.text = specialtyNames
            barberYears.text = root.resources.getQuantityString(
                R.plurals.barber_years,
                barber.yearsExperience,
                barber.yearsExperience
            )
            inactiveBadge.isVisible = isAdmin && !barber.active
            root.alpha = if (barber.active) 1f else 0.6f
            if (isAdmin) {
                root.setOnClickListener { onClick(item) }
            } else {
                root.setOnClickListener(null)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<BarberWithSpecialties>() {
            override fun areItemsTheSame(
                oldItem: BarberWithSpecialties,
                newItem: BarberWithSpecialties
            ) = oldItem.barber.id == newItem.barber.id

            override fun areContentsTheSame(
                oldItem: BarberWithSpecialties,
                newItem: BarberWithSpecialties
            ) = oldItem == newItem
        }
    }
}
