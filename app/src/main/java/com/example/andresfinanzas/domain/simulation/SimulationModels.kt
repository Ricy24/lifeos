package com.example.andresfinanzas.domain.simulation

enum class SimulationEventType {
    EXPENSE, INCOME
}

data class SimulationEvent(
    val eventType: SimulationEventType,
    val amount: Int,
    val accountId: String,
    val description: String? = null
)

// Just a shell representation mirroring the backend perfectly.
