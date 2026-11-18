package com.tovar.barbermanager.ui.promotions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.PromotionForm
import com.tovar.barbermanager.data.PromotionItem
import com.tovar.barbermanager.data.PromotionRepository
import com.tovar.barbermanager.data.PromotionSaveResult
import com.tovar.barbermanager.data.local.AppDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class PromotionFormState(val loaded: Boolean = false, val item: PromotionItem? = null)

class PromotionFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PromotionRepository(AppDatabase.getInstance(application))

    private val _formState = MutableStateFlow(PromotionFormState())
    val formState: StateFlow<PromotionFormState> = _formState

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private val _codeTaken = Channel<Unit>(Channel.BUFFERED)
    val codeTaken = _codeTaken.receiveAsFlow()

    fun load(promotionId: Long) {
        viewModelScope.launch {
            val item = if (promotionId == 0L) null else repository.getItem(promotionId)
            _formState.value = PromotionFormState(loaded = true, item = item)
        }
    }

    fun save(form: PromotionForm) {
        viewModelScope.launch {
            when (repository.save(form)) {
                PromotionSaveResult.Saved -> _saved.send(Unit)
                PromotionSaveResult.CodeTaken -> _codeTaken.send(Unit)
            }
        }
    }
}
