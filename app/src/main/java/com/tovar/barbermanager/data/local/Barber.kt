package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barbers")
data class Barber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val yearsExperience: Int,
    val active: Boolean = true
)
