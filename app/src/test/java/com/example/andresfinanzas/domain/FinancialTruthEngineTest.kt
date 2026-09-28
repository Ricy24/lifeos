package com.example.andresfinanzas.domain

import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.local.entities.DebtEntity
import com.example.andresfinanzas.data.local.entities.GoalEntity
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.domain.finance.FinancialTruthEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialTruthEngineTest {

    @Test
    fun testNormalPositiveBalanceAndNetWorth() {
        val accounts = listOf(
            AccountEntity("1", "Cash", "cash", 150000.0, "COP", null, null, null, true),
            AccountEntity("2", "Bank", "bank", 850000.0, "COP", null, null, null, true),
            AccountEntity("3", "Nequi", "nequi", 200000.0, "COP", null, null, null, true)
        )
        val available = FinancialTruthEngine.calculateAvailableCash(accounts)
        assertEquals(1200000.0, available, 0.01)

        val assets = FinancialTruthEngine.calculateTotalAssets(accounts)
        assertEquals(1200000.0, assets, 0.01)

        val netWorth = FinancialTruthEngine.calculateNetWorth(assets, 0.0)
        assertEquals(1200000.0, netWorth, 0.01)
    }

    @Test
    fun testNegativeBalanceAndLiabilities() {
        val accounts = listOf(
            AccountEntity("1", "Bank", "bank", -50000.0, "COP", null, null, null, true),
            AccountEntity("2", "Credit", "credit_card", -250000.0, "COP", null, null, null, true),
            AccountEntity("3", "Cash", "cash", 100000.0, "COP", null, null, null, true)
        )
        val debts = listOf(
            DebtEntity(
                id = "d1",
                personOrEntity = "Acreedor",
                debtType = "i_owe",
                originalAmount = 300000.0,
                remainingAmount = 300000.0,
                interestRate = 0.0,
                debtDate = 0L,
                dueDate = null,
                priority = 1,
                status = "pending",
                description = null,
                notes = null
            )
        )
        val available = FinancialTruthEngine.calculateAvailableCash(accounts)
        assertEquals(100000.0, available, 0.01)

        val liabilities = FinancialTruthEngine.calculateTotalLiabilities(accounts, debts)
        assertEquals(600000.0, liabilities, 0.01)

        val netWorth = FinancialTruthEngine.calculateNetWorth(100000.0, liabilities)
        assertEquals(-500000.0, netWorth, 0.01)
    }

    @Test
    fun testProtectedAndFreeCash() {
        val availableCash = 500000.0
        val obligations = 200000.0
        val emergencyMin = 100000.0
        val partnerPct = 10.0 // 50,000
        val goals = listOf(
            GoalEntity(
                id = "g1",
                name = "Emergency Fund",
                targetAmount = 1000000.0,
                currentAmount = 200000.0,
                category = "emergency",
                description = null,
                priority = 1,
                targetDate = null,
                status = "active"
            )
        ) // 80,000 allocation

        val breakdown = FinancialTruthEngine.calculateProtectedCash(
            availableCash = availableCash,
            monthlyObligations = obligations,
            obligationItems = emptyList(),
            emergencyMinimum = emergencyMin,
            partnerPercentage = partnerPct,
            activeGoals = goals
        )

        assertEquals(430000.0, breakdown.totalProtected, 0.01)
        assertEquals(70000.0, breakdown.freeCash, 0.01)
    }

    @Test
    fun testRedStateWhenDeficitForObligations() {
        val indicators = FinancialTruthEngine.calculateHealthIndicators(
            availableCash = 50000.0,
            monthlyIncome = 120000.0,
            monthlyExpenses = 90000.0,
            monthlyObligations = 100000.0,
            monthlyDebtPayments = 0.0
        )
        val score = FinancialTruthEngine.evaluateHealthScore(indicators)
        val (status, explanations) = FinancialTruthEngine.evaluateHealthStatus(
            score = score,
            availableCash = 50000.0,
            hardObligations = 100000.0,
            indicators = indicators
        )
        assertEquals("RED", status)
        assertTrue(explanations.isNotEmpty())
    }
}
