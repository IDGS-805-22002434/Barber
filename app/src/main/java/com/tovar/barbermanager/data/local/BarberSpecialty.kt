package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "barber_specialties",
    primaryKeys = ["barberId", "specialtyId"],
    foreignKeys = [
        ForeignKey(
            entity = Barber::class,
            parentColumns = ["id"],
            childColumns = ["barberId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Specialty::class,
            parentColumns = ["id"],
            childColumns = ["specialtyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("specialtyId")]
)
data class BarberSpecialty(
    val barberId: Long,
    val specialtyId: Long
)
