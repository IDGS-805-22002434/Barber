package com.tovar.barbermanager.ui.services

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.ServiceRepository
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.Service
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Carga y guarda un servicio (alta y edición del administrador, RF02.03).
 * El guardado vive aquí (viewModelScope) para que no dependa del ciclo de vida
 * de la pantalla; el resultado se avisa con un evento de una sola vez.
 */
class ServiceFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceRepository(AppDatabase.getInstance(application).serviceDao())

    private val _formState = MutableStateFlow(FormState())
    val formState: StateFlow<FormState> = _formState.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    /** Se carga una sola vez; volver desde segundo plano no borra lo escrito. */
    fun load(id: Long) {
        if (_formState.value.loaded) return
        viewModelScope.launch {
            _formState.value = FormState(loaded = true, service = repository.getById(id))
        }
    }

    fun save(service: Service) {
        viewModelScope.launch {
            repository.save(service)
            _saved.send(Unit)
        }
    }

    data class FormState(
        val loaded: Boolean = false,
        val service: Service? = null
    )

    companion object {
        const val SERVICE_ID_NEW = 0L
    }
}
