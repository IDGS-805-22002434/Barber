package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Estados de entrega de una notificación (RF07.03). */
object NotificationStatus {
    /** Generada, pero aún no mostrada en el dispositivo del destinatario. */
    const val PENDIENTE = "PENDIENTE"
    const val ENVIADA = "ENVIADA"

    /** No se pudo entregar (permiso de notificaciones denegado o canal desactivado). */
    const val FALLIDA = "FALLIDA"
}

object NotificationType {
    const val CONFIRMACION = "CONFIRMACION"
    const val REPROGRAMACION = "REPROGRAMACION"
    const val CANCELACION = "CANCELACION"
    const val RECORDATORIO = "RECORDATORIO"
    const val COMPLETADA = "COMPLETADA"
    const val SISTEMA = "SISTEMA"
}

/**
 * Registro de cada notificación generada y su estado (RF07.03).
 * El destinatario se identifica por correo, igual que las citas (RNF07).
 */
@Entity(
    tableName = "app_notifications",
    indices = [Index(value = ["userEmail"]), Index(value = ["appointmentId"])]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val appointmentId: Long? = null,
    val title: String,
    val message: String,
    val type: String,
    val status: String = NotificationStatus.PENDIENTE,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null
)
