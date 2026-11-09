package com.tovar.barbermanager.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun `el hash es distinto por password y verifica correctamente`() {
        val first = Passwords.create("Admin123!")
        val second = Passwords.create("Admin123!")
        assertNotEquals(first.encodedHash, second.encodedHash)

        assertTrue(Passwords.verify("Admin123!", first.encodedSalt, first.encodedHash))
        assertFalse(Passwords.verify("otra", first.encodedSalt, first.encodedHash))
        assertFalse(Passwords.verify("Admin123!", first.encodedSalt, second.encodedHash))
        assertFalse(Passwords.verify("Admin123!", first.encodedSalt, "hash-corrupto"))
    }

    @Test
    fun `el formato del hash es compatible con la app Android`() {
        val hash = Passwords.create("secreta123")
        assertTrue(hash.encodedHash.startsWith("PBKDF2WithHmacSHA256$"))
        assertTrue(hash.encodedSalt.isNotBlank())
    }

    @Test
    fun `el token se emite y se verifica`() {
        val token = Tokens.issue(42L, "secreto")
        assertEquals(42L, Tokens.verify(token, "secreto"))
    }

    @Test
    fun `el token rechaza otra clave, basura y expiracion`() {
        val token = Tokens.issue(42L, "secreto")
        assertNull(Tokens.verify(token, "otra-clave"))
        assertNull(Tokens.verify("no-es-un-token", "secreto"))
        assertNull(Tokens.verify(token.replace("v1.", "v2."), "secreto"))

        val expirado = Tokens.issue(42L, "secreto", nowEpochSeconds = 1_000L)
        assertNull(Tokens.verify(expirado, "secreto", nowEpochSeconds = 1_000L + 15 * 24 * 60 * 60))
    }

    @Test
    fun `el divisor del script SQL respeta comentarios y comillas`() {
        val script = """
            -- Comentario con ; punto y coma
            CREATE TABLE t (c VARCHAR(10) DEFAULT 'a;b');
            INSERT INTO t VALUES ('x');
            SELECT 1;
        """.trimIndent()
        val statements = Db.splitStatements(script)
        assertEquals(3, statements.size)
        assertTrue(statements[0].startsWith("CREATE TABLE"))
        assertTrue(statements[0].contains("'a;b'"))
    }
}
