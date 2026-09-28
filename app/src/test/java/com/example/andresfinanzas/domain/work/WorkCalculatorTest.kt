package com.example.andresfinanzas.domain.work

import com.example.andresfinanzas.data.local.entities.WorkSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class WorkCalculatorTest {

    private fun makeSession(income: Int, duration: Int?, start: Date, end: Date? = null): WorkSessionEntity {
        return WorkSessionEntity(
            userId = "test",
            startTime = start,
            endTime = end,
            durationMinutes = duration,
            income = income,
            activityType = "dev"
        )
    }

    // 1. Arithmetic Tests
    @Test
    fun testCalculateSessionIncomePerHour() {
        assertEquals(10000, WorkCalculator.calculateSessionIncomePerHour(10000, 60))
        assertEquals(20000, WorkCalculator.calculateSessionIncomePerHour(10000, 30))
        assertEquals(6667, WorkCalculator.calculateSessionIncomePerHour(10000, 90))
        assertEquals(5000, WorkCalculator.calculateSessionIncomePerHour(10000, 120))
        assertNull(WorkCalculator.calculateSessionIncomePerHour(10000, 0))
        assertNull(WorkCalculator.calculateSessionIncomePerHour(10000, null))
        assertEquals(0, WorkCalculator.calculateSessionIncomePerHour(0, 60))
        assertEquals(16667, WorkCalculator.calculateSessionIncomePerHour(25000, 90))
        assertEquals(25000, WorkCalculator.calculateSessionIncomePerHour(50000, 120))
        assertEquals(20000, WorkCalculator.calculateSessionIncomePerHour(10000, 30))
    }

    // 2. Aggregation Tests
    @Test
    fun testAggregateEqualDuration() {
        val s1 = makeSession(10000, 60, Date(), Date())
        val s2 = makeSession(20000, 60, Date(), Date())
        
        val metrics = WorkCalculator.aggregateMetrics(listOf(s1, s2))
        assertEquals(30000, metrics.totalIncome)
        assertEquals(120, metrics.totalDurationMinutes)
        assertEquals(15000, metrics.incomePerHour)
    }

    @Test
    fun testAggregateUnequalDuration() {
        val s1 = makeSession(10000, 60, Date(), Date())
        val s2 = makeSession(10000, 600, Date(), Date())
        
        val metrics = WorkCalculator.aggregateMetrics(listOf(s1, s2))
        assertEquals(20000, metrics.totalIncome)
        assertEquals(660, metrics.totalDurationMinutes)
        assertEquals(1818, metrics.incomePerHour)
    }

    @Test
    fun testAggregateActiveAndCompleted() {
        val s1 = makeSession(10000, 60, Date(), Date())
        val s2 = makeSession(0, null, Date(), null)
        
        val metrics = WorkCalculator.aggregateMetrics(listOf(s1, s2))
        assertEquals(1, metrics.activeSessionsCount)
        assertEquals(1, metrics.completedSessionsCount)
        assertEquals(10000, metrics.incomePerHour)
    }

    // 3. Target Math
    @Test
    fun testRequiredWork() {
        val metrics = WorkMetrics(totalIncome = 10000, totalDurationMinutes = 60, activeSessionsCount = 0, completedSessionsCount = 1)
        val req = WorkCalculator.calculateRequiredWork(50000, metrics)
        assertEquals(300, req)
    }

    @Test
    fun testRequiredWorkInsufficientData() {
        val metrics = WorkMetrics(totalIncome = 0, totalDurationMinutes = 0, activeSessionsCount = 0, completedSessionsCount = 0)
        val req = WorkCalculator.calculateRequiredWork(50000, metrics)
        assertNull(req)
    }

    // 4. Boundaries
    @Test
    fun testBoundaries() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val now = cal.time

        val dayBoundary = WorkCalculator.getCurrentDayBoundary(now)
        val expectedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.SEPTEMBER, 16, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals(expectedDay, dayBoundary)

        val weekBoundary = WorkCalculator.getCurrentWeekBoundary(now)
        val expectedWeek = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.SEPTEMBER, 14, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals(expectedWeek, weekBoundary)

        val monthBoundary = WorkCalculator.getCurrentMonthBoundary(now)
        val expectedMonth = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals(expectedMonth, monthBoundary)
        
        val r7 = WorkCalculator.getRolling7DaysBoundary(now)
        val expectedR7 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.SEPTEMBER, 9, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals(expectedR7, r7)
        
        val r30 = WorkCalculator.getRolling30DaysBoundary(now)
        val expectedR30 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.AUGUST, 17, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        assertEquals(expectedR30, r30)
    }
}
