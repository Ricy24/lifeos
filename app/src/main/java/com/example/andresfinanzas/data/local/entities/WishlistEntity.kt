package com.example.andresfinanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wishlist_items")
data class WishlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    val url: String?,
    val store: String?,
    val imageUrl: String?,
    val category: String?,
    val priority: Int,
    val savedAmount: Double,
    val status: String,
    val notes: String?,
    val syncStatus: String = "pending",
    val lastUpdated: Long = System.currentTimeMillis()
)
