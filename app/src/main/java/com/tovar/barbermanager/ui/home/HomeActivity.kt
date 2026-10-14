package com.tovar.barbermanager.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.databinding.ActivityHomeBinding
import com.tovar.barbermanager.databinding.ItemQuickActionBinding
import com.tovar.barbermanager.ui.login.LoginActivity
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    /** Acceso rápido a una sección; [sprint] indica en qué ciclo del plan se construye. */
    private data class QuickAction(
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val subtitle: Int,
        val sprint: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        applyInsets()
        bindGreeting()
        setupQuickActions()
        setupBottomNav()

        binding.bookButton.setOnClickListener { showComingSprint(SPRINT_BOOKING) }
        binding.logoutButton.setOnClickListener { confirmLogout() }
    }

    private fun applyInsets() {
        val headerBaseTop = binding.homeHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeHeader.updatePadding(top = headerBaseTop + bars.top)
            // El BottomNavigationView ya maneja el inset inferior (paddingBottomSystemWindowInsets).
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
        binding.nameText.text = displayName(intent.getStringExtra(EXTRA_EMAIL))
    }

    /** Nombre provisional a partir del correo; se reemplazará por el nombre real del perfil. */
    private fun displayName(email: String?): String {
        val raw = email?.substringBefore('@')?.split('.', '_', '-', '+')?.firstOrNull().orEmpty()
        return if (raw.isBlank()) {
            getString(R.string.default_user_name).replaceFirstChar { it.uppercase() }
        } else {
            raw.replaceFirstChar { it.uppercase() }
        }
    }

    private fun setupQuickActions() {
        val actions = listOf(
            QuickAction(R.drawable.ic_scissors, R.string.qa_services, R.string.qa_services_desc, SPRINT_CATALOG),
            QuickAction(R.drawable.ic_person, R.string.qa_barbers, R.string.qa_barbers_desc, SPRINT_BARBERS),
            QuickAction(R.drawable.ic_calendar, R.string.qa_appointments, R.string.qa_appointments_desc, SPRINT_BOOKING),
            QuickAction(R.drawable.ic_offer, R.string.qa_promos, R.string.qa_promos_desc, SPRINT_LOYALTY)
        )
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
            item.root.setOnClickListener { showComingSprint(action.sprint) }
            binding.quickActionsGrid.addView(item.root)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_appointments -> deny { showComingSprint(SPRINT_BOOKING) }
                R.id.nav_promos -> deny { showComingSprint(SPRINT_LOYALTY) }
                else -> deny { showSnackbar(getString(R.string.msg_coming_soon)) }
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    /** Muestra el aviso y devuelve false para que la pestaña "Inicio" siga seleccionada. */
    private inline fun deny(block: () -> Unit): Boolean {
        block()
        return false
    }

    private fun showComingSprint(sprint: Int) {
        showSnackbar(getString(R.string.msg_coming_sprint, sprint))
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.bottomNav)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.logout_confirm) { _, _ ->
                session.clear()
                val intent = Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"

        // Sprint donde se construye cada módulo según el reporte de definición.
        private const val SPRINT_CATALOG = 1
        private const val SPRINT_BARBERS = 2
        private const val SPRINT_BOOKING = 3
        private const val SPRINT_LOYALTY = 4
    }
}
