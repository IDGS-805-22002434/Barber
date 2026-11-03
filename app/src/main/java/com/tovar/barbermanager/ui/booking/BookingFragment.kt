package com.tovar.barbermanager.ui.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.remote.SlotDto
import com.tovar.barbermanager.databinding.FragmentBookingBinding
import com.tovar.barbermanager.ui.util.Format
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Agendar Cita: servicio → barbero → fecha → turno libre (RF01). */
class BookingFragment : Fragment() {

    private var _binding: FragmentBookingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BookingViewModel by lazy {
        ViewModelProvider(this)[BookingViewModel::class.java]
    }

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.forLanguageTag("es-MX"))
    private var serviceOptions: List<String> = emptyList()
    private var barberOptions: List<String> = emptyList()
    private var renderedSlots: List<SlotDto> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        setupInputs()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        serviceOptions = emptyList()
        barberOptions = emptyList()
        renderedSlots = emptyList()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.bookingHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.bookingRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.bookingHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = ime.bottom)
            insets
        }
    }

    private fun setupInputs() {
        binding.serviceDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.uiState.value.servicios.getOrNull(position)
                ?.let { viewModel.onServicioSelected(it) }
        }
        binding.barberDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.uiState.value.barberos.getOrNull(position)
                ?.let { viewModel.onBarberoSelected(it) }
        }
        binding.dateButton.setOnClickListener { showDatePicker() }
        binding.notesInput.doAfterTextChanged { text ->
            viewModel.onNotasChanged(text?.toString().orEmpty())
        }
        binding.confirmButton.setOnClickListener { viewModel.confirm() }
        binding.errorText.setOnClickListener { viewModel.retry() }
    }

    private fun render(state: BookingUiState) {
        binding.catalogProgress.isVisible = state.loading
        binding.confirmButton.isEnabled = state.canConfirm
        binding.confirmButton.text =
            getString(if (state.submitting) R.string.booking_saving else R.string.booking_confirm)

        bindDropdowns(state)
        bindDate(state)
        bindSlots(state)

        if (binding.notesInput.text?.toString() != state.notas) {
            binding.notesInput.setText(state.notas)
        }

        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error

        state.created?.let { cita ->
            viewModel.consumeCreated()
            setFragmentResult(
                REQUEST_CITA_CREADA,
                Bundle().apply { putLong(ARG_CITA_ID, cita.id) }
            )
            findNavController().navigateUp()
        }
    }

    private fun bindDropdowns(state: BookingUiState) {
        val serviceNames = state.servicios.map { "${it.nombre} · ${Format.price(it.precioCentavos)}" }
        if (serviceNames != serviceOptions) {
            serviceOptions = serviceNames
            binding.serviceDropdown.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, serviceNames)
            )
        }
        val selectedService = state.servicio
            ?.let { servicio -> state.servicios.indexOfFirst { it.id == servicio.id } }
            ?.takeIf { it >= 0 }
            ?.let { serviceNames[it] }
        if (binding.serviceDropdown.text?.toString() != selectedService.orEmpty()) {
            binding.serviceDropdown.setText(selectedService.orEmpty(), false)
        }

        val barberNames = state.barberos.map { it.nombre }
        if (barberNames != barberOptions) {
            barberOptions = barberNames
            binding.barberDropdown.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, barberNames)
            )
        }
        val selectedBarber = state.barbero?.nombre.orEmpty()
        if (binding.barberDropdown.text?.toString() != selectedBarber) {
            binding.barberDropdown.setText(selectedBarber, false)
        }
    }

    private fun bindDate(state: BookingUiState) {
        binding.dateButton.text = state.fecha
            ?.let { runCatching { LocalDate.parse(it).format(dateFormatter) }.getOrNull() ?: it }
            ?: getString(R.string.booking_pick_date)
    }

    private fun bindSlots(state: BookingUiState) {
        binding.slotsProgress.isVisible = state.slotsLoading
        val listo = !state.loading && state.fecha != null && state.barbero != null
        binding.slotsEmpty.isVisible = !state.loading && !state.slotsLoading && state.slots.isEmpty()
        binding.slotsEmpty.setText(
            if (listo) R.string.booking_slots_empty else R.string.booking_slots_hint
        )

        if (renderedSlots != state.slots) {
            renderedSlots = state.slots
            binding.slotsGroup.removeAllViews()
            state.slots.forEach { slot -> binding.slotsGroup.addView(createChip(slot)) }
        }
        state.slots.forEachIndexed { index, slot ->
            val chip = binding.slotsGroup.getChildAt(index) as? Chip ?: return@forEachIndexed
            if (chip.isChecked != (slot == state.slot)) chip.isChecked = slot == state.slot
        }
    }

    private fun createChip(slot: SlotDto): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            text = "${slot.horaInicio} – ${slot.horaFin}"
            isCheckable = true
            setOnClickListener { viewModel.onSlotSelected(slot) }
        }

    private fun showDatePicker() {
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
        viewModel.uiState.value.fecha?.let { fecha ->
            runCatching { LocalDate.parse(fecha) }.getOrNull()
                ?.takeIf { !it.isBefore(today) }
                ?.let { local -> builder.setSelection(local.toEpochDay() * 86_400_000L) }
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { millis ->
            val fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            viewModel.onFechaSelected(fecha.toString())
        }
        picker.show(childFragmentManager, TAG_DATE_PICKER)
    }

    companion object {
        const val REQUEST_CITA_CREADA = "cita_creada"
        const val ARG_CITA_ID = "cita_id"
        private const val TAG_DATE_PICKER = "booking_date_picker"
    }
}
