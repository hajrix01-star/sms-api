package com.appenza.smsapi

import java.util.Calendar
import java.util.TimeZone

internal enum class CalendarMonthFilter { THIS_MONTH, LAST_MONTH }

internal object DateRangeCalculator {
    fun calendarMonthWindow(
        filter: CalendarMonthFilter,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            if (filter == CalendarMonthFilter.LAST_MONTH) add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        return from to calendar.timeInMillis
    }

    fun dayBoundary(
        year: Int,
        month: Int,
        day: Int,
        endOfDay: Boolean,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, if (endOfDay) 23 else 0)
        set(Calendar.MINUTE, if (endOfDay) 59 else 0)
        set(Calendar.SECOND, if (endOfDay) 59 else 0)
        set(Calendar.MILLISECOND, if (endOfDay) 999 else 0)
    }.timeInMillis
}
