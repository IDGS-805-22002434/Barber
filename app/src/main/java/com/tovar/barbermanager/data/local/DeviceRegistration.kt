package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Relaciona el token de Firebase Cloud Messaging con la cuenta propietaria (RF07.04). */
@Entity(
    tableName = "device_registrations",
    indices = [Index(value = ["userEmail"]), Index(value = ["fcmToken"], unique = true)]
)
data class DeviceRegistration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val fcmToken: String,
    val active: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis()
)
