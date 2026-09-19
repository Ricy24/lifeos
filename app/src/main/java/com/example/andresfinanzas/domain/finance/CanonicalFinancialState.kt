package com.example.andresfinanzas.domain.finance

data class ProtectedCashItem(
    val category: String,
    val sourceId: String?,
    val name: String,
    val amount: Double,
    val isHardConstraint: Boolean,
    val notes: String? = null
)

data class ProtectedCashBreakdown(
    val availableCash: Double,
    val hardObligations: Double,
    val emergencyMinimum: Double,
    val protectedPartnerMoney: Double,
    val protectedGoalMoney: Double,
    val totalProtected: Double,
    val freeCash: Double,
    val items: List<ProtectedCashItem> = emptyList()
)

data class FinancialHealthIndicators(
    val liquidityMonths: Double,
    val obligationCoverage: Double,
    val debtPressure: Double,
    val savingsRate: Double,
    val incomeStability: Double,
    val goalProgress: Double
)

data class FinancialHealthAssessment(
    val score: Double,
    val status: String, // "GREEN", "YELLOW", "RED"
    val indicators: FinancialHealthIndicators,
    val explanations: List<String> = emptyList()
)

data class CanonicalFinancialState(
    val availableCash: Double,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val monthlyObligations: Double,
    val protectedCash: Double,
    val freeCash: Double,
    val goalProgress: Double,
    val debtPressure: Double,
    val obligationCoverage: Double,
    val financialHealthScore: Double,
    val financialHealthStatus: String,
    val protectedCashBreakdown: ProtectedCashBreakdown,
    val healthAssessment: FinancialHealthAssessment,
    val timestamp: Long = System.currentTimeMillis()
)
