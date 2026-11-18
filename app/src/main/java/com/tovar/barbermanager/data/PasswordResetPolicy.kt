package com.tovar.barbermanager.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** Reglas del código temporal de recuperación de contraseña (RF01.03). Lógica pura. */
object PasswordResetPolicy {

    const val CODE_LENGTH = 6
    const val TTL_MILLIS = 15 * 60 * 1000L
    const val MAX_ATTEMPTS = 5

    fun generateCode(random: SecureRandom = SecureRandom()): String =
        String.format(Locale.US, "%0${CODE_LENGTH}d", random.nextInt(1_000_000))

    fun isWellFormed(code: String): Boolean =
        code.length == CODE_LENGTH && code.all { it.isDigit() }

    fun isExpired(issuedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - issuedAtMillis > TTL_MILLIS

    /** Solo se guarda el hash del código, nunca el código en claro. */
    fun hash(email: String, code: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:${email.trim().lowercase()}:$code".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
