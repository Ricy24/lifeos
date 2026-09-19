package com.example.andresfinanzas.domain.work

import com.example.andresfinanzas.data.local.entities.WorkSessionEntity
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

data class WorkMetrics(
    val totalIncome: Int,
    val totalDurationMinutes: Int,
    val activeSessionsCount: Int,
    val completedSessionsCount: Int
) {
    val incomePerHour: Int?
        get() {
            if (totalDurationMinutes <= 0) return null
            val numerator = totalIncome.toLong() * 60L
            return ((numerator + (totalDurationMinutes / 2)) / totalDurationMinutes).toInt()
        }
}

object WorkCalculator {

    fun calculateSessionIncomePerHour(income: Int, durationMinutes: Int?): Int? {
        if (durationMinutes == null || durationMinutes <= 0) return null
        val numerator = income.toLong() * 60L
        return ((numerator + (durationMinutes / 2)) / durationMinutes).toInt()
    }

    fun aggregateMetrics(sessions: List<WorkSessionEntity>): WorkMetrics {
        var totalIncome = 0
        var totalDuration = 0
        var activeCount = 0
        var completedCount = 0

        for (session in sessions) {
            if (session.endTime == null) {
                activeCount++
            } else {
                completedCount++
                totalIncome += session.income
                if (session.durationMinutes != null) {
                    totalDuration += session.durationMinutes
                }
            }
        }

        return WorkMetrics(
            totalIncome = totalIncome,
            totalDurationMinutes = totalDuration,
            activeSessionsCount = activeCount,
            completedSessionsCount = completedCount
        )
    }

    fun calculateRequiredWork(targetIncome: Int, metrics: WorkMetrics): Int? {
        val rate = metrics.incomePerHour
        if (rate == null || rate <= 0) return null
        
        val numerator = targetIncome.toLong() * 60L
        return ((numerator + (rate / 2)) / rate).toInt()
    }

    private fun getCalendar(date: Date): Calendar {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = date
        return cal
    }

    fun getCurrentDayBoundary(now: Date): Date {
        val cal = getCalendar(now)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun getCurrentWeekBoundary(now: Date): Date {
        val cal = getCalendar(now)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        // Android Calendar uses Sunday=1, Monday=2
        // We want Monday as start of week like Python (where Monday=0)
        var dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -6)
        } else {
            cal.add(Calendar.DAY_OF_MONTH, -(dayOfWeek - Calendar.MONDAY))
        }
        return cal.time
    }

    fun getCurrentMonthBoundary(now: Date): Date {
        val cal = getCalendar(now)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun getRolling7DaysBoundary(now: Date): Date {
        val cal = getCalendar(now)
        cal.add(Calendar.DAY_OF_MONTH, -7)
        return cal.time
    }

    fun getRolling30DaysBoundary(now: Date): Date {
        val cal = getCalendar(now)
        cal.add(Calendar.DAY_OF_MONTH, -30)
        return cal.time
    }
}
