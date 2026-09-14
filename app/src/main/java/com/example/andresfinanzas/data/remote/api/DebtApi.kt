package com.example.andresfinanzas.data.remote.api

import com.example.andresfinanzas.data.remote.models.DebtRemote
import com.example.andresfinanzas.data.remote.models.DebtPaymentRemote
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface DebtApi {
    @GET("debts")
    suspend fun getDebts(@Query("status") status: String? = null): List<DebtRemote>

    @POST("debts")
    suspend fun createDebt(@Body debt: DebtRemote): DebtRemote

    @PUT("debts/{id}")
    suspend fun updateDebt(
        @Path("id") id: String,
        @Body debt: DebtRemote
    ): DebtRemote

    @POST("debts/{id}/payments")
    suspend fun addPayment(
        @Path("id") id: String,
        @Body payment: DebtPaymentRemote
    )
}
