package com.example.andresfinanzas.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

data class MotorcycleRemote(
    val id: String,
    val user_id: String,
    val name: String,
    val model: String,
    val current_mileage: Int,
    val oil_change_interval: Int,
    val last_oil_change_mileage: Int,
    val front_tire_mileage: Int,
    val front_tire_life_km: Int,
    val rear_tire_mileage: Int,
    val rear_tire_life_km: Int,
    val brake_pads_mileage: Int,
    val brake_pads_life_km: Int,
    val chain_maintenance_mileage: Int,
    val chain_maintenance_interval: Int,
    val soat_expiry_date: Long,
    val techno_expiry_date: Long,
    val cost_per_km: Double,
    val last_updated: Long
)

data class MotorcycleUpdateRemote(
    val name: String? = null,
    val model: String? = null,
    val current_mileage: Int? = null,
    val oil_change_interval: Int? = null,
    val last_oil_change_mileage: Int? = null,
    val front_tire_mileage: Int? = null,
    val front_tire_life_km: Int? = null,
    val rear_tire_mileage: Int? = null,
    val rear_tire_life_km: Int? = null,
    val brake_pads_mileage: Int? = null,
    val brake_pads_life_km: Int? = null,
    val chain_maintenance_mileage: Int? = null,
    val chain_maintenance_interval: Int? = null,
    val soat_expiry_date: Long? = null,
    val techno_expiry_date: Long? = null,
    val cost_per_km: Double? = null
)

interface MotorcycleApi {
    @GET("motorcycle")
    suspend fun getMotorcycle(): MotorcycleRemote

    @PUT("motorcycle")
    suspend fun updateMotorcycle(@Body update: MotorcycleUpdateRemote): MotorcycleRemote
}
