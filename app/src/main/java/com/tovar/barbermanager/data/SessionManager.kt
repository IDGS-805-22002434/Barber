package com.tovar.barbermanager.data

import android.content.Context
import com.tovar.barbermanager.data.local.UserAccount
import com.tovar.barbermanager.data.local.UserRole
import com.tovar.barbermanager.data.remote.ApiClient

/**
 * Conserva la identidad de la sesión en el dispositivo. El rol se copia desde
 * la cuenta validada; la interfaz ya no permite elevar privilegios con un switch.
 */
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

    val userName: String?
        get() = prefs.getString(KEY_NAME, null)

    val userId: Long
        get() = prefs.getLong(KEY_USER_ID, 0L)

    val userRole: UserRole
        get() = UserRole.fromStorage(prefs.getString(KEY_ROLE, UserRole.CLIENTE.name))

    val isAdmin: Boolean
        get() = userRole == UserRole.ADMINISTRADOR

    /** Token JWT de la API; null cuando la sesión es solo local (sin red). */
    val authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)

    /** true si el login se validó contra el servidor y no solo contra Room. */
    val isServerSession: Boolean
        get() = authToken != null

    val serverUrl: String
        get() = ApiClient.normalizeBaseUrl(
            prefs.getString(KEY_SERVER_URL, null) ?: ApiClient.DEFAULT_BASE_URL
        ) ?: ApiClient.DEFAULT_BASE_URL

    /**
     * Debe llamarse al iniciar la app para que las peticiones lleven el token.
     * Si hay una sesión de servidor activa se conserva su URL (aunque el usuario
     * haya configurado otra para el próximo login); sin token se usa la URL
     * configurada. Así cambiar de servidor no cierra la sesión en curso.
     */
    fun restoreApiSession() {
        val configuredUrl = serverUrl
        val token = prefs.getString(KEY_TOKEN, null)
        if (token != null) {
            val tokenServerUrl = ApiClient.normalizeBaseUrl(
                prefs.getString(KEY_TOKEN_SERVER_URL, null).orEmpty()
            )
            if (tokenServerUrl == null) {
                clear()
                ApiClient.setBaseUrl(configuredUrl)
            } else {
                ApiClient.setToken(token)
                ApiClient.setBaseUrl(tokenServerUrl)
            }
        } else {
            ApiClient.setToken(null)
            ApiClient.setBaseUrl(configuredUrl)
        }
    }

    fun saveSession(user: UserAccount, rememberMe: Boolean, token: String? = null) {
        val editor = prefs.edit()
            .putBoolean(KEY_ACTIVE_SESSION, true)
            .putBoolean(KEY_REMEMBER, rememberMe)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NAME, user.fullName)
            .putString(KEY_ROLE, user.role)
            .putString(KEY_TOKEN, token)
        if (token != null) {
            editor.putString(KEY_TOKEN_SERVER_URL, ApiClient.baseUrl)
        } else {
            editor.remove(KEY_TOKEN_SERVER_URL)
        }
        editor.apply()
        ApiClient.setToken(token)
    }

    /**
     * Guarda la URL para el próximo inicio de sesión sin cambiar la conexión
     * activa, de modo que un error de escritura no deje la sesión apuntando a
     * un servidor distinto. Devuelve false si la URL no es válida.
     */
    fun saveServerUrl(url: String): Boolean {
        val normalized = ApiClient.normalizeBaseUrl(url) ?: return false
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        return true
    }

    /** Mantiene el nombre local en sincronía tras editar el perfil en el servidor. */
    fun updateLocalName(nombre: String) {
        prefs.edit().putString(KEY_NAME, nombre).apply()
    }

    /** Cierra la sesión pero conserva la URL del servidor configurada. */
    fun clear() {
        prefs.edit()
            .remove(KEY_ACTIVE_SESSION)
            .remove(KEY_REMEMBER)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_SERVER_URL)
            .apply()
        ApiClient.setToken(null)
    }

    private companion object {
        const val PREFS_NAME = "barbermanager_session"
        const val KEY_ACTIVE_SESSION = "active_session"
        const val KEY_REMEMBER = "remember_login"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "full_name"
        const val KEY_ROLE = "user_role"
        const val KEY_TOKEN = "api_token"
        const val KEY_TOKEN_SERVER_URL = "api_token_server_url"
        const val KEY_SERVER_URL = "server_url"
    }
}
