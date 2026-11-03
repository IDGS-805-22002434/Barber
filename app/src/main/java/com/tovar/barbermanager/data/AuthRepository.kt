package com.tovar.barbermanager.data

import android.database.sqlite.SQLiteConstraintException
import com.tovar.barbermanager.data.local.UserAccount
import com.tovar.barbermanager.data.local.UserDao
import com.tovar.barbermanager.data.local.UserRole
import com.tovar.barbermanager.data.remote.ApiClient
import com.tovar.barbermanager.data.remote.ApiException
import com.tovar.barbermanager.data.remote.LoginRequest
import com.tovar.barbermanager.data.remote.RegistroRequest
import com.tovar.barbermanager.data.remote.UsuarioDto

/** Resultados explícitos para que la interfaz pueda mostrar mensajes claros. */
sealed interface LoginResult {
    /** [token] no nulo cuando la validación fue contra el servidor (API-first). */
    data class Success(val user: UserAccount, val token: String? = null) : LoginResult
    data object InvalidCredentials : LoginResult
    data object InactiveAccount : LoginResult
}

sealed interface RegisterResult {
    data object Created : RegisterResult
    data object EmailAlreadyExists : RegisterResult
    data class Failed(val message: String? = null) : RegisterResult
}

/**
 * Autenticación API-first: valida contra el servidor Ktor y solo recurre a la
 * cuenta local de Room cuando no hay conexión (o cuando el registro previo se
 * hizo sin red).
 */
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
        var serverError: ApiException? = null
        try {
            val response = ApiClient.call {
                ApiClient.service().login(LoginRequest(normalizeEmail(email), password))
            }
            return LoginResult.Success(response.usuario.toLocalAccount(), response.token)
        } catch (error: ApiException) {
            serverError = error
            if (error.code == HTTP_FORBIDDEN) return LoginResult.InactiveAccount
        }

        // Sin conexión, o la cuenta existe solo localmente (registro sin red).
        val local = authenticateLocal(email, password)
        if (local != null) return local

        val error = serverError ?: return LoginResult.InvalidCredentials
        if (error.code != CODE_OFFLINE && error.code != HTTP_UNAUTHORIZED) throw error
        return LoginResult.InvalidCredentials
    }

    /** El registro público siempre crea CLIENTE. Nunca acepta un rol de la pantalla. */
    suspend fun registerClient(
        fullName: String,
        email: String,
        password: String
    ): RegisterResult {
        try {
            ApiClient.call {
                ApiClient.service().registro(
                    RegistroRequest(
                        nombreCompleto = fullName.trim(),
                        correo = normalizeEmail(email),
                        password = password
                    )
                )
            }
            return RegisterResult.Created
        } catch (error: ApiException) {
            when (error.code) {
                HTTP_CONFLICT -> return RegisterResult.EmailAlreadyExists
                CODE_OFFLINE -> { /* sigue con el registro local */ }
                else -> return RegisterResult.Failed(error.message)
            }
        }
        return registerLocal(fullName, email, password)
    }

    private suspend fun authenticateLocal(email: String, password: String): LoginResult? {
        val account = userDao.getByEmail(normalizeEmail(email))
            ?: return null
        if (!account.active) return LoginResult.InactiveAccount
        if (!PasswordHasher.verify(password, account.passwordSalt, account.passwordHash)) {
            return null
        }
        return LoginResult.Success(account)
    }

    private suspend fun registerLocal(
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
            RegisterResult.Failed()
        }
    }

    suspend fun accountExists(email: String): Boolean =
        userDao.getByEmail(normalizeEmail(email)) != null

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun UsuarioDto.toLocalAccount() = UserAccount(
        id = id,
        fullName = nombreCompleto,
        email = correo,
        passwordHash = "",
        passwordSalt = "",
        role = rol,
        active = activo
    )

    companion object {
        /** Credenciales solo para pruebas locales/demostración. Cambiarlas antes de publicar. */
        const val DEFAULT_ADMIN_EMAIL = "admin@barbermanager.mx"
        const val DEFAULT_ADMIN_PASSWORD = "Admin123!"
        private const val CODE_OFFLINE = 0
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_CONFLICT = 409
    }
}
