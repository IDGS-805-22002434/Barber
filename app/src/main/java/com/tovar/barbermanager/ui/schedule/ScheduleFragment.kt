package com.tovar.barbermanager.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.databinding.FragmentScheduleBinding
import com.tovar.barbermanager.databinding.ItemBlockedSlotBinding
import com.tovar.barbermanager.databinding.ItemScheduleDayBinding
import com.tovar.barbermanager.ui.util.DatePickers
import kotlinx.coroutines.launch

/** Horario semanal y bloqueos de un barbero (RF04.03). Solo administrador. */
class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScheduleViewModel by lazy {
        ViewModelProvider(this)[ScheduleViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!SessionManager(requireContext()).isAdmin) {
            findNavController().navigateUp()
            return
        }

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.addBlockButton.setOnClickListener { startBlockFlow() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { if (it.loaded) render(it) } }
                launch {
                    viewModel.invalidRange.collect {
                        Snackbar.make(binding.root, R.string.schedule_invalid_range_appointment, Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }

        viewModel.load(arguments?.getLong(ARG_BARBER_ID, 0L) ?: 0L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.scheduleHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.scheduleRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.scheduleHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun render(state: ScheduleState) {
        binding.scheduleTitleText.text = getString(R.string.schedule_for_barber, state.barberName)

        val dayNames = resources.getStringArray(R.array.schedule_days)
        binding.daysContainer.removeAllViews()
        state.week.forEachIndexed { index, hours ->
            val dayOfWeek = index + 1
            val row = ItemScheduleDayBinding.inflate(layoutInflater, binding.daysContainer, false)
            row.daySwitch.text = dayNames[index]
            row.daySwitch.isChecked = hours.active
            row.daySwitch.setOnCheckedChangeListener { _, checked ->
                viewModel.setActive(dayOfWeek, checked)
            }
            row.hoursGroup.isVisible = hours.active
            row.closedLabel.isVisible = !hours.active
            row.startButton.text = Scheduling.formatMinutes(hours.start)
            row.endButton.text = Scheduling.formatMinutes(hours.end)
            row.startButton.setOnClickListener {
                pickTime(R.string.schedule_start_picker, hours.start) {
                    viewModel.setStart(dayOfWeek, it)
                }
            }
            row.endButton.setOnClickListener {
                pickTime(R.string.schedule_end_picker, hours.end) {
                    viewModel.setEnd(dayOfWeek, it)
                }
            }
            binding.daysContainer.addView(row.root)
        }

        binding.blocksContainer.removeAllViews()
        binding.blocksEmpty.isVisible = state.blocks.isEmpty()
        state.blocks.forEach { block ->
            val row = ItemBlockedSlotBinding.inflate(layoutInflater, binding.blocksContainer, false)
            row.blockText.text = getString(
                R.string.appt_date_time, Dates.displayShort(block.date), block.time
            )
            row.unblockButton.setOnClickListener { viewModel.unblock(block.id) }
            binding.blocksContainer.addView(row.root)
        }
    }

    private fun pickTime(@StringRes title: Int, currentMinutes: Int, onPicked: (Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentMinutes / 60)
            .setMinute(currentMinutes % 60)
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(picker.hour * 60 + picker.minute) }
        picker.show(childFragmentManager, "time_picker")
    }

    /** Elegir fecha y luego una de las horas libres de ese día para bloquearla. */
    private fun startBlockFlow() {
        val picker = DatePickers.create(R.string.schedule_block_date_title, null)
        picker.addOnPositiveButtonClickListener { millis ->
            val date = Dates.isoFromUtcMillis(millis)
            viewLifecycleOwner.lifecycleScope.launch {
                val slots = viewModel.freeSlots(date)
                if (slots.isEmpty()) {
                    Snackbar.make(binding.root, R.string.schedule_block_none, Snackbar.LENGTH_SHORT)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(
                            getString(R.string.schedule_block_pick_title, Dates.displayShort(date))
                        )
                        .setItems(slots.toTypedArray()) { _, which ->
                            viewModel.block(date, slots[which])
                        }
                        .show()
                }
            }
        }
        picker.show(childFragmentManager, "block_date")
    }

    companion object {
        const val ARG_BARBER_ID = "barberId"
    }
}
