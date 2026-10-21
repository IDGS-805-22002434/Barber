package com.tovar.barbermanager.ui.appointments

import android.content.Intent
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
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentHistory
import com.tovar.barbermanager.data.local.AppointmentStatus
import com.tovar.barbermanager.data.local.HistoryAction
import com.tovar.barbermanager.databinding.FragmentReceiptBinding
import com.tovar.barbermanager.databinding.ItemReceiptRowBinding
import com.tovar.barbermanager.ui.util.Format
import kotlinx.coroutines.launch

class ReceiptFragment : Fragment() {

    private var _binding: FragmentReceiptBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReceiptViewModel by lazy {
        ViewModelProvider(this)[ReceiptViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.appointmentsButton.setOnClickListener {
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, inclusive = false)
                .setLaunchSingleTop(true)
                .build()
            findNavController().navigate(R.id.appointmentsFragment, null, options)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (!state.loaded) return@collect
                    val appointment = state.appointment
                    if (appointment == null) {
                        Snackbar.make(binding.root, R.string.receipt_not_found, Snackbar.LENGTH_SHORT)
                            .show()
                        findNavController().navigateUp()
                    } else {
                        render(appointment, state.history)
                    }
                }
            }
        }

        val id = arguments?.getLong(ARG_APPOINTMENT_ID, 0L) ?: 0L
        viewModel.load(id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.receiptHeaderBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.receiptRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.receiptHeaderBar.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun render(appointment: Appointment, history: List<AppointmentHistory>) {
        binding.receiptHeader.setText(
            when (appointment.status) {
                AppointmentStatus.CANCELADA -> R.string.receipt_header_cancelled
                AppointmentStatus.COMPLETADA -> R.string.receipt_header_completed
                else -> R.string.receipt_header_confirmed
            }
        )
        binding.receiptFolio.text = getString(
            R.string.receipt_folio, AppointmentUi.folio(appointment.id)
        )

        binding.receiptRows.removeAllViews()
        addRow(R.string.receipt_client, appointment.clientName)
        if (appointment.clientPhone.isNotBlank()) {
            addRow(R.string.receipt_phone, appointment.clientPhone)
        }
        addRow(R.string.receipt_service, appointment.serviceName)
        addRow(R.string.receipt_barber, appointment.barberName)
        addRow(R.string.receipt_date, Dates.display(appointment.date))
        addRow(R.string.receipt_time, appointment.time)
        addRow(R.string.receipt_total, Format.price(appointment.priceCents))
        addRow(R.string.receipt_status, getString(AppointmentUi.statusLabel(appointment)))

        binding.historySection.isVisible = history.isNotEmpty()
        binding.historyText.text = history.joinToString("\n") { historyLine(it) }

        binding.shareButton.setOnClickListener { share(appointment) }
    }

    private fun addRow(labelRes: Int, value: String) {
        val row = ItemReceiptRowBinding.inflate(layoutInflater, binding.receiptRows, false)
        row.rowLabel.setText(labelRes)
        row.rowValue.text = value
        binding.receiptRows.addView(row.root)
    }

    private fun historyLine(entry: AppointmentHistory): String =
        if (entry.action == HistoryAction.REPROGRAMADA) {
            getString(
                R.string.receipt_history_rescheduled,
                Dates.displayShort(entry.oldDate), entry.oldTime,
                Dates.displayShort(entry.newDate), entry.newTime
            )
        } else {
            getString(
                R.string.receipt_history_cancelled,
                Dates.displayShort(entry.oldDate), entry.oldTime
            )
        }

    private fun share(appointment: Appointment) {
        val text = getString(
            R.string.receipt_share_text,
            AppointmentUi.folio(appointment.id),
            appointment.clientName,
            appointment.serviceName,
            appointment.barberName,
            Dates.display(appointment.date),
            appointment.time,
            Format.price(appointment.priceCents),
            getString(AppointmentUi.statusLabel(appointment))
        )
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.receipt_share_chooser)))
    }

    companion object {
        const val ARG_APPOINTMENT_ID = "appointmentId"
    }
}
