package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "appointments", indices = [Index(value = ["clientUserId"])])
data class Appointment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clientName: String,
    val clientPhone: String,
    val barberId: Long,
    val barberName: String,
    val serviceId: Long,
    val serviceName: String,
    val servicePrice: Double,
    val date: String,
    val time: String,
    val status: String = "PENDIENTE",
    /** Cuenta propietaria; null conserva compatibilidad con citas antiguas/de demostración. */
    val clientUserId: Long? = null
)