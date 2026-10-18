package com.tovar.barbermanager.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** Un barbero con sus especialidades del catálogo (RF03.03, relación N:N). */
data class BarberWithSpecialties(
    @Embedded val barber: Barber,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = Specialty::class,
        associateBy = Junction(
            value = BarberSpecialty::class,
            parentColumn = "barberId",
            entityColumn = "specialtyId"
        )
    )
    val specialties: List<Specialty>
)
