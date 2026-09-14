package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val accountId: String?,
    val amount: Double,
    val transactionType: String,
    val category: String,
    val description: String?,
    val notes: String?,
    val transactionDate: Long,
    val location: String?,
    val isRecurring: Boolean,
    val recurringPattern: String?,
    val syncStatus: String = "pending", // pending, synced, failed
    val lastUpdated: Long = System.currentTimeMillis()
)
