package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserAccount::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Appointment::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["appointmentId"])]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val appointmentId: Long? = null,
    val title: String,
    val message: String,
    val type: String,
    /** PENDIENTE, ENVIADA o FALLIDA. */
    val status: String = "PENDIENTE",
    val createdAt: Long = System.currentTimeMillis()
)
