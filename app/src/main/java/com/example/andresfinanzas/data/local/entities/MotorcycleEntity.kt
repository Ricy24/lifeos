package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "motorcycles")
data class MotorcycleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Mi Moto",
    val model: String = "2024",
    val currentMileage: Int = 0,
    val oilChangeInterval: Int = 2500, // cada 2500 km
    val lastOilChangeMileage: Int = 0,
    val frontTireMileage: Int = 0, // km al montar llanta delantera
    val frontTireLifeKm: Int = 18000,
    val rearTireMileage: Int = 0, // km al montar llanta trasera
    val rearTireLifeKm: Int = 12000,
    val brakePadsMileage: Int = 0,
    val brakePadsLifeKm: Int = 8000,
    val chainMaintenanceMileage: Int = 0,
    val chainMaintenanceInterval: Int = 1000, // lubricación/tensión cada 1000 km
    val soatExpiryDate: Long = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000), // +1 año
    val technoExpiryDate: Long = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000),
    val costPerKm: Double = 45.0, // Ahorro preventivo COP por km recorrido
    val lastUpdated: Long = System.currentTimeMillis()
)
