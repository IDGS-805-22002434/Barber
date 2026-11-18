package com.tovar.barbermanager.ui.home

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.notifications.FcmTokenManager
import com.tovar.barbermanager.data.notifications.NotificationCenter
import com.tovar.barbermanager.data.notifications.NotificationHelper
import com.tovar.barbermanager.data.notifications.ReminderScheduler
import com.tovar.barbermanager.databinding.ActivityHomeBinding
import com.tovar.barbermanager.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private var pendingDeliveryEmail: String? = null

    /** Permiso de notificaciones en Android 13+ (RNF13). */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingDeliveryEmail?.let { deliverPending(it) }
            pendingDeliveryEmail = null
            if (!granted) showNotificationsDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        session.restoreApiSession()
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
                    val options = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, inclusive = false)
                        .setLaunchSingleTop(true)
                        .build()
                    navController.navigate(R.id.promotionsFragment, null, options)
                    true
                }
                R.id.nav_notifications -> {
                    val options = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, inclusive = false)
                        .setLaunchSingleTop(true)
                        .build()
                    navController.navigate(R.id.notificationsFragment, null, options)
                    true
                }
                R.id.nav_profile -> {
                    val options = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, inclusive = false)
                        .setLaunchSingleTop(true)
                        .build()
                    navController.navigate(R.id.profileFragment, null, options)
                    true
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val itemId = when (destination.id) {
                R.id.appointmentsFragment, R.id.bookingFragment, R.id.receiptFragment ->
                    R.id.nav_appointments
                R.id.homeFragment -> R.id.nav_home
                R.id.promotionsFragment, R.id.promotionFormFragment, R.id.couponUsageFragment ->
                    R.id.nav_promos
                R.id.notificationsFragment -> R.id.nav_notifications
                R.id.profileFragment -> R.id.nav_profile
                else -> null
            }
            if (itemId != null) binding.bottomNav.menu.findItem(itemId)?.isChecked = true
        }

        setupNotifications()
    }

    /**
     * RF07: permiso de notificaciones, entrega de avisos pendientes, recordatorios de las
     * próximas citas y registro del dispositivo en FCM (RF07.04).
     */
    private fun setupNotifications() {
        val email = session.currentEmail?.takeIf { it.isNotBlank() } ?: return
        if (!session.isAdmin) observeUnreadBadge(email)
        FcmTokenManager.registerCurrentDevice(applicationContext, email)
        lifecycleScope.launch(Dispatchers.IO) {
            ReminderScheduler.syncFor(applicationContext, email)
        }
        if (NotificationHelper.hasPermission(this)) {
            deliverPending(email)
        } else {
            pendingDeliveryEmail = email
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun deliverPending(email: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                NotificationHelper.ensureChannel(applicationContext)
                NotificationCenter(applicationContext).deliverPending(email)
            }
        }
    }

    private fun observeUnreadBadge(email: String) {
        val dao = AppDatabase.getInstance(this).notificationDao()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.observeUnreadCount(email).collect { count ->
                    if (count > 0) {
                        binding.bottomNav.getOrCreateBadge(R.id.nav_notifications).number = count
                    } else {
                        binding.bottomNav.removeBadge(R.id.nav_notifications)
                    }
                }
            }
        }
    }

    /** Aviso una sola vez: sin permiso no llegan recordatorios (RNF13). */
    private fun showNotificationsDenied() {
        val prefs = getSharedPreferences("barbermanager_ui", MODE_PRIVATE)
        if (prefs.getBoolean("notif_denied_notice", false)) return
        prefs.edit().putBoolean("notif_denied_notice", true).apply()
        Snackbar.make(binding.root, R.string.notif_permission_denied, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.bottomNav)
            .setAction(R.string.notif_permission_action) { NotificationHelper.openSettings(this) }
            .show()
    }

    private fun configureNavigationForRole() {
        val menu = binding.bottomNav.menu
        if (session.isAdmin) {
            menu.findItem(R.id.nav_promos).isVisible = false
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
