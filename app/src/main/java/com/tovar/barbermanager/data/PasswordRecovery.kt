package com.tovar.barbermanager.data

import android.content.Context
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.notifications.NotificationHelper
import com.tovar.barbermanager.data.remote.ApiClient
import com.tovar.barbermanager.data.remote.ApiException
import com.tovar.barbermanager.data.remote.RecuperarRequest
import com.tovar.barbermanager.data.remote.RestablecerRequest

sealed interface RecoveryRequest {
    /** El servidor envió el código al correo registrado. */
    data object SentByServer : RecoveryRequest

    /**
     * Sin conexión: el código se generó en el dispositivo. [code] solo viene cuando no se pudo
     * mostrar como notificación (permiso denegado) para que la persona pueda continuar.
     */
    data class SentLocally(val code: String?) : RecoveryRequest

    data object AccountNotFound : RecoveryRequest
    data class Failed(val message: String) : RecoveryRequest
}

sealed interface ResetResult {
    data object Success : ResetResult
    data object InvalidCode : ResetResult
    data object Expired : ResetResult
    data object TooManyAttempts : ResetResult
    data class Failed(val message: String) : ResetResult
}

/**
 * Recuperación de contraseña por correo (RF01.03): primero contra el servidor y, si no hay
 * conexión, con un código temporal para las cuentas que existen solo en el dispositivo.
 */
class PasswordRecovery(
    context: Context,
    private val auth: AuthRepository
) {
    private val appContext = context.applicationContext
    private val store = PasswordResetStore(appContext)

    suspend fun request(email: String): RecoveryRequest {
        val normalized = email.trim().lowercase()
        try {
            ApiClient.call { ApiClient.service().recuperarPassword(RecuperarRequest(normalized)) }
            return RecoveryRequest.SentByServer
        } catch (error: ApiException) {
            if (error.code != CODE_OFFLINE) return RecoveryRequest.Failed(error.message)
        }
        if (!auth.accountExists(normalized)) return RecoveryRequest.AccountNotFound
        val code = store.issue(normalized)
        val shown = NotificationHelper.show(
            appContext,
            RESET_NOTIFICATION_ID,
            appContext.getString(R.string.recovery_notif_title),
            appContext.getString(R.string.recovery_notif_message, code)
        )
        return RecoveryRequest.SentLocally(if (shown) null else code)
    }

    suspend fun reset(email: String, code: String, newPassword: String): ResetResult {
        val normalized = email.trim().lowercase()
        if (store.hasPending(normalized)) {
            return when (store.check(normalized, code)) {
                ResetCodeCheck.VALID -> {
                    if (auth.updateLocalPassword(normalized, newPassword)) {
                        store.clear(normalized)
                        ResetResult.Success
                    } else {
                        ResetResult.Failed(appContext.getString(R.string.error_database))
                    }
                }
                ResetCodeCheck.WRONG, ResetCodeCheck.NONE -> ResetResult.InvalidCode
                ResetCodeCheck.EXPIRED -> ResetResult.Expired
                ResetCodeCheck.TOO_MANY_ATTEMPTS -> ResetResult.TooManyAttempts
            }
        }
        return try {
            ApiClient.call {
                ApiClient.service().restablecerPassword(
                    RestablecerRequest(normalized, code.trim(), newPassword)
                )
            }
            ResetResult.Success
        } catch (error: ApiException) {
            ResetResult.Failed(error.message)
        }
    }

    private companion object {
        const val CODE_OFFLINE = 0
        const val RESET_NOTIFICATION_ID = 7001
    }
}
