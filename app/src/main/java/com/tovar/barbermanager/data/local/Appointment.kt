package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
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
    val status: String = "PENDIENTE"
)