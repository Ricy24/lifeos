package com.example.andresfinanzas.domain.finance

import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.local.entities.DebtEntity
import com.example.andresfinanzas.data.local.entities.GoalEntity
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object FinancialTruthEngine {

    private val LIQUID_TYPES = setOf("cash", "bank", "nequi", "daviplata", "savings")

    fun calculateAvailableCash(accounts: List<AccountEntity>): Double {
        var total = 0.0
        for (acc in accounts) {
            if (!acc.includeInTotal) continue
            val type = acc.accountType.lowercase()
            if (type in LIQUID_TYPES && acc.balance > 0) {
                total += acc.balance
            }
        }
        return roundToTwoDecimals(total)
    }

    fun calculateTotalAssets(accounts: List<AccountEntity>): Double {
        var total = 0.0
        for (acc in accounts) {
            if (acc.balance > 0) {
                total += acc.balance
            }
        }
        return roundToTwoDecimals(total)
    }

    fun calculateTotalLiabilities(
        accounts: List<AccountEntity>,
        debts: List<DebtEntity>
    ): Double {
        var total = 0.0
        for (acc in accounts) {
            if (acc.balance < 0) {
                total += kotlin.math.abs(acc.balance)
            }
        }
        for (d in debts) {
            val status = d.status.lowercase()
            val debtType = d.debtType.lowercase()
            if (status in setOf("pending", "partially_paid", "overdue") && debtType == "i_owe") {
                total += d.remainingAmount
            }
        }
        return roundToTwoDecimals(total)
    }

    fun calculateNetWorth(totalAssets: Double, totalLiabilities: Double): Double {
        return roundToTwoDecimals(totalAssets - totalLiabilities)
    }

    fun calculateMonthlyObligations(
        recurringTransactions: List<TransactionEntity>,
        debts: List<DebtEntity>
    ): Pair<Double, List<ProtectedCashItem>> {
        var total = 0.0
        val items = mutableListOf<ProtectedCashItem>()

        for (tx in recurringTransactions) {
            if (!tx.isRecurring || tx.amount <= 0) continue
            val pattern = tx.recurringPattern?.lowercase() ?: "monthly"
            val monthlyAmt = when (pattern) {
                "daily" -> tx.amount * 30.0
                "weekly" -> tx.amount * 4.33
                "yearly" -> tx.amount / 12.0
                else -> tx.amount
            }
            total += monthlyAmt
            items.add(
                ProtectedCashItem(
                    category = "hard_obligation",
                    sourceId = tx.id,
                    name = tx.description ?: tx.category,
                    amount = roundToTwoDecimals(monthlyAmt),
                    isHardConstraint = true,
                    notes = "Recurrente ($pattern)"
                )
            )
        }

        for (d in debts) {
            val status = d.status.lowercase()
            val debtType = d.debtType.lowercase()
            if (status in setOf("pending", "partially_paid", "overdue") && debtType == "i_owe") {
                if (d.remainingAmount > 0) {
                    total += d.remainingAmount
                    val isUrgent = status == "overdue" || d.priority <= 2
                    items.add(
                        ProtectedCashItem(
                            category = "hard_obligation",
                            sourceId = d.id,
                            name = "Deuda: ${d.personOrEntity}",
                            amount = roundToTwoDecimals(d.remainingAmount),
                            isHardConstraint = isUrgent,
                            notes = "Estado: $status, Prioridad: ${d.priority}"
                        )
                    )
                }
            }
        }

        return Pair(roundToTwoDecimals(total), items)
    }

    fun calculateProtectedCash(
        availableCash: Double,
        monthlyObligations: Double,
        obligationItems: List<ProtectedCashItem>,
        emergencyMinimum: Double = 0.0,
        partnerPercentage: Double = 0.0,
        activeGoals: List<GoalEntity> = emptyList()
    ): ProtectedCashBreakdown {
        val items = obligationItems.toMutableList()

        val emergencyProt = max(0.0, emergencyMinimum)
        if (emergencyProt > 0) {
            items.add(
                ProtectedCashItem(
                    category = "emergency_minimum",
                    sourceId = null,
                    name = "Fondo de Emergencia Mínimo",
                    amount = roundToTwoDecimals(emergencyProt),
                    isHardConstraint = true,
                    notes = "Colchón de seguridad obligatorio"
                )
            )
        }

        var partnerProt = 0.0
        if (partnerPercentage > 0 && availableCash > 0) {
            partnerProt = roundToTwoDecimals(availableCash * (partnerPercentage / 100.0))
            items.add(
                ProtectedCashItem(
                    category = "partner_money",
                    sourceId = null,
                    name = "Asignación Pareja (${partnerPercentage}%)",
                    amount = partnerProt,
                    isHardConstraint = false,
                    notes = "Presupuesto asignado a planes en pareja"
                )
            )
        }

        var goalProt = 0.0
        for (g in activeGoals) {
            if (g.status.lowercase() != "active") continue
            val rem = max(0.0, g.targetAmount - g.currentAmount)
            if (rem <= 0) continue
            val cat = g.category.lowercase()
            if (cat == "emergency" || g.priority == 1) {
                val alloc = roundToTwoDecimals(min(rem, max(50000.0, rem * 0.1)))
                goalProt += alloc
                items.add(
                    ProtectedCashItem(
                        category = "goal_allocation",
                        sourceId = g.id,
                        name = "Meta: ${g.name}",
                        amount = alloc,
                        isHardConstraint = (cat == "emergency"),
                        notes = "Categoría: $cat, Prioridad: ${g.priority}"
                    )
                )
            }
        }

        val totalProtected = roundToTwoDecimals(monthlyObligations + emergencyProt + partnerProt + goalProt)
        val freeCash = roundToTwoDecimals(max(0.0, availableCash - totalProtected))

        return ProtectedCashBreakdown(
            availableCash = availableCash,
            hardObligations = monthlyObligations,
            emergencyMinimum = emergencyProt,
            protectedPartnerMoney = partnerProt,
            protectedGoalMoney = roundToTwoDecimals(goalProt),
            totalProtected = totalProtected,
            freeCash = freeCash,
            items = items
        )
    }

    fun calculateHealthIndicators(
        availableCash: Double,
        monthlyIncome: Double,
        monthlyExpenses: Double,
        monthlyObligations: Double,
        monthlyDebtPayments: Double,
        activeGoals: List<GoalEntity> = emptyList()
    ): FinancialHealthIndicators {
        val burnRate = if (monthlyExpenses > 0) monthlyExpenses else (if (monthlyObligations > 0) monthlyObligations else 1.0)
        val liquidityMonths = max(0.0, availableCash / burnRate)

        val obligationCoverage = if (monthlyObligations > 0) {
            max(0.0, monthlyIncome / monthlyObligations)
        } else {
            if (monthlyIncome > 0) 2.0 else 1.0
        }

        val debtPressure = if (monthlyIncome > 0) {
            min(2.0, max(0.0, monthlyDebtPayments / monthlyIncome))
        } else {
            if (monthlyDebtPayments > 0) 1.0 else 0.0
        }

        val savingsRate = if (monthlyIncome > 0) {
            max(-1.0, min(1.0, (monthlyIncome - monthlyExpenses) / monthlyIncome))
        } else 0.0

        val incomeStability = if (monthlyIncome <= 0) {
            0.0
        } else if (monthlyIncome >= monthlyObligations) {
            1.0
        } else {
            max(0.1, monthlyIncome / (if (monthlyObligations > 0) monthlyObligations else 1.0))
        }

        val goalProgress = if (activeGoals.isNotEmpty()) {
            val active = activeGoals.filter { it.status.lowercase() == "active" }
            if (active.isNotEmpty()) {
                active.sumOf { if (it.targetAmount > 0) min(1.0, it.currentAmount / it.targetAmount) else 1.0 } / active.size
            } else 0.0
        } else 0.0

        return FinancialHealthIndicators(
            liquidityMonths = roundToTwoDecimals(liquidityMonths),
            obligationCoverage = roundToTwoDecimals(obligationCoverage),
            debtPressure = roundToTwoDecimals(debtPressure),
            savingsRate = roundToTwoDecimals(savingsRate),
            incomeStability = roundToTwoDecimals(incomeStability),
            goalProgress = roundToTwoDecimals(goalProgress)
        )
    }

    fun evaluateHealthScore(indicators: FinancialHealthIndicators): Double {
        val liquidityScore = min(100.0, (indicators.liquidityMonths / 3.0) * 100.0)

        val covScore = if (indicators.obligationCoverage >= 1.5) {
            100.0
        } else if (indicators.obligationCoverage >= 1.0) {
            50.0 + (indicators.obligationCoverage - 1.0) * 100.0
        } else {
            max(0.0, indicators.obligationCoverage * 50.0)
        }

        val debtScore = if (indicators.debtPressure <= 0.10) {
            100.0
        } else if (indicators.debtPressure <= 0.35) {
            100.0 - ((indicators.debtPressure - 0.10) / 0.25) * 50.0
        } else {
            max(0.0, 50.0 - ((indicators.debtPressure - 0.35) / 0.35) * 50.0)
        }

        val savingsScore = if (indicators.savingsRate >= 0.20) {
            100.0
        } else if (indicators.savingsRate > 0) {
            30.0 + (indicators.savingsRate / 0.20) * 70.0
        } else {
            max(0.0, 30.0 + indicators.savingsRate * 30.0)
        }

        val stabilityScore = min(100.0, max(0.0, indicators.incomeStability * 100.0))
        val goalScore = min(100.0, max(0.0, indicators.goalProgress * 100.0))

        val total = (
            liquidityScore * 0.25 +
            covScore * 0.25 +
            debtScore * 0.20 +
            savingsScore * 0.15 +
            stabilityScore * 0.10 +
            goalScore * 0.05
        )
        return roundToOneDecimal(min(100.0, max(0.0, total)))
    }

    fun evaluateHealthStatus(
        score: Double,
        availableCash: Double,
        hardObligations: Double,
        indicators: FinancialHealthIndicators
    ): Pair<String, List<String>> {
        val explanations = mutableListOf<String>()

        if (hardObligations > 0 && availableCash < hardObligations) {
            val deficit = hardObligations - availableCash
            explanations.add("Efectivo disponible insuficiente para cubrir obligaciones duras. Déficit: $$deficit")
            return Pair("RED", explanations)
        }

        if (indicators.obligationCoverage < 1.0) {
            explanations.add("Ingresos no cubren obligaciones (${indicators.obligationCoverage}x).")
        } else {
            explanations.add("Obligaciones cubiertas (${indicators.obligationCoverage}x).")
        }

        val status = if (indicators.obligationCoverage < 0.8 || score < 45.0) {
            "RED"
        } else if (score >= 70.0 && indicators.obligationCoverage >= 1.2 && indicators.debtPressure <= 0.35) {
            "GREEN"
        } else {
            "YELLOW"
        }

        return Pair(status, explanations)
    }

    fun computeState(
        accounts: List<AccountEntity>,
        debts: List<DebtEntity>,
        recurringTransactions: List<TransactionEntity>,
        monthlyIncome: Double,
        monthlyExpenses: Double,
        monthlyDebtPayments: Double = 0.0,
        emergencyMinimum: Double = 0.0,
        partnerPercentage: Double = 0.0,
        activeGoals: List<GoalEntity> = emptyList()
    ): CanonicalFinancialState {
        val availableCash = calculateAvailableCash(accounts)
        val totalAssets = calculateTotalAssets(accounts)
        val totalLiabilities = calculateTotalLiabilities(accounts, debts)
        val netWorth = calculateNetWorth(totalAssets, totalLiabilities)

        val (monthlyObligations, obligationItems) = calculateMonthlyObligations(
            recurringTransactions = recurringTransactions,
            debts = debts
        )

        val protectedBreakdown = calculateProtectedCash(
            availableCash = availableCash,
            monthlyObligations = monthlyObligations,
            obligationItems = obligationItems,
            emergencyMinimum = emergencyMinimum,
            partnerPercentage = partnerPercentage,
            activeGoals = activeGoals
        )

        val indicators = calculateHealthIndicators(
            availableCash = availableCash,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses,
            monthlyObligations = monthlyObligations,
            monthlyDebtPayments = monthlyDebtPayments,
            activeGoals = activeGoals
        )

        val score = evaluateHealthScore(indicators)
        val (status, explanations) = evaluateHealthStatus(
            score = score,
            availableCash = availableCash,
            hardObligations = monthlyObligations,
            indicators = indicators
        )

        val assessment = FinancialHealthAssessment(
            score = score,
            status = status,
            indicators = indicators,
            explanations = explanations
        )

        return CanonicalFinancialState(
            availableCash = availableCash,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = netWorth,
            monthlyIncome = roundToTwoDecimals(monthlyIncome),
            monthlyExpenses = roundToTwoDecimals(monthlyExpenses),
            monthlyObligations = monthlyObligations,
            protectedCash = protectedBreakdown.totalProtected,
            freeCash = protectedBreakdown.freeCash,
            goalProgress = indicators.goalProgress,
            debtPressure = indicators.debtPressure,
            obligationCoverage = indicators.obligationCoverage,
            financialHealthScore = score,
            financialHealthStatus = status,
            protectedCashBreakdown = protectedBreakdown,
            healthAssessment = assessment
        )
    }

    private fun roundToTwoDecimals(v: Double): Double = round(v * 100.0) / 100.0
    private fun roundToOneDecimal(v: Double): Double = round(v * 10.0) / 10.0
}
