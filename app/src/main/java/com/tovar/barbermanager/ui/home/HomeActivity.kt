package com.tovar.barbermanager.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.databinding.ActivityHomeBinding
import com.tovar.barbermanager.ui.login.LoginActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.hasActiveSession) {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureNavigationForRole()

        intent.getStringExtra(EXTRA_EMAIL)?.let { SessionManager(this).setActiveEmail(it) }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val options = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, saveState = true, inclusive = false)
                        .setLaunchSingleTop(true)
                        .build()
                    try {
                        navController.navigate(R.id.homeFragment, null, options)
                    } catch (_: IllegalArgumentException) {
                        // Ya se está en Inicio.
                    }
                    true
                }
                R.id.nav_appointments -> {
                    val options = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, inclusive = false)
                        .setLaunchSingleTop(true)
                        .build()
                    navController.navigate(R.id.appointmentsFragment, null, options)
                    true
                }
                R.id.nav_promos -> {
                    showComingSoon()
                    false
                }
                else -> false
            }
        }

        // Mantiene resaltada la pestaña correcta aunque se llegue por otro camino
        // (por ejemplo desde "Agendar cita" en Inicio). isChecked no dispara el listener.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val itemId = when (destination.id) {
                R.id.appointmentsFragment, R.id.bookingFragment, R.id.receiptFragment ->
                    R.id.nav_appointments
                R.id.homeFragment -> R.id.nav_home
                else -> null
            }
            if (itemId != null) binding.bottomNav.menu.findItem(itemId)?.isChecked = true
        }
    }

    private fun configureNavigationForRole() {
        val menu = binding.bottomNav.menu
        if (session.isAdmin) {
            menu.findItem(R.id.nav_promos).isVisible = false
            menu.findItem(R.id.nav_profile).isVisible = false
            menu.findItem(R.id.nav_appointments).title = getString(R.string.nav_all_appointments)
        }
    }

    private fun showComingSoon() {
        Snackbar.make(binding.root, R.string.msg_coming_soon, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.bottomNav)
            .show()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}
