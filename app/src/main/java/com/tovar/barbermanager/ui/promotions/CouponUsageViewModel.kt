package com.tovar.barbermanager.ui.promotions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.PromotionRepository
import com.tovar.barbermanager.data.SessionManager
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.CouponUsage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Cupones aplicados: todos para el administrador, solo los propios para el cliente (RF08.05). */
class CouponUsageViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    val isAdmin: Boolean = session.isAdmin

    val usage: StateFlow<List<CouponUsage>?> =
        PromotionRepository(AppDatabase.getInstance(application))
            .observeUsage(session.userId, isAdmin)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
