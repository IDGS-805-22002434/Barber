package com.tovar.barbermanager.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hash de contraseñas con PBKDF2, compatible con el formato que usa la app
 * Android (PasswordHasher): `passwordHash = "PBKDF2WithHmacSHA256$<b64>"` y
 * `passwordSalt = <b64>` en columnas separadas.
 */
object Passwords {

    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val ITERATIONS = 120_000
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SEPARATOR = "$"

    data class Hash(val encodedHash: String, val encodedSalt: String)

    fun create(password: String): Hash {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt)
        return Hash(
            encodedHash = ALGORITHM + SEPARATOR + Base64.getEncoder().encodeToString(derived),
            encodedSalt = Base64.getEncoder().encodeToString(salt)
        )
    }

    fun verify(password: String, encodedSalt: String, expectedHash: String): Boolean {
        return try {
            val divider = expectedHash.indexOf(SEPARATOR)
            if (divider <= 0) return false
            val algorithm = expectedHash.substring(0, divider)
            if (algorithm != ALGORITHM) return false
            val expected = Base64.getDecoder().decode(expectedHash.substring(divider + 1))
            val salt = Base64.getDecoder().decode(encodedSalt)
            val actual = derive(password, salt, algorithm)
            MessageDigest.isEqual(expected, actual)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, algorithm: String = ALGORITHM): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * Tokens firmados con HMAC-SHA256 en formato `v1.<usuarioId>.<expiraEn>.<firma>`.
 * El servidor solo valida la firma y la expiración; no hay estado en memoria.
 */
object Tokens {

    private const val VERSION = "v1"
    private const val TTL_SECONDS = 14L * 24 * 60 * 60 // 14 días

    fun issue(userId: Long, secret: String, nowEpochSeconds: Long = now()): String {
        val expires = nowEpochSeconds + TTL_SECONDS
        val payload = "$VERSION.$userId.$expires"
        return "$payload.${sign(payload, secret)}"
    }

    /** Devuelve el id del usuario o null si el token es inválido o expiró. */
    fun verify(token: String, secret: String, nowEpochSeconds: Long = now()): Long? {
        val parts = token.split(".")
        if (parts.size != 4 || parts[0] != VERSION) return null
        val userId = parts[1].toLongOrNull() ?: return null
        val expires = parts[2].toLongOrNull() ?: return null
        if (expires < nowEpochSeconds) return null
        val payload = "${parts[0]}.${parts[1]}.${parts[2]}"
        val expected = sign(payload, secret)
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[3].toByteArray())) return null
        return userId
    }

    private fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val raw = mac.doFinal(payload.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    private fun now(): Long = System.currentTimeMillis() / 1000
}
