package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE email = :normalizedEmail LIMIT 1")
    suspend fun getByEmail(normalizedEmail: String): UserAccount?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert
    suspend fun insert(user: UserAccount): Long

    @Query("UPDATE users SET passwordHash = :passwordHash, passwordSalt = :passwordSalt WHERE email = :normalizedEmail")
    suspend fun updatePassword(
        normalizedEmail: String,
        passwordHash: String,
        passwordSalt: String
    ): Int
}
