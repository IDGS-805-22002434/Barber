package com.tovar.barbermanager.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.databinding.FragmentBookingBinding
import com.tovar.barbermanager.ui.util.DatePickers
import com.tovar.barbermanager.ui.util.Format
import kotlinx.coroutines.launch

class BookingFragment : Fragment() {

    private var _binding: FragmentBookingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BookingViewModel by lazy {
        ViewModelProvider(this)[BookingViewModel::class.java]
    }

    private var appointmentId: Long = BookingViewModel.NEW
    private var bound = false

    private val rescheduling get() = appointmentId != BookingViewModel.NEW

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

        appointmentId = arguments?.getLong(ARG_APPOINTMENT_ID, BookingViewModel.NEW)
            ?: BookingViewModel.NEW

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.bookingTitleText.setText(
            if (rescheduling) R.string.reschedule_title else R.string.booking_title
        )
        binding.confirmButton.setText(
            if (rescheduling) R.string.reschedule_confirm else R.string.booking_confirm
        )
        binding.newFields.isVisible = !rescheduling
        binding.rescheduleCard.isVisible = rescheduling

        binding.dateInput.setOnClickListener { showDatePicker() }
        binding.dateLayout.setEndIconOnClickListener { showDatePicker() }
        binding.confirmButton.setOnClickListener { attemptConfirm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.events.collect { onEvent(it) } }
            }
        }

        viewModel.load(appointmentId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bound = false
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.bookingHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.bookingRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.bookingHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    private fun render(state: BookingState) {
        if (!state.loaded) return

        if (state.notFound) {
            Snackbar.make(binding.root, R.string.receipt_not_found, Snackbar.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        if (!bound) {
            bound = true
            if (!rescheduling) setupDropdowns(state)
        }

        val unavailable = !rescheduling && (state.services.isEmpty() || state.barbers.isEmpty())
        binding.unavailableText.isVisible = unavailable
        binding.confirmButton.isEnabled = !unavailable

        state.editing?.let { appointment ->
            binding.rescheduleSummary.text = getString(
                R.string.booking_reschedule_header,
                appointment.serviceName,
                appointment.barberName,
                getString(
                    R.string.reschedule_current,
                    Dates.displayShort(appointment.date),
                    appointment.time
                )
            )
        }

        if (!rescheduling) {
            val serviceLabel = state.service?.let { serviceLabel(it) }.orEmpty()
            if (binding.serviceInput.text.toString() != serviceLabel) {
                binding.serviceInput.setText(serviceLabel, false)
            }
            val barberLabel = state.barber?.name.orEmpty()
            if (binding.barberInput.text.toString() != barberLabel) {
                binding.barberInput.setText(barberLabel, false)
            }
        }

        val dateLabel = state.date?.let { Dates.display(it) }.orEmpty()
        if (binding.dateInput.text.toString() != dateLabel) {
            binding.dateInput.setText(dateLabel)
        }

        renderSlots(state)
        renderSummary(state)
    }

    private fun setupDropdowns(state: BookingState) {
        binding.serviceInput.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                state.services.map { serviceLabel(it) }
            )
        )
        binding.serviceInput.setOnItemClickListener { _, _, position, _ ->
            binding.serviceLayout.error = null
            viewModel.selectService(state.services[position])
        }

        binding.barberInput.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                state.barbers.map { it.name }
            )
        )
        binding.barberInput.setOnItemClickListener { _, _, position, _ ->
            binding.barberLayout.error = null
            viewModel.selectBarber(state.barbers[position])
        }

        if (binding.nameInput.text.isNullOrEmpty()) {
            binding.nameInput.setText(state.defaultName)
        }
    }

    private fun serviceLabel(service: com.tovar.barbermanager.data.local.Service): String =
        "${service.name} · ${Format.price(service.priceCents)}"

    private fun renderSlots(state: BookingState) {
        binding.slotsGroup.removeAllViews()
        val hint = when {
            state.barberId == null || state.date == null -> R.string.booking_slots_choose
            state.slotsLoading -> R.string.booking_slots_loading
            state.slots.isEmpty() -> R.string.booking_slots_none
            else -> null
        }
        binding.slotsHint.isVisible = hint != null
        if (hint != null) binding.slotsHint.setText(hint)

        state.slots.forEach { slot ->
            val chip = Chip(requireContext()).apply {
                text = slot
                isCheckable = true
                isChecked = slot == state.time
                setOnClickListener {
                    isChecked = true
                    binding.slotsError.isVisible = false
                    viewModel.selectTime(slot)
                }
            }
            binding.slotsGroup.addView(chip)
        }
    }

    private fun renderSummary(state: BookingState) {
        val editing = state.editing
        val serviceName = editing?.serviceName ?: state.service?.name
        val priceCents = editing?.priceCents ?: state.service?.priceCents
        val barberName = editing?.barberName ?: state.barber?.name
        val date = state.date
        val time = state.time

        val ready = serviceName != null && priceCents != null && barberName != null &&
            date != null && time != null
        binding.summaryCard.isVisible = ready
        if (ready) {
            binding.summaryText.text = getString(
                R.string.booking_summary,
                serviceName,
                Format.price(priceCents!!),
                barberName,
                Dates.display(date!!),
                time
            )
        }
    }

    private fun attemptConfirm() {
        val state = viewModel.state.value
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()

        var valid = true
        if (!rescheduling) {
            binding.serviceLayout.error =
                if (state.service == null) getString(R.string.error_booking_service) else null
            binding.barberLayout.error =
                if (state.barber == null) getString(R.string.error_booking_barber) else null
            binding.nameLayout.error =
                if (name.isEmpty()) getString(R.string.error_booking_name) else null
            valid = state.service != null && state.barber != null && name.isNotEmpty()
        }
        binding.dateLayout.error =
            if (state.date == null) getString(R.string.error_booking_date) else null
        binding.slotsError.isVisible = state.date != null && state.time == null
        valid = valid && state.date != null && state.time != null

        if (valid) viewModel.confirm(name, phone)
    }

    private fun onEvent(event: BookingViewModel.Event) {
        when (event) {
            is BookingViewModel.Event.Saved -> findNavController().navigate(
                R.id.action_booking_to_receipt,
                Bundle().apply { putLong(ReceiptFragment.ARG_APPOINTMENT_ID, event.appointmentId) }
            )
            BookingViewModel.Event.SlotTaken ->
                Snackbar.make(binding.root, R.string.msg_slot_taken, Snackbar.LENGTH_LONG).show()
            BookingViewModel.Event.NotAllowed -> {
                Snackbar.make(
                    binding.root,
                    getString(R.string.msg_change_not_allowed, Scheduling.MIN_HOURS_TO_CHANGE),
                    Snackbar.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun showDatePicker() {
        val picker = DatePickers.create(
            R.string.booking_date_picker_title,
            viewModel.state.value.date
        )
        picker.addOnPositiveButtonClickListener { millis ->
            binding.dateLayout.error = null
            viewModel.selectDate(Dates.isoFromUtcMillis(millis))
        }
        picker.show(childFragmentManager, "date_picker")
    }

    companion object {
        const val ARG_APPOINTMENT_ID = "appointmentId"
    }
}
