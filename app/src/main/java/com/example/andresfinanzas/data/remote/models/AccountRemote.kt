package com.example.andresfinanzas.data.remote.models

data class AccountRemote(
    val id: String,
    val name: String,
    val account_type: String,
    val balance: Double,
    val currency: String,
    val description: String?,
    val color: String?,
    val icon: String?,
    val include_in_total: Boolean
)
