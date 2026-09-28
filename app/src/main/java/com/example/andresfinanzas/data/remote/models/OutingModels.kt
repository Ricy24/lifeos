package com.example.andresfinanzas.data.remote.models

data class OutingStopRemote(
    val order: Int,
    val title: String,
    val category: String,
    val estimated_cost: Double,
    val description: String,
    val maps_query: String,
    val maps_url: String,
    val image_url: String? = null,
    val rating: Double? = 4.8,
    val review_count: Int? = 120,
    val highlight_review: String? = null,
    val distance_km: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val price_level: Int? = null
)

data class OutingPlanRequestRemote(
    val outing_type: String,
    val budget: Double?,
    val area_or_city: String,
    val preferences: String?,
    val use_current_location: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius_km: Int? = 5,
    val google_maps_api_key: String? = null
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
