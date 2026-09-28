package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "visited_places")
data class VisitedPlaceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "Restaurante",
    val addressOrArea: String = "",
    val rating: Int = 5,
    val averageCost: Double = 0.0,
    val notes: String? = null,
    val mapsUrl: String? = null,
    val visitedDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
