package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val category: String,
    val description: String?,
    val priority: Int,
    val targetDate: Long?,
    val status: String,
    val syncStatus: String = "pending",
    val lastUpdated: Long = System.currentTimeMillis()
)
