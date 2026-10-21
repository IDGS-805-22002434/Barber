package com.tovar.barbermanager.data

import android.content.Context
import com.tovar.barbermanager.data.local.UserAccount
import com.tovar.barbermanager.data.local.UserRole

class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Solo se debe saltar el login al reabrir la aplicación si se eligió Recordarme. */
    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE_SESSION, false) && prefs.getBoolean(KEY_REMEMBER, false)

    val hasActiveSession: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE_SESSION, false)

    val userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)

    val currentEmail: String?
        get() = prefs.getString(KEY_ACTIVE_EMAIL, null) ?: userEmail

    fun setActiveEmail(email: String) {
        prefs.edit().putString(KEY_ACTIVE_EMAIL, email).apply()
    }

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_ADMIN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ADMIN, value).apply()
        }

    val userId: Long
        get() = prefs.getLong(KEY_USER_ID, 0L)

    val userRole: UserRole
        get() = UserRole.fromStorage(prefs.getString(KEY_ROLE, UserRole.CLIENTE.name))

    val isAdmin: Boolean
        get() = userRole == UserRole.ADMINISTRADOR

    fun saveSession(user: UserAccount, rememberMe: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ACTIVE_SESSION, true)
            .putBoolean(KEY_REMEMBER, rememberMe)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NAME, user.fullName)
            .putString(KEY_ROLE, user.role)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "barbermanager_session"
        const val KEY_ACTIVE_SESSION = "active_session"
        const val KEY_REMEMBER = "remember_login"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ADMIN = "admin_mode"
        const val KEY_ACTIVE_EMAIL = "active_email"
    }
}
