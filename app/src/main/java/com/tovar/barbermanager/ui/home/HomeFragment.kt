package com.tovar.barbermanager.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.AppointmentRepository
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Appointment
import com.tovar.barbermanager.data.local.AppointmentStatus
import com.tovar.barbermanager.databinding.FragmentHomeBinding
import com.tovar.barbermanager.databinding.ItemQuickActionBinding
import com.tovar.barbermanager.ui.login.LoginActivity
import java.util.Calendar
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    private data class QuickAction(
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val subtitle: Int,
        val sprint: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())

        applyInsets()
        bindGreeting()
        configureDashboardForRole()
        setupQuickActions()

        observeNextAppointment()
        binding.logoutButton.setOnClickListener { confirmLogout() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.homeHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentHomeRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right)
            insets
        }
    }

    private fun bindGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.greetingText.setText(
            when {
                hour < 12 -> R.string.greeting_morning
                hour < 19 -> R.string.greeting_afternoon
                else -> R.string.greeting_night
            }
        )
        binding.nameText.text = session.userName?.takeIf { it.isNotBlank() }
            ?: displayName(requireActivity().intent.getStringExtra(HomeActivity.EXTRA_EMAIL))
    }

    private fun displayName(email: String?): String {
        val raw = email?.substringBefore('@')?.split('.', '_', '-', '+')?.firstOrNull().orEmpty()
        return if (raw.isBlank()) {
            getString(R.string.default_user_name).replaceFirstChar { it.uppercase() }
        } else {
            raw.replaceFirstChar { it.uppercase() }
        }
    }

    private fun configureDashboardForRole() {
        val isAdmin = session.isAdmin
        // El control que antes elevaba permisos manualmente se retira de la interfaz.
        binding.adminSwitch.isVisible = false
        binding.homeNextTitleText.isVisible = !isAdmin
        binding.homeNextCard.isVisible = !isAdmin
        binding.promoCard.isVisible = !isAdmin
        binding.quickActionsTitle.setText(
            if (isAdmin) R.string.admin_dashboard_title else R.string.home_quick_title
        )
    }

    private fun setupQuickActions() {
        val actions = if (session.isAdmin) {
            listOf(
                QuickAction(R.drawable.ic_scissors, R.string.qa_services, R.string.qa_services_admin_desc, SPRINT_CATALOG),
                QuickAction(R.drawable.ic_person, R.string.qa_barbers, R.string.qa_barbers_admin_desc, SPRINT_BARBERS),
                QuickAction(R.drawable.ic_person, R.string.qa_specialties, R.string.qa_specialties_desc, SPRINT_SPECIALTIES),
                QuickAction(R.drawable.ic_calendar, R.string.qa_schedules, R.string.qa_schedules_desc, SPRINT_SCHEDULES),
                QuickAction(R.drawable.ic_calendar, R.string.qa_manage_appointments, R.string.qa_manage_appointments_desc, SPRINT_BOOKING)
            )
        } else {
            listOf(
                QuickAction(R.drawable.ic_scissors, R.string.qa_services, R.string.qa_services_desc, SPRINT_CATALOG),
                QuickAction(R.drawable.ic_person, R.string.qa_barbers, R.string.qa_barbers_desc, SPRINT_BARBERS),
                QuickAction(R.drawable.ic_calendar, R.string.qa_appointments, R.string.qa_appointments_desc, SPRINT_BOOKING),
                QuickAction(R.drawable.ic_offer, R.string.qa_promos, R.string.qa_promos_desc, SPRINT_LOYALTY)
            )
        }
        val margin = (6 * resources.displayMetrics.density).toInt()

        actions.forEach { action ->
            val item = ItemQuickActionBinding.inflate(layoutInflater, binding.quickActionsGrid, false)
            item.actionIcon.setImageResource(action.icon)
            item.actionTitle.setText(action.title)
            item.actionSubtitle.setText(action.subtitle)
            item.root.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(margin, margin, margin, margin)
            }
            item.root.setOnClickListener { onQuickAction(action) }
            binding.quickActionsGrid.addView(item.root)
        }
    }

    private fun onQuickAction(action: QuickAction) {
        when (action.sprint) {
            SPRINT_CATALOG -> findNavController().navigate(R.id.action_home_to_services)
            SPRINT_BARBERS -> findNavController().navigate(R.id.action_home_to_barbers)
            SPRINT_SPECIALTIES -> findNavController().navigate(R.id.specialtiesFragment)
            SPRINT_SCHEDULES -> findNavController().navigate(R.id.schedulesFragment)
            SPRINT_BOOKING -> findNavController().navigate(R.id.appointmentsFragment)
            else -> showComingSprint(action.sprint)
        }
    }

    private fun setupAdminSwitch() {
        binding.adminSwitch.isChecked = session.isAdmin
        binding.adminSwitch.setOnCheckedChangeListener { _, checked ->
            session.isAdmin = checked
            showSnackbar(
                getString(if (checked) R.string.admin_enabled else R.string.admin_disabled)
            )
        }
    }

    private fun observeNextAppointment() {
        val repository = AppointmentRepository(AppDatabase.getInstance(requireContext()))
        val email = session.currentEmail
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeFor(email, isAdmin = false).collect { list ->
                    val now = Dates.nowKey()
                    renderNext(
                        list.firstOrNull {
                            it.status == AppointmentStatus.CONFIRMADA && "${it.date} ${it.time}" >= now
                        }
                    )
                }
            }
        }
    }

    private fun renderNext(next: Appointment?) {
        if (next == null) {
            binding.nextTitle.setText(R.string.home_empty_title)
            binding.nextDesc.setText(R.string.home_empty_desc)
            binding.bookButton.setText(R.string.home_book)
            binding.bookButton.setOnClickListener {
                findNavController().navigate(R.id.action_home_to_booking)
            }
        } else {
            binding.nextTitle.text = next.serviceName
            binding.nextDesc.text = getString(
                R.string.home_next_desc, Dates.displayShort(next.date), next.time, next.barberName
            )
            binding.bookButton.setText(R.string.home_view_appointments)
            binding.bookButton.setOnClickListener {
                findNavController().navigate(R.id.appointmentsFragment)
            }
        }
    }

    private fun showComingSprint(sprint: Int) {
        showSnackbar(getString(R.string.msg_coming_sprint, sprint))
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.logout_confirm) { _, _ ->
                session.clear()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                requireActivity().finish()
            }
            .show()
    }

    private companion object {
        const val SPRINT_CATALOG = 1
        const val SPRINT_BARBERS = 2
        const val SPRINT_BOOKING = 3
        const val SPRINT_LOYALTY = 4
        const val SPRINT_SPECIALTIES = 5
        const val SPRINT_SCHEDULES = 6
    }
}
