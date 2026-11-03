package com.tovar.barbermanager.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.remote.ApiClient
import com.tovar.barbermanager.data.remote.ApiException
import com.tovar.barbermanager.data.remote.HistorialItemDto
import com.tovar.barbermanager.data.remote.PasswordUpdateRequest
import com.tovar.barbermanager.data.remote.PerfilUpdateRequest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val nombre: String = "",
    val telefono: String = "",
    val correo: String = "",
    val servidorUrl: String = "",
    val isServerSession: Boolean = false,
    val saving: Boolean = false,
    val historial: List<HistorialItemDto> = emptyList(),
    val historialLoading: Boolean = true,
    val historialEmpty: Boolean = false,
    val error: String? = null
)

/** Perfil del usuario: datos personales, URL del servidor, contraseña e historial. */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val session = SessionManager(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        _uiState.update {
            it.copy(
                nombre = session.userName.orEmpty(),
                correo = session.userEmail.orEmpty(),
                servidorUrl = session.serverUrl,
                isServerSession = session.isServerSession
            )
        }
        load()
    }

    fun load() {
        session.restoreApiSession()
        val servidor = session.serverUrl
        if (!session.isServerSession) {
            _uiState.update {
                it.copy(
                    loading = false,
                    historialLoading = false,
                    historialEmpty = true,
                    historial = emptyList(),
                    correo = session.userEmail.orEmpty(),
                    servidorUrl = servidor,
                    isServerSession = false,
                    error = null
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                loading = true,
                historialLoading = true,
                isServerSession = true,
                servidorUrl = servidor,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val perfil = ApiClient.call { ApiClient.service().perfil() }
                _uiState.update {
                    it.copy(
                        loading = false,
                        nombre = perfil.nombreCompleto,
                        telefono = perfil.telefono.orEmpty(),
                        correo = perfil.correo
                    )
                }
            } catch (error: ApiException) {
                _uiState.update { it.copy(loading = false, historialLoading = false) }
                if (error.code != 0) {
                    _uiState.update { it.copy(error = error.message) }
                } else {
                    _events.tryEmit(error.message)
                }
            }
            cargarHistorial()
        }
    }

    private suspend fun cargarHistorial() {
        try {
            val historial = ApiClient.call { ApiClient.service().historial() }
            _uiState.update {
                it.copy(
                    historialLoading = false,
                    historial = historial,
                    historialEmpty = historial.isEmpty()
                )
            }
        } catch (error: ApiException) {
            _uiState.update { it.copy(historialLoading = false, historialEmpty = true) }
        }
    }

    fun onNombreChanged(valor: String) = _uiState.update { it.copy(nombre = valor) }
    fun onTelefonoChanged(valor: String) = _uiState.update { it.copy(telefono = valor) }
    fun onServidorChanged(valor: String) = _uiState.update { it.copy(servidorUrl = valor) }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    fun guardar() {
        val state = _uiState.value
        val context = getApplication<Application>()
        if (state.nombre.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.error_register_name)) }
            return
        }
        val urlNueva = ApiClient.normalizeBaseUrl(state.servidorUrl)
        if (urlNueva == null) {
            _events.tryEmit(context.getString(R.string.profile_server_invalid))
            return
        }
        if (state.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val cambioUrl = urlNueva != session.serverUrl
            val nombre = state.nombre.trim()
            val telefono = state.telefono.trim().ifBlank { null }

            // Una sesión local no tiene perfil en el servidor: se guarda el nombre
            // (y la URL, si cambió) en el dispositivo sin lanzar una petición
            // condenada a 401.
            if (!session.isServerSession) {
                session.updateLocalName(nombre)
                if (cambioUrl) session.saveServerUrl(urlNueva)
                _uiState.update {
                    it.copy(saving = false, nombre = nombre, servidorUrl = urlNueva)
                }
                _events.tryEmit(
                    context.getString(
                        if (cambioUrl) R.string.profile_saved_server_change else R.string.profile_saved
                    )
                )
                return@launch
            }

            try {
                val perfil = ApiClient.call {
                    ApiClient.service().actualizarPerfil(
                        PerfilUpdateRequest(nombreCompleto = nombre, telefono = telefono)
                    )
                }
                session.updateLocalName(perfil.nombreCompleto)
                if (cambioUrl) {
                    // La URL se usa en el próximo inicio de sesión; no se cambia
                    // el servidor de la sesión actual ni su token.
                    session.saveServerUrl(urlNueva)
                }
                _uiState.update {
                    it.copy(
                        saving = false,
                        nombre = perfil.nombreCompleto,
                        servidorUrl = if (cambioUrl) urlNueva else it.servidorUrl
                    )
                }
                _events.tryEmit(
                    context.getString(
                        if (cambioUrl) R.string.profile_saved_server_change else R.string.profile_saved
                    )
                )
            } catch (error: ApiException) {
                _uiState.update { it.copy(saving = false) }
                _events.tryEmit(error.message)
            }
        }
    }

    fun cambiarPassword(actual: String, nueva: String, confirmacion: String): Boolean {
        val context = getApplication<Application>()
        if (!session.isServerSession) {
            _events.tryEmit(context.getString(R.string.profile_password_server_only))
            return true
        }
        val error = when {
            actual.isBlank() -> context.getString(R.string.error_password_empty)
            nueva.length < 6 -> context.getString(R.string.error_password_short, 6)
            nueva != confirmacion -> context.getString(R.string.error_password_mismatch)
            else -> null
        }
        if (error != null) {
            _events.tryEmit(error)
            return false
        }
        viewModelScope.launch {
            try {
                ApiClient.call {
                    ApiClient.service().cambiarContrasena(
                        PasswordUpdateRequest(actualPassword = actual, nuevaPassword = nueva)
                    )
                }
                _events.tryEmit(context.getString(R.string.password_updated))
            } catch (e: ApiException) {
                _events.tryEmit(
                    if (e.code == 403 || e.code == 401) context.getString(R.string.error_password_wrong)
                    else e.message
                )
            }
        }
        return true
    }
}
