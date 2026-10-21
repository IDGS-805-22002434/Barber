package com.tovar.barbermanager.data

import android.database.sqlite.SQLiteConstraintException
import com.tovar.barbermanager.data.local.UserAccount
import com.tovar.barbermanager.data.local.UserDao
import com.tovar.barbermanager.data.local.UserRole

/** Resultados explícitos para que la interfaz pueda mostrar mensajes claros. */
sealed interface LoginResult {
    data class Success(val user: UserAccount) : LoginResult
    data object InvalidCredentials : LoginResult
    data object InactiveAccount : LoginResult
}

sealed interface RegisterResult {
    data object Created : RegisterResult
    data object EmailAlreadyExists : RegisterResult
    data object Failed : RegisterResult
}

class AuthRepository(private val userDao: UserDao) {

    suspend fun ensureDefaultAdmin() {
        val email = DEFAULT_ADMIN_EMAIL
        if (userDao.getByEmail(email) != null) return

        val password = PasswordHasher.create(DEFAULT_ADMIN_PASSWORD)
        val account = UserAccount(
            fullName = "Administrador BarberManager",
            email = email,
            passwordHash = password.encodedHash,
            passwordSalt = password.encodedSalt,
            role = UserRole.ADMINISTRADOR.name
        )
        try {
            userDao.insert(account)
        } catch (_: SQLiteConstraintException) {
            // Otra inicialización pudo haber creado la cuenta entre la consulta y el insert.
        }
    }

    suspend fun authenticate(email: String, password: String): LoginResult {
        val account = userDao.getByEmail(normalizeEmail(email))
            ?: return LoginResult.InvalidCredentials
        if (!account.active) return LoginResult.InactiveAccount
        if (!PasswordHasher.verify(password, account.passwordSalt, account.passwordHash)) {
            return LoginResult.InvalidCredentials
        }
        return LoginResult.Success(account)
    }

    /** El registro público siempre crea CLIENTE. Nunca acepta un rol de la pantalla. */
    suspend fun registerClient(
        fullName: String,
        email: String,
        password: String
    ): RegisterResult {
        val normalizedEmail = normalizeEmail(email)
        if (userDao.getByEmail(normalizedEmail) != null) return RegisterResult.EmailAlreadyExists

        val credentials = PasswordHasher.create(password)
        return try {
            userDao.insert(
                UserAccount(
                    fullName = fullName.trim(),
                    email = normalizedEmail,
                    passwordHash = credentials.encodedHash,
                    passwordSalt = credentials.encodedSalt,
                    role = UserRole.CLIENTE.name
                )
            )
            RegisterResult.Created
        } catch (_: SQLiteConstraintException) {
            RegisterResult.EmailAlreadyExists
        } catch (_: Exception) {
            RegisterResult.Failed
        }
    }

    suspend fun accountExists(email: String): Boolean =
        userDao.getByEmail(normalizeEmail(email)) != null

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    companion object {
        /** Credenciales solo para pruebas locales/demostración. Cambiarlas antes de publicar. */
        const val DEFAULT_ADMIN_EMAIL = "admin@barbermanager.mx"
        const val DEFAULT_ADMIN_PASSWORD = "Admin123!"
    }
}
