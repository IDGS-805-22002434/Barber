package com.tovar.barbermanager.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.tabs.TabLayout
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.databinding.FragmentAppointmentsBinding
import kotlinx.coroutines.launch

class AppointmentsFragment : Fragment() {

    private var _binding: FragmentAppointmentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppointmentsViewModel by lazy {
        ViewModelProvider(this)[AppointmentsViewModel::class.java]
    }

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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
}
