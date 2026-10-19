package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Relaciona el token de Firebase Cloud Messaging con la cuenta propietaria. */
@Entity(
    tableName = "device_registrations",
    foreignKeys = [
        ForeignKey(
            entity = UserAccount::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["fcmToken"], unique = true)]
)
data class DeviceRegistration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val fcmToken: String,
    val active: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis()
)
