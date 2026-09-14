package com.example.andresfinanzas.data.remote.models

data class TransactionRemote(
    val id: String,
    val account_id: String?,
    val amount: Double,
    val transaction_type: String,
    val category: String,
    val description: String?,
    val notes: String?,
    val transaction_date: String,
    val location: String?,
    val is_recurring: Boolean,
    val recurring_pattern: String?,
    val source: String = "app"
)

data class TransactionListResponse(
    val transactions: List<TransactionRemote>,
    val total: Int,
    val page: Int,
    val page_size: Int
)
