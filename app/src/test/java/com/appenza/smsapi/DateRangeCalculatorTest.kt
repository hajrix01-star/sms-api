package com.appenza.smsapi

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangeCalculatorTest {
    private val riyadh = TimeZone.getTimeZone("Asia/Riyadh")

    @Test
    fun thisMonthIncludesTheWholeCalendarMonth() {
        val now = millis(2026, Calendar.SEPTEMBER, 10, 13, 15, 30, 123)

        val (from, to) = DateRangeCalculator.calendarMonthWindow(
            CalendarMonthFilter.THIS_MONTH,
            now,
            riyadh,
        )

        assertEquals(millis(2026, Calendar.SEPTEMBER, 1, 0, 0, 0, 0), from)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 30, 23, 59, 59, 999), to)
    }

    @Test
    fun lastMonthCrossesTheYearBoundary() {
        val now = millis(2026, Calendar.JANUARY, 8, 9, 0, 0, 0)

        val (from, to) = DateRangeCalculator.calendarMonthWindow(
            CalendarMonthFilter.LAST_MONTH,
            now,
            riyadh,
        )

        assertEquals(millis(2025, Calendar.DECEMBER, 1, 0, 0, 0, 0), from)
        assertEquals(millis(2025, Calendar.DECEMBER, 31, 23, 59, 59, 999), to)
    }

    @Test
    fun customDayBoundariesCoverTheSelectedDay() {
        assertEquals(
            millis(2026, Calendar.SEPTEMBER, 10, 0, 0, 0, 0),
            DateRangeCalculator.dayBoundary(2026, Calendar.SEPTEMBER, 10, false, riyadh),
        )
        assertEquals(
            millis(2026, Calendar.SEPTEMBER, 10, 23, 59, 59, 999),
            DateRangeCalculator.dayBoundary(2026, Calendar.SEPTEMBER, 10, true, riyadh),
        )
    }

    @Test
    fun todayAndYesterdayCoverExactlyOneCalendarDay() {
        val now = millis(2026, Calendar.MARCH, 15, 18, 45, 12, 321)

        val today = DateRangeCalculator.calendarDayWindow(0, now, riyadh)
        val yesterday = DateRangeCalculator.calendarDayWindow(-1, now, riyadh)

        assertEquals(millis(2026, Calendar.MARCH, 15, 0, 0, 0, 0), today.first)
        assertEquals(millis(2026, Calendar.MARCH, 15, 23, 59, 59, 999), today.second)
        assertEquals(millis(2026, Calendar.MARCH, 14, 0, 0, 0, 0), yesterday.first)
        assertEquals(millis(2026, Calendar.MARCH, 14, 23, 59, 59, 999), yesterday.second)
    }

    @Test
    fun sameCalendarDayIgnoresTheTimeOfDay() {
        val morning = millis(2026, Calendar.MARCH, 15, 0, 0, 0, 0)
        val evening = millis(2026, Calendar.MARCH, 15, 23, 59, 59, 999)
        val nextDay = millis(2026, Calendar.MARCH, 16, 0, 0, 0, 0)

        assertEquals(true, DateRangeCalculator.isSameCalendarDay(morning, evening, riyadh))
        assertEquals(false, DateRangeCalculator.isSameCalendarDay(morning, nextDay, riyadh))
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millisecond: Int,
    ): Long = Calendar.getInstance(riyadh).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, second)
        set(Calendar.MILLISECOND, millisecond)
    }.timeInMillis
}
