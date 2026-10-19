package com.tovar.barbermanager.ui.specialties

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.Specialty
import com.tovar.barbermanager.databinding.ItemSpecialtyBinding

class SpecialtyAdapter(
    private val onClick: (Specialty) -> Unit = {}
) : ListAdapter<Specialty, SpecialtyAdapter.SpecialtyViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpecialtyViewHolder {
        val binding =
            ItemSpecialtyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpecialtyViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: SpecialtyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SpecialtyViewHolder(
        private val binding: ItemSpecialtyBinding,
        private val onClick: (Specialty) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(specialty: Specialty) = with(binding) {
            specialtyName.text = specialty.name
            inactiveBadge.isVisible = !specialty.active
            root.alpha = if (specialty.active) 1f else 0.6f
            root.setOnClickListener { onClick(specialty) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Specialty>() {
            override fun areItemsTheSame(oldItem: Specialty, newItem: Specialty) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Specialty, newItem: Specialty) =
                oldItem == newItem
        }
    }
}
