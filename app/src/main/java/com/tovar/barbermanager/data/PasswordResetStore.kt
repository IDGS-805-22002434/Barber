package com.tovar.barbermanager.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** Resultado de comprobar un código de recuperación guardado en el dispositivo. */
enum class ResetCodeCheck { VALID, WRONG, EXPIRED, TOO_MANY_ATTEMPTS, NONE }

/**
 * Código de recuperación para cuentas locales (sin conexión con el servidor).
 * Guarda solo el hash, vence a los 15 minutos, se puede usar una vez y permite
 * un máximo de intentos.
 */
class PasswordResetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun issue(email: String, nowMillis: Long = System.currentTimeMillis()): String {
        val key = keyOf(email)
        val code = PasswordResetPolicy.generateCode()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.NO_WRAP) }
        prefs.edit()
            .putString("$key.hash", PasswordResetPolicy.hash(email, code, salt))
            .putString("$key.salt", salt)
            .putLong("$key.issued", nowMillis)
            .putInt("$key.attempts", 0)
            .apply()
        return code
    }

    fun hasPending(email: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val key = keyOf(email)
        val issued = prefs.getLong("$key.issued", 0L)
        return issued != 0L && !PasswordResetPolicy.isExpired(issued, nowMillis)
    }

    fun check(email: String, code: String, nowMillis: Long = System.currentTimeMillis()): ResetCodeCheck {
        val key = keyOf(email)
        val storedHash = prefs.getString("$key.hash", null)
        val salt = prefs.getString("$key.salt", null)
        val issued = prefs.getLong("$key.issued", 0L)
        if (storedHash == null || salt == null || issued == 0L) return ResetCodeCheck.NONE
        if (PasswordResetPolicy.isExpired(issued, nowMillis)) {
            clear(email)
            return ResetCodeCheck.EXPIRED
        }
        val attempts = prefs.getInt("$key.attempts", 0)
        if (attempts >= PasswordResetPolicy.MAX_ATTEMPTS) return ResetCodeCheck.TOO_MANY_ATTEMPTS

        val candidate = PasswordResetPolicy.hash(email, code.trim(), salt)
        val matches = MessageDigest.isEqual(candidate.toByteArray(), storedHash.toByteArray())
        if (!matches) {
            prefs.edit().putInt("$key.attempts", attempts + 1).apply()
            return ResetCodeCheck.WRONG
        }
        return ResetCodeCheck.VALID
    }

    fun clear(email: String) {
        val key = keyOf(email)
        prefs.edit()
            .remove("$key.hash")
            .remove("$key.salt")
            .remove("$key.issued")
            .remove("$key.attempts")
            .apply()
    }

    private fun keyOf(email: String) = "reset." + email.trim().lowercase()

    private companion object {
        const val PREFS_NAME = "barbermanager_password_reset"
    }
}
