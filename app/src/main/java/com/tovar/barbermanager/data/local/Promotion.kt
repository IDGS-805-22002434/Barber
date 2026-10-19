package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "promotions")
data class Promotion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val conditions: String = "",
    val startsAt: Long,
    val endsAt: Long,
    val active: Boolean = true
)
