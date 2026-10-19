package com.tovar.barbermanager.ui.schedules

import android.app.TimePickerDialog
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Barber
import com.tovar.barbermanager.data.local.BarberSchedule
import com.tovar.barbermanager.data.local.BarberScheduleWithName
import com.tovar.barbermanager.databinding.DialogScheduleBinding
import com.tovar.barbermanager.databinding.FragmentSchedulesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/** Administración de horarios semanales de cada barbero (RF04.03). */
class SchedulesFragment : Fragment() {

    private var _binding: FragmentSchedulesBinding? = null
    private val binding get() = _binding!!
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val scheduleDao by lazy { database.barberScheduleDao() }
    private lateinit var adapter: SchedulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSchedulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!SessionManager(requireContext()).isAdmin) {
            Snackbar.make(binding.root, R.string.admin_only, Snackbar.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        adapter = SchedulesAdapter(
            onEdit = { showScheduleEditor(it) },
            onToggleActive = { toggleSchedule(it) }
        )
        binding.schedulesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.schedulesRecycler.adapter = adapter
        binding.addScheduleFab.setOnClickListener { showScheduleEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                scheduleDao.observeAllWithNames().collect { schedules ->
                    adapter.submitList(schedules)
                    binding.schedulesEmptyText.isVisible = schedules.isEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showScheduleEditor(item: BarberScheduleWithName?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val barbers = try {
                withContext(Dispatchers.IO) { scheduleDao.getAllBarbers() }
            } catch (_: Exception) {
                showMessage(R.string.error_database)
                return@launch
            }
            if (barbers.isEmpty()) {
                showMessage(R.string.schedules_need_barber)
                return@launch
            }

            val dialogBinding = DialogScheduleBinding.inflate(layoutInflater)
            configureDropdowns(dialogBinding, barbers, item?.schedule)
            dialogBinding.scheduleStartInput.setOnClickListener {
                showTimePicker(dialogBinding.scheduleStartInput.text?.toString() ?: "09:00") {
                    dialogBinding.scheduleStartInput.setText(it)
                }
            }
            dialogBinding.scheduleEndInput.setOnClickListener {
                showTimePicker(dialogBinding.scheduleEndInput.text?.toString() ?: "18:00") {
                    dialogBinding.scheduleEndInput.setText(it)
                }
            }

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (item == null) R.string.schedules_add_title else R.string.schedules_edit_title)
                .setView(dialogBinding.root)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.schedule_save, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val barberIndex = barbers.indexOfFirst {
                        it.name == dialogBinding.scheduleBarberDropdown.text.toString()
                    }
                    val dayNames = resources.getStringArray(R.array.week_days).toList()
                    val dayIndex = dayNames.indexOf(dialogBinding.scheduleDayDropdown.text.toString())
                    val start = dialogBinding.scheduleStartInput.text?.toString().orEmpty()
                    val end = dialogBinding.scheduleEndInput.text?.toString().orEmpty()
                    val durationText = dialogBinding.scheduleDurationDropdown.text.toString()
                    val duration = durationText.substringBefore(' ').toIntOrNull()

                    if (barberIndex < 0 || dayIndex < 0 || duration == null) {
                        showMessage(R.string.schedule_complete_fields)
                        return@setOnClickListener
                    }
                    if (!isValidTime(start) || !isValidTime(end) || start >= end) {
                        showMessage(R.string.schedule_invalid_range)
                        return@setOnClickListener
                    }

                    val barber = barbers[barberIndex]
                    val dayOfWeek = dayIndex + 1
                    val schedule = BarberSchedule(
                        id = item?.schedule?.id ?: 0L,
                        barberId = barber.id,
                        dayOfWeek = dayOfWeek,
                        startTime = start,
                        endTime = end,
                        slotDurationMinutes = duration,
                        active = dialogBinding.scheduleActiveCheck.isChecked
                    )
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        val saved = try {
                            withContext(Dispatchers.IO) {
                                val alreadyExists = scheduleDao.getByBarberAndDay(barber.id, dayOfWeek)
                                if (alreadyExists != null && alreadyExists.id != schedule.id) {
                                    false
                                } else {
                                    if (schedule.id == 0L) scheduleDao.insert(schedule)
                                    else scheduleDao.update(schedule)
                                    true
                                }
                            }
                        } catch (_: SQLiteConstraintException) {
                            false
                        } catch (_: Exception) {
                            null
                        }
                        when (saved) {
                            true -> {
                                dialog.dismiss()
                                showMessage(R.string.schedule_saved)
                            }
                            false -> {
                                showMessage(R.string.schedule_duplicate_day)
                                if (dialog.isShowing) {
                                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                }
                            }
                            null -> {
                                showMessage(R.string.error_database)
                                if (dialog.isShowing) {
                                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                }
                            }
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    private fun configureDropdowns(
        dialogBinding: DialogScheduleBinding,
        barbers: List<Barber>,
        existing: BarberSchedule?
    ) {
        val barberNames = barbers.map { it.name }
        dialogBinding.scheduleBarberDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, barberNames)
        )
        dialogBinding.scheduleDayDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, resources.getStringArray(R.array.week_days))
        )
        val durations = listOf("15 minutos", "30 minutos", "45 minutos", "60 minutos")
        dialogBinding.scheduleDurationDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, durations)
        )

        dialogBinding.scheduleBarberDropdown.setText(
            barbers.firstOrNull { it.id == existing?.barberId }?.name ?: barberNames.first(), false
        )
        val days = resources.getStringArray(R.array.week_days)
        dialogBinding.scheduleDayDropdown.setText(
            days.getOrElse((existing?.dayOfWeek ?: Calendar.MONDAY) - 1) { days.first() }, false
        )
        dialogBinding.scheduleStartInput.setText(existing?.startTime ?: "09:00")
        dialogBinding.scheduleEndInput.setText(existing?.endTime ?: "18:00")
        dialogBinding.scheduleDurationDropdown.setText(
            durations.firstOrNull { it.startsWith("${existing?.slotDurationMinutes ?: 30} ") } ?: durations[1],
            false
        )
        dialogBinding.scheduleActiveCheck.isChecked = existing?.active ?: true
    }

    private fun showTimePicker(initial: String, onPicked: (String) -> Unit) {
        val pieces = initial.split(":")
        val hour = pieces.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = pieces.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(requireContext(), { _, h, m ->
            onPicked(String.format(Locale.ROOT, "%02d:%02d", h, m))
        }, hour, minute, true).show()
    }

    private fun isValidTime(value: String): Boolean =
        value.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$"))

    private fun toggleSchedule(item: BarberScheduleWithName) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    scheduleDao.setActive(item.schedule.id, !item.schedule.active)
                }
                showMessage(if (item.schedule.active) R.string.schedule_deactivated else R.string.schedule_activated)
            } catch (_: Exception) {
                showMessage(R.string.error_database)
            }
        }
    }

    private fun showMessage(messageRes: Int) {
        _binding?.let { Snackbar.make(it.root, messageRes, Snackbar.LENGTH_SHORT).show() }
    }
}
