package com.appenza.smsapi

import java.util.Calendar
import java.util.TimeZone

internal enum class CalendarMonthFilter { THIS_MONTH, LAST_MONTH }

internal object DateRangeCalculator {
    fun calendarDayWindow(
        dayOffset: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        return dayBoundary(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
            endOfDay = false,
            timeZone = timeZone,
        ) to dayBoundary(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
            endOfDay = true,
            timeZone = timeZone,
        )
    }

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

    fun isSameCalendarDay(
        firstMillis: Long,
        secondMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val first = Calendar.getInstance(timeZone).apply { timeInMillis = firstMillis }
        val second = Calendar.getInstance(timeZone).apply { timeInMillis = secondMillis }
        return first.get(Calendar.ERA) == second.get(Calendar.ERA) &&
            first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
