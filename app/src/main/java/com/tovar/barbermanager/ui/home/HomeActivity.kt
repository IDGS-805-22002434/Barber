package com.tovar.barbermanager.ui.home

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.databinding.ActivityHomeBinding

/**
 * Activity anfitriona de la navegación: contiene el NavHostFragment con
 * Inicio, Servicios, Barberos y sus formularios, más la barra inferior.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

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