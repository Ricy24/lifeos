package com.example.andresfinanzas.data.remote.models

data class DebtPaymentRemote(
    val amount: Double,
    val payment_date: String? = null,
    val notes: String? = null
)
