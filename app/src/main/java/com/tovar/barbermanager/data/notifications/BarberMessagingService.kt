package com.tovar.barbermanager.data.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Recibe los mensajes push (FCM) y renueva el token del dispositivo (RF07.04). */
class BarberMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val session = SessionManager(applicationContext)
        val email = session.currentEmail
        if (!session.hasActiveSession || email == null) return
        FcmTokenManager.registerToken(applicationContext, email, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val session = SessionManager(applicationContext)
        val email = session.currentEmail
        if (!session.hasActiveSession || email == null) return

        val title = message.notification?.title
            ?: message.data["titulo"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["mensaje"].orEmpty()
        if (body.isBlank()) return
        val appointmentId = message.data["citaId"]?.toLongOrNull()

        CoroutineScope(Dispatchers.IO).launch {
            NotificationCenter(applicationContext)
                .notify(email, appointmentId, NotificationType.SISTEMA, title, body)
        }
    }
}
