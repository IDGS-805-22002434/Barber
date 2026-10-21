package com.tovar.barbermanager.ui.util

import androidx.annotation.StringRes
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.Scheduling


object DatePickers {

    fun create(@StringRes title: Int, selectedIso: String?): MaterialDatePicker<Long> {
        val today = Dates.today()
        val todayUtc = Dates.utcMidnightMillis(today)
        val lastUtc = Dates.utcMidnightMillis(
            Dates.plusDays(today, Scheduling.BOOKING_HORIZON_DAYS)
        )
        val validators = listOf<CalendarConstraints.DateValidator>(
            DateValidatorPointForward.from(todayUtc),
            DateValidatorPointBackward.before(lastUtc)
        )
        val constraints = CalendarConstraints.Builder()
            .setStart(todayUtc)
            .setEnd(lastUtc)
            .setValidator(CompositeDateValidator.allOf(validators))
            .build()
        return MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setSelection(selectedIso?.let { Dates.utcMidnightMillis(it) } ?: todayUtc)
            .setCalendarConstraints(constraints)
            .build()
    }
}
