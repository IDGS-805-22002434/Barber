package com.tovar.barbermanager.data.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.DeviceRegistration
import com.tovar.barbermanager.data.remote.ApiClient
import com.tovar.barbermanager.data.remote.ApiException
import com.tovar.barbermanager.data.remote.DispositivoRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Asocia el dispositivo con la cuenta mediante su token de Firebase Cloud Messaging (RF07.04).
 *
 * Firebase se inicia con los valores de `res/values/firebase_config.xml`. Mientras conserven
 * el texto de ejemplo, FCM queda desactivado y la app sigue funcionando con notificaciones
 * locales; no hace falta tocar nada más del código.
 */
object FcmTokenManager {

    private const val PLACEHOLDER = "REEMPLAZAR"

    /** True cuando Firebase está iniciado (por google-services.json o por firebase_config.xml). */
    fun ensureFirebase(context: Context): Boolean {
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isNotEmpty()) return true
        val apiKey = app.getString(R.string.firebase_api_key)
        val applicationId = app.getString(R.string.firebase_app_id)
        val projectId = app.getString(R.string.firebase_project_id)
        val senderId = app.getString(R.string.firebase_sender_id)
        val values = listOf(apiKey, applicationId, projectId, senderId)
        if (values.any { it.isBlank() || it.startsWith(PLACEHOLDER) }) return false
        return try {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(applicationId)
                .setProjectId(projectId)
                .setGcmSenderId(senderId)
                .build()
            FirebaseApp.initializeApp(app, options)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Pide el token actual a Firebase y lo registra para [userEmail]. */
    fun registerCurrentDevice(context: Context, userEmail: String) {
        val app = context.applicationContext
        if (!ensureFirebase(app)) return
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val token = task.result ?: return@addOnCompleteListener
                registerToken(app, userEmail, token)
            }
        } catch (_: Exception) {
            // Sin Google Play Services o sin configuración: se sigue con notificaciones locales.
        }
    }

    /** Guarda el token en Room y, si hay sesión de servidor, también en el servidor. */
    fun registerToken(context: Context, userEmail: String, token: String) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(app).deviceDao()
            val email = userEmail.trim().lowercase()
            val existing = dao.getByToken(token)
            if (existing == null) {
                dao.insert(DeviceRegistration(userEmail = email, fcmToken = token))
            } else {
                dao.update(existing.copy(userEmail = email, active = true))
            }
            val session = SessionManager(app)
            if (session.isServerSession) {
                session.restoreApiSession()
                try {
                    ApiClient.call { ApiClient.service().registrarDispositivo(DispositivoRequest(token)) }
                } catch (_: ApiException) {
                    // Se reintenta en el siguiente inicio de sesión.
                }
            }
        }
    }

    /** Da de baja los dispositivos de la cuenta al cerrar sesión (antes de borrar el token). */
    suspend fun unregister(context: Context, userEmail: String) {
        val app = context.applicationContext
        val dao = AppDatabase.getInstance(app).deviceDao()
        val tokens = dao.activeFor(userEmail.trim())
        val session = SessionManager(app)
        if (session.isServerSession) {
            session.restoreApiSession()
            tokens.forEach { device ->
                try {
                    ApiClient.call { ApiClient.service().bajaDispositivo(DispositivoRequest(device.fcmToken)) }
                } catch (_: ApiException) {
                    // El servidor la dará de baja al detectar el token inválido.
                }
            }
        }
        dao.deactivateForUser(userEmail.trim())
    }
}
