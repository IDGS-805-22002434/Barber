package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "specialties",
    indices = [Index(value = ["name"], unique = true)]
)
data class Specialty(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val active: Boolean = true
)
