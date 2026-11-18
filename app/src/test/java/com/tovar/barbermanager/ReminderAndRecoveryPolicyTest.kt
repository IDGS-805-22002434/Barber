package com.tovar.barbermanager

import com.tovar.barbermanager.data.PasswordResetPolicy
import com.tovar.barbermanager.data.notifications.ReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAndRecoveryPolicyTest {

    private val hour = 3_600_000L

    @Test
    fun citaLejana_tieneRecordatorioDeUnDiaYDeUnaHora() {
        val reminders = ReminderPolicy.reminders(startMillis = 48 * hour, nowMillis = 0L)
        assertEquals(listOf(ReminderPolicy.KIND_DAY_BEFORE, ReminderPolicy.KIND_HOUR_BEFORE), reminders.map { it.kind })
        assertEquals(24 * hour, reminders[0].delayMillis)
        assertEquals(47 * hour, reminders[1].delayMillis)
    }

    @Test
    fun citaDeMananaEnLaTarde_soloTieneRecordatorioDeUnaHora() {
        val reminders = ReminderPolicy.reminders(startMillis = 5 * hour, nowMillis = 0L)
        assertEquals(listOf(ReminderPolicy.KIND_HOUR_BEFORE), reminders.map { it.kind })
    }

    @Test
    fun citaEnMenosDeUnaHora_noTieneRecordatorios() {
        assertTrue(ReminderPolicy.reminders(startMillis = hour / 2, nowMillis = 0L).isEmpty())
    }

    @Test
    fun codigo_tieneSeisDigitos() {
        repeat(50) { assertTrue(PasswordResetPolicy.isWellFormed(PasswordResetPolicy.generateCode())) }
        assertFalse(PasswordResetPolicy.isWellFormed("12345"))
        assertFalse(PasswordResetPolicy.isWellFormed("12a456"))
    }

    @Test
    fun codigo_venceALos15Minutos() {
        assertFalse(PasswordResetPolicy.isExpired(0L, PasswordResetPolicy.TTL_MILLIS))
        assertTrue(PasswordResetPolicy.isExpired(0L, PasswordResetPolicy.TTL_MILLIS + 1))
    }

    @Test
    fun hash_dependeDelCodigoYDelCorreo() {
        val base = PasswordResetPolicy.hash("a@x.com", "123456", "sal")
        assertEquals(base, PasswordResetPolicy.hash(" A@X.com ", "123456", "sal"))
        assertNotEquals(base, PasswordResetPolicy.hash("a@x.com", "654321", "sal"))
        assertNotEquals(base, PasswordResetPolicy.hash("b@x.com", "123456", "sal"))
    }
}
