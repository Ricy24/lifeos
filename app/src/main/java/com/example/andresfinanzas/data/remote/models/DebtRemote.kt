package com.example.andresfinanzas.data.remote.models

data class DebtRemote(
    val id: String,
    val person_or_entity: String,
    val debt_type: String,
    val original_amount: Double,
    val remaining_amount: Double,
    val interest_rate: Double,
    val debt_date: String,
    val due_date: String?,
    val priority: Int,
    val status: String,
    val description: String?,
    val notes: String?
)
