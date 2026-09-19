package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val startTime: Date,
    val endTime: Date? = null,
    val durationMinutes: Int? = null,
    val income: Int = 0,
    val activityType: String,
    val location: String? = null,
    val notes: String? = null,
    val version: Int = 1,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val deletedAt: Date? = null
)
