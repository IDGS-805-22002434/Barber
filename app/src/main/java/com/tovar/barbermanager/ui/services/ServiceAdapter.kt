package com.tovar.barbermanager.ui.services

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.Service
import com.tovar.barbermanager.databinding.ItemServiceBinding
import com.tovar.barbermanager.ui.util.Format

class ServiceAdapter(
    private val onClick: (Service) -> Unit
) : ListAdapter<Service, ServiceAdapter.ServiceViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServiceViewHolder(
        private val binding: ItemServiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) = with(binding) {
            serviceName.text = service.name
            serviceDescription.text = service.description
            servicePrice.text = Format.price(service.priceCents)
            serviceCategory.setText(service.category.titleRes)
            inactiveBadge.isVisible = !service.active
            root.alpha = if (service.active) 1f else 0.6f
            root.setOnClickListener { onClick(service) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Service>() {
            override fun areItemsTheSame(oldItem: Service, newItem: Service) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Service, newItem: Service) = oldItem == newItem
        }
    }
}
