package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey
    val id: String,
    val personOrEntity: String,
    val debtType: String, // i_owe, owed_to_me
    val originalAmount: Double,
    val remainingAmount: Double,
    val interestRate: Double,
    val debtDate: Long,
    val dueDate: Long?,
    val priority: Int,
    val status: String, // pending, partially_paid, paid, overdue
    val description: String?,
    val notes: String?,
    val syncStatus: String = "pending",
    val lastUpdated: Long = System.currentTimeMillis()
)
