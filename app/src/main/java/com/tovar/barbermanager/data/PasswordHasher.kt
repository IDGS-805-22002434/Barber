package com.tovar.barbermanager.data

import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Deriva hashes de contraseñas con PBKDF2 y un salt aleatorio por cuenta.
 * Guarda el algoritmo junto al hash para conservar compatibilidad entre versiones de Android.
 */
object PasswordHasher {

    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val ITERATIONS = 120_000
    private const val HASH_SEPARATOR = "\u0024"

    data class Hash(val encodedHash: String, val encodedSalt: String)

    fun create(password: String): Hash {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val algorithm = supportedAlgorithm()
        val derived = derive(password, salt, algorithm)
        return Hash(
            encodedHash = algorithm + HASH_SEPARATOR + Base64.encodeToString(derived, Base64.NO_WRAP),
            encodedSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verify(password: String, encodedSalt: String, expectedHash: String): Boolean {
        return try {
            val divider = expectedHash.indexOf(HASH_SEPARATOR)
            if (divider <= 0 || divider == expectedHash.lastIndex) {
                false
            } else {
                val algorithm = expectedHash.substring(0, divider)
                if (algorithm != PBKDF2_SHA256 && algorithm != PBKDF2_SHA1) {
                    false
                } else {
                    val expected = Base64.decode(expectedHash.substring(divider + 1), Base64.NO_WRAP)
                    val salt = Base64.decode(encodedSalt, Base64.NO_WRAP)
                    val actual = derive(password, salt, algorithm)
                    MessageDigest.isEqual(expected, actual)
                }
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: NoSuchAlgorithmException) {
            false
        }
    }

    private fun supportedAlgorithm(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return PBKDF2_SHA256
        return try {
            SecretKeyFactory.getInstance(PBKDF2_SHA256)
            PBKDF2_SHA256
        } catch (_: NoSuchAlgorithmException) {
            PBKDF2_SHA1
        }
    }

    private fun derive(password: String, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private const val PBKDF2_SHA256 = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_SHA1 = "PBKDF2WithHmacSHA1"
}
