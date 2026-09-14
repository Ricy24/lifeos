package com.example.andresfinanzas.data.remote.models

data class WishlistRemote(
    val id: String,
    val name: String,
    val price: Double,
    val url: String?,
    val store: String?,
    val image_url: String?,
    val category: String?,
    val priority: Int,
    val saved_amount: Double,
    val status: String,
    val notes: String?
)
