package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_slots",
    indices = [Index(value = ["barberId", "date", "time"], unique = true)]
)
data class BlockedSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val barberId: Long,
    val date: String,
    val time: String
)