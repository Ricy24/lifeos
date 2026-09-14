package com.example.andresfinanzas.data.remote.api

import com.example.andresfinanzas.data.remote.models.TransactionRemote
import com.example.andresfinanzas.data.remote.models.TransactionListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TransactionApi {
    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): TransactionListResponse

    @POST("transactions")
    suspend fun createTransaction(@Body transaction: TransactionRemote): TransactionRemote
}
