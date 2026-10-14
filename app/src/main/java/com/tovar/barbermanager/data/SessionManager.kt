package com.tovar.barbermanager.data

import android.content.Context

/**
 * Guarda la sesión del usuario en el dispositivo.
 *
 * Por ahora usa SharedPreferences como marcador de "sesión recordada".
 * Cuando exista backend, aquí se guardará el token (idealmente con
 * EncryptedSharedPreferences o DataStore + Keystore) para cumplir con
 * "persistencia de sesión segura" de la HU-01.
 */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)

    val userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)

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
    }
}
