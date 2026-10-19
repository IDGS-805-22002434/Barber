package com.tovar.barbermanager.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.tovar.barbermanager.databinding.FragmentAppointmentsBinding

// Modelo de datos simple solo para prueba de diseño
data class MockAppointment(
    val id: Long,
    val serviceName: String,
    val barberName: String,
    val dateTime: String,
    val status: String
)

class AppointmentsFragment : Fragment() {

    private var _binding: FragmentAppointmentsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppointmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lista ficticia de vista previa
        val mockList = listOf(
            MockAppointment(1, "Corte de Cabello + Barba", "Carlos Mendoza", "24 Oct 2026 - 15:30 hrs", "CONFIRMADA"),
            MockAppointment(2, "Perfilado de Barba", "Alejandro Silva", "02 Nov 2026 - 11:00 hrs", "PENDIENTE")
        )

        // Configurar RecyclerView
        val adapter = MockAppointmentsAdapter(mockList) { appointment ->
            Toast.makeText(requireContext(), "Cita cancelada: ${appointment.serviceName}", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerViewAppointments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            visibility = View.VISIBLE
        }

        // Si tienes layout de estado vacío, se oculta
        // binding.emptyStateLayout?.visibility = View.GONE

        // Botón flotante para simular creación
        binding.fabAddAppointment?.setOnClickListener {
            Toast.makeText(requireContext(), "Navegar a Formulario de Nueva Cita", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}