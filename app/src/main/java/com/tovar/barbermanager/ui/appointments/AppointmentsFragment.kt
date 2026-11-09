package com.tovar.barbermanager.ui.appointments

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.databinding.FragmentAppointmentsBinding
import kotlinx.coroutines.launch

import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.remote.CitaDto
import com.tovar.barbermanager.data.remote.SlotDto
import com.tovar.barbermanager.databinding.DialogRescheduleBinding
import com.tovar.barbermanager.databinding.FragmentAppointmentsBinding
import com.tovar.barbermanager.ui.booking.BookingFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Mis Citas (cliente) / Agenda (admin): lista real desde el API con cancelar, reprogramar y estados. */
class AppointmentsFragment : Fragment() {

    private var _binding: FragmentAppointmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppointmentsViewModel by lazy {
        ViewModelProvider(this)[AppointmentsViewModel::class.java]
    }

    private var adapter: AppointmentsAdapter? = null
    private var rescheduleDialog: android.app.Dialog? = null
    private var rescheduleJob: Job? = null

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

        applyInsets()

        val isAdmin = viewModel.isAdmin
        binding.adminBadge.isVisible = isAdmin
        binding.appointmentsTitleText.setText(
            if (isAdmin) R.string.appointments_title_admin else R.string.appointments_title
        )
        binding.appointmentsSubtitleText.setText(
            if (isAdmin) R.string.appointments_subtitle_admin else R.string.appointments_subtitle
        )

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_upcoming))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_past))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.selectTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        val adapter = AppointmentsAdapter(
            isAdmin = isAdmin,
            onOpen = { openReceipt(it) },
            onCancel = { confirmCancel(it) },
            onReschedule = { openReschedule(it) },
            onComplete = { viewModel.complete(it) }
        )
        binding.appointmentsList.adapter = adapter

        binding.addAppointmentFab.setOnClickListener {
            findNavController().navigate(R.id.action_appointments_to_booking)
        setupList()
        setupHeader()

        setFragmentResultListener(BookingFragment.REQUEST_CITA_CREADA) { _, _ ->
            viewModel.refresh()
            showSnackbar(getString(R.string.cita_creadada))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (binding.tabs.selectedTabPosition != state.tab) {
                            binding.tabs.getTabAt(state.tab)?.select()
                        }
                        adapter.submitList(state.items)
                        binding.emptyText.isVisible = !state.loading && state.items.isEmpty()
                        binding.emptyText.setText(
                            if (state.tab == AppointmentsViewModel.TAB_UPCOMING) {
                                R.string.appointments_empty_upcoming
                            } else {
                                R.string.appointments_empty_past
                            }
                        )
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        val message = when (event) {
                            AppointmentsViewModel.Event.Cancelled ->
                                getString(R.string.msg_cancelled)
                            AppointmentsViewModel.Event.Completed ->
                                getString(R.string.msg_completed)
                            AppointmentsViewModel.Event.NotAllowed ->
                                getString(
                                    R.string.msg_change_not_allowed,
                                    Scheduling.MIN_HOURS_TO_CHANGE
                                )
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.events.collect { showSnackbar(it) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rescheduleJob?.cancel()
        rescheduleDialog?.dismiss()
        rescheduleDialog = null
        adapter = null
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.appointmentsHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appointmentsRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appointmentsHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right)
            insets
        }
    }

    private fun openReceipt(appointment: Appointment) {
        findNavController().navigate(
            R.id.action_appointments_to_receipt,
            Bundle().apply { putLong(ReceiptFragment.ARG_APPOINTMENT_ID, appointment.id) }
        )
    }

    private fun openReschedule(appointment: Appointment) {
        findNavController().navigate(
            R.id.action_appointments_to_booking,
            Bundle().apply { putLong(BookingFragment.ARG_APPOINTMENT_ID, appointment.id) }
        )
    }

    private fun confirmCancel(appointment: Appointment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cancel_dialog_title)
            .setMessage(
                getString(
                    R.string.cancel_dialog_message,
                    appointment.serviceName,
                    Dates.displayShort(appointment.date),
                    appointment.time
                )
            )
            .setNegativeButton(R.string.cancel_dialog_keep, null)
            .setPositiveButton(R.string.cancel_dialog_confirm) { _, _ ->
                viewModel.cancel(appointment)
            }
            .show()
    }
    private fun setupHeader() {
        binding.appointmentsTitle.setText(
            if (viewModel.isAdmin) R.string.nav_all_appointments else R.string.appointments_title
        )
        binding.appointmentsSubtitle.setText(
            if (viewModel.isAdmin) R.string.appointments_subtitle_admin else R.string.appointments_subtitle
        )
        binding.refreshButton.setOnClickListener { viewModel.refresh() }
    }

    private fun setupList() {
        val listAdapter = AppointmentsAdapter(
            isAdmin = viewModel.isAdmin,
            onCancelClick = { confirmCancel(it) },
            onRescheduleClick = { showRescheduleDialog(it) },
            onStatusClick = { showStatusDialog(it) }
        )
        adapter = listAdapter
        binding.rvAppointments.adapter = listAdapter
        binding.fabAddAppointment.isVisible = !viewModel.isAdmin
        binding.fabAddAppointment.setOnClickListener {
            findNavController().navigate(R.id.bookFragment)
        }
    }

    private fun render(state: AppointmentsUiState) {
        binding.appointmentsProgress.isVisible = state.loading && state.items.isEmpty()
        adapter?.submitList(state.items)
        val vacio = !state.loading && state.items.isEmpty()
        binding.emptyState.isVisible = vacio
        binding.emptyText.setText(
            if (viewModel.isAdmin) R.string.appointments_empty_admin else R.string.appointments_empty
        )

        state.error?.let { message ->
            viewModel.consumeError()
            showSnackbar(message)
        }
    }

    private fun confirmCancel(cita: CitaDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appointment_cancel_title)
            .setMessage(
                getString(R.string.appointment_cancel_message, cita.nombreServicio, cita.horaInicio)
            )
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.appointment_cancel) { _, _ -> viewModel.cancelar(cita) }
            .show()
    }

    private fun showStatusDialog(cita: CitaDto) {
        val estados = listOf("CONFIRMADA", "COMPLETADA", "CANCELADA")
        val etiquetas = estados.map { estado ->
            when (estado) {
                "CONFIRMADA" -> getString(R.string.estado_confirmada)
                "COMPLETADA" -> getString(R.string.estado_completada)
                else -> getString(R.string.estado_cancelada)
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appointment_status_title)
            .setItems(etiquetas.toTypedArray()) { _, which ->
                viewModel.cambiarEstado(cita, estados[which])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showRescheduleDialog(cita: CitaDto) {
        val view = DialogRescheduleBinding.inflate(layoutInflater, null, false)
        var fecha: String? = null
        var slot: SlotDto? = null
        // Evita mostrar los turnos del diálogo anterior mientras llega la nueva consulta.
        viewModel.limpiarSlots()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reschedule_title)
            .setView(view.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.appointment_reschedule, null)
            .create()

        fun actualizarBoton() {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled =
                fecha != null && slot != null
        }

        fun actualizarVacio() {
            view.rescheduleEmpty.isVisible = fecha != null &&
                !viewModel.slotsLoading.value &&
                viewModel.slots.value.isEmpty()
        }

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val f = fecha
                val s = slot
                if (f != null && s != null) {
                    viewModel.reprogramar(cita, f, s.horaInicio)
                    dialog.dismiss()
                }
            }
        }
        dialog.setOnDismissListener {
            rescheduleJob?.cancel()
            rescheduleJob = null
            rescheduleDialog = null
        }
        rescheduleDialog = dialog

        view.rescheduleDateButton.setOnClickListener {
            val today = LocalDate.now()
            val builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.booking_pick_date))
                .setCalendarConstraints(
                    CalendarConstraints.Builder()
                        .setValidator(
                            DateValidatorPointForward.from(MaterialDatePicker.todayInUtcMilliseconds())
                        )
                        .build()
                )
            fecha?.let { f ->
                runCatching { LocalDate.parse(f) }.getOrNull()
                    ?.takeIf { !it.isBefore(today) }
                    ?.let { local -> builder.setSelection(local.toEpochDay() * 86_400_000L) }
            }
            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { millis ->
                val seleccionada = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                fecha = seleccionada.toString()
                view.rescheduleDateButton.text = seleccionada.toString()
                slot = null
                view.rescheduleSlotsGroup.removeAllViews()
                actualizarBoton()
                viewModel.cargarSlotsParaReprogramar(cita.barberoId, seleccionada.toString())
                actualizarVacio()
            }
            picker.show(childFragmentManager, TAG_RESCHEDULE_DATE)
        }

        rescheduleJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.slotsLoading.collect { cargando ->
                        if (rescheduleDialog === dialog) {
                            view.rescheduleProgress.isVisible = cargando
                            actualizarVacio()
                        }
                    }
                }
                launch {
                    viewModel.slots.collect { slots ->
                        if (rescheduleDialog !== dialog) return@collect
                        view.rescheduleSlotsGroup.removeAllViews()
                        slots.forEach { s -> view.rescheduleSlotsGroup.addView(createSlotChip(s) { elegido ->
                            slot = elegido
                            actualizarBoton()
                        }) }
                        actualizarVacio()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun createSlotChip(slot: SlotDto, onSelect: (SlotDto) -> Unit): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            text = "${slot.horaInicio} – ${slot.horaFin}"
            isCheckable = true
            setOnClickListener { onSelect(slot) }
        }

    private fun showSnackbar(message: String) {
        val root = _binding?.root ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(_binding?.fabAddAppointment)
            .show()
    }

    private companion object {
        const val TAG_RESCHEDULE_DATE = "reschedule_date_picker"
    }
}
