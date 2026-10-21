package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cuenta local de BarberManager. Las contraseñas se guardan únicamente como hash + salt. */
@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    /** Valores permitidos: CLIENTE y ADMINISTRADOR. */
    val role: String = UserRole.CLIENTE.name,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserRole {
    CLIENTE,
    ADMINISTRADOR;

    companion object {
        fun fromStorage(value: String?): UserRole =
            entries.firstOrNull { it.name == value } ?: CLIENTE
    }
}
