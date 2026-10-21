package com.tovar.barbermanager.data

import android.content.Context


class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)

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

    fun saveSession(email: String) {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "barbermanager_session"
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_EMAIL = "email"
        const val KEY_ADMIN = "admin_mode"
        const val KEY_ACTIVE_EMAIL = "active_email"
    }
}
