package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {

    /** Cliente: solo sus avisos (RNF07). */
    @Query("SELECT * FROM app_notifications WHERE userEmail = :email COLLATE NOCASE ORDER BY createdAt DESC, id DESC")
    fun observeForUser(email: String): Flow<List<AppNotification>>

    /** Administrador: registro completo de notificaciones generadas (RF07.03). */
    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE userEmail = :email COLLATE NOCASE AND isRead = 0")
    fun observeUnreadCount(email: String): Flow<Int>

    @Query("SELECT * FROM app_notifications WHERE userEmail = :email COLLATE NOCASE AND status = 'PENDIENTE' ORDER BY createdAt, id")
    suspend fun pendingFor(email: String): List<AppNotification>

    @Query("SELECT * FROM app_notifications WHERE id = :id")
    suspend fun getById(id: Long): AppNotification?

    @Query(
        "SELECT COUNT(*) FROM app_notifications WHERE appointmentId = :appointmentId " +
            "AND type = :type AND title = :title"
    )
    suspend fun countSimilar(appointmentId: Long, type: String, title: String): Int

    @Insert
    suspend fun insert(notification: AppNotification): Long

    @Update
    suspend fun update(notification: AppNotification)

    @Query("UPDATE app_notifications SET status = :status, sentAt = :sentAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, sentAt: Long?)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE userEmail = :email COLLATE NOCASE AND isRead = 0")
    suspend fun markAllRead(email: String)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)
}

@Dao
interface DeviceRegistrationDao {

    @Query("SELECT * FROM device_registrations WHERE userEmail = :email COLLATE NOCASE AND active = 1")
    suspend fun activeFor(email: String): List<DeviceRegistration>

    @Query("SELECT * FROM device_registrations WHERE fcmToken = :token LIMIT 1")
    suspend fun getByToken(token: String): DeviceRegistration?

    @Insert
    suspend fun insert(registration: DeviceRegistration): Long

    @Update
    suspend fun update(registration: DeviceRegistration)

    @Query("UPDATE device_registrations SET active = 0 WHERE userEmail = :email COLLATE NOCASE")
    suspend fun deactivateForUser(email: String)
}
