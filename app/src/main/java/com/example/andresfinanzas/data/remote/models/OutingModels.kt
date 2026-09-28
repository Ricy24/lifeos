package com.example.andresfinanzas.data.remote.models

data class OutingStopRemote(
    val order: Int,
    val title: String,
    val category: String,
    val estimated_cost: Double,
    val description: String,
    val maps_query: String,
    val maps_url: String
)

data class OutingPlanRequestRemote(
    val outing_type: String,
    val budget: Double?,
    val area_or_city: String,
    val preferences: String?
)

data class OutingPlanResponseRemote(
    val title: String,
    val summary: String,
    val total_estimated_cost: Double,
    val safe_budget_available: Double,
    val stops: List<OutingStopRemote>,
    val financial_advice: String
)

data class VisitedPlaceRemote(
    val id: String? = null,
    val user_id: String? = null,
    val name: String,
    val category: String = "Restaurante",
    val address_or_area: String = "",
    val rating: Int = 5,
    val average_cost: Double = 0.0,
    val notes: String? = null,
    val maps_url: String? = null,
    val visited_date: Long? = null,
    val created_at: Long? = null
)
