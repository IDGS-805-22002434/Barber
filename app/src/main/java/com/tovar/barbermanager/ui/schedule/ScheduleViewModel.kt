package com.tovar.barbermanager.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tovar.barbermanager.data.AppointmentRepository
import com.tovar.barbermanager.data.ScheduleRepository
import com.tovar.barbermanager.data.Scheduling
import com.tovar.barbermanager.data.local.AppDatabase
import com.tovar.barbermanager.data.local.BlockedSlot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ScheduleState(
    val loaded: Boolean = false,
    val barberName: String = "",
    /** Siete días: índice 0 = lunes … 6 = domingo. */
    val week: List<Scheduling.DayHours> = emptyList(),
    val blocks: List<BlockedSlot> = emptyList()
)

/** Horario semanal y horarios bloqueados de un barbero (RF04.03). */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val schedules = ScheduleRepository(database.scheduleDao())
    private val appointments = AppointmentRepository(database)

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    private val _invalidRange = Channel<Unit>(Channel.BUFFERED)
    val invalidRange = _invalidRange.receiveAsFlow()

    private var barberId = 0L
    private var started = false

    fun load(id: Long) {
        if (started) return
        started = true
        barberId = id
        viewModelScope.launch {
            val name = database.barberDao().getById(id)?.name.orEmpty()
            combine(schedules.observeWeek(id), schedules.observeBlocks(id)) { week, blocks ->
                ScheduleState(loaded = true, barberName = name, week = week, blocks = blocks)
            }.collect { _state.value = it }
        }
    }

    fun setActive(dayOfWeek: Int, active: Boolean) {
        val hours = hoursOf(dayOfWeek) ?: return
        save(dayOfWeek, hours.copy(active = active))
    }

    fun setStart(dayOfWeek: Int, minutes: Int) {
        val hours = hoursOf(dayOfWeek) ?: return
        update(dayOfWeek, hours.copy(start = minutes))
    }

    fun setEnd(dayOfWeek: Int, minutes: Int) {
        val hours = hoursOf(dayOfWeek) ?: return
        update(dayOfWeek, hours.copy(end = minutes))
    }

    /** Horarios libres que el administrador puede bloquear en esa fecha. */
    suspend fun freeSlots(date: String): List<String> = appointments.freeSlots(barberId, date)

    fun block(date: String, time: String) {
        viewModelScope.launch { schedules.block(barberId, date, time) }
    }

    fun unblock(id: Long) {
        viewModelScope.launch { schedules.unblock(id) }
    }

    private fun hoursOf(dayOfWeek: Int): Scheduling.DayHours? =
        _state.value.week.getOrNull(dayOfWeek - 1)

    private fun update(dayOfWeek: Int, hours: Scheduling.DayHours) {
        if (hours.end - hours.start < Scheduling.SLOT_MINUTES) {
            _invalidRange.trySend(Unit)
            return
        }
        save(dayOfWeek, hours)
    }

    private fun save(dayOfWeek: Int, hours: Scheduling.DayHours) {
        viewModelScope.launch { schedules.saveDay(barberId, dayOfWeek, hours) }
    }
}
