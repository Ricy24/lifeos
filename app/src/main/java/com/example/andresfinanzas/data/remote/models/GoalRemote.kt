package com.example.andresfinanzas.data.remote.models

data class GoalRemote(
    val id: String,
    val name: String,
    val target_amount: Double,
    val current_amount: Double,
    val category: String,
    val description: String?,
    val priority: Int,
    val target_date: String?,
    val status: String,
    val notes: String? = null,
    val tags: List<String>? = null
)
