package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val accountType: String,
    val balance: Double,
    val currency: String,
    val description: String?,
    val color: String?,
    val icon: String?,
    val includeInTotal: Boolean,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
