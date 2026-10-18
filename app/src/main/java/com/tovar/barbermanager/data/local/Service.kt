package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tovar.barbermanager.data.model.ServiceCategory

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: ServiceCategory,
    val active: Boolean = true
)
