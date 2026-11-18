package com.tovar.barbermanager.data.notifications

import android.content.Context
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.AppNotification
import com.tovar.barbermanager.data.local.NotificationStatus

/**
 * Registra cada notificación generada y su estado (RF07.03) y la entrega en el dispositivo
 * cuando el destinatario es quien tiene la sesión abierta. Si no lo es, queda PENDIENTE y se
 * entrega la próxima vez que esa cuenta abra la app.
 */
class NotificationCenter(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).notificationDao()

    suspend fun notify(
        userEmail: String,
        appointmentId: Long?,
        type: String,
        title: String,
        message: String
    ): Long {
        val id = dao.insert(
            AppNotification(
                userEmail = userEmail.trim().lowercase(),
                appointmentId = appointmentId,
                title = title,
                message = message,
                type = type
            )
        )
        val session = SessionManager(appContext)
        val current = session.currentEmail
        if (session.hasActiveSession && current != null && current.equals(userEmail.trim(), ignoreCase = true)) {
            deliver(id)
        }
        return id
    }

    /** Muestra la notificación y guarda si salió bien (ENVIADA) o no (FALLIDA). */
    suspend fun deliver(id: Long) {
        val notification = dao.getById(id) ?: return
        val shown = NotificationHelper.show(
            appContext,
            id.toInt(),
            notification.title,
            notification.message
        )
        dao.updateStatus(
            id,
            if (shown) NotificationStatus.ENVIADA else NotificationStatus.FALLIDA,
            if (shown) System.currentTimeMillis() else null
        )
    }

    /** Entrega lo que quedó pendiente para [email] mientras no tenía la sesión abierta. */
    suspend fun deliverPending(email: String) {
        dao.pendingFor(email.trim()).forEach { deliver(it.id) }
    }
}
