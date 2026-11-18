package com.tovar.barbermanager.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.AppNotification
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    val items: List<AppNotification> = emptyList()
)

/** Cliente: sus avisos. Administrador: registro de todas las notificaciones y su estado (RF07.03). */
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).notificationDao()
    private val session = SessionManager(application)
    private val email = session.currentEmail.orEmpty()

    val isAdmin: Boolean = session.isAdmin

    val uiState: StateFlow<NotificationsUiState> =
        (if (isAdmin) dao.observeAll() else dao.observeForUser(email))
            .map { NotificationsUiState(loading = false, items = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun markAllRead() {
        viewModelScope.launch { dao.markAllRead(email) }
    }

    fun markRead(notification: AppNotification) {
        if (notification.isRead) return
        viewModelScope.launch { dao.markRead(notification.id) }
    }
}
