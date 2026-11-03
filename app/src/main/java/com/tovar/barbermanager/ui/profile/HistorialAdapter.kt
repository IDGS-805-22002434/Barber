package com.tovar.barbermanager.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.data.remote.HistorialItemDto
import com.tovar.barbermanager.databinding.ItemHistorialBinding
import com.tovar.barbermanager.ui.util.Format
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistorialAdapter : ListAdapter<HistorialItemDto, HistorialAdapter.HistorialViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val binding = ItemHistorialBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistorialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistorialViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistorialViewHolder(
        private val binding: ItemHistorialBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistorialItemDto) {
            binding.historyDate.text = formatFecha(item.fecha, item.horaInicio)
            binding.historyService.text = item.nombreServicio
            binding.historyBarber.text = item.barberoNombre
            binding.historyPrice.text = Format.price(item.precioCentavos)
        }

        private fun formatFecha(fecha: String, horaInicio: String): String {
            val fechaLarga = runCatching {
                LocalDate.parse(fecha).format(
                    DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale.forLanguageTag("es-MX"))
                )
            }.getOrNull() ?: fecha
            return "$fechaLarga · $horaInicio hrs"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<HistorialItemDto>() {
        override fun areItemsTheSame(oldItem: HistorialItemDto, newItem: HistorialItemDto): Boolean =
            oldItem.fecha == newItem.fecha && oldItem.horaInicio == newItem.horaInicio &&
                oldItem.nombreServicio == newItem.nombreServicio

        override fun areContentsTheSame(oldItem: HistorialItemDto, newItem: HistorialItemDto): Boolean =
            oldItem == newItem
    }
}
