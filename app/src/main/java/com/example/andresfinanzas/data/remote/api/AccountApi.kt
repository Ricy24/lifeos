package com.example.andresfinanzas.data.remote.api

import com.example.andresfinanzas.data.remote.models.AccountRemote
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AccountApi {
    @GET("accounts")
    suspend fun getAccounts(): List<AccountRemote>

    @POST("accounts")
    suspend fun createAccount(@Body account: AccountRemote): AccountRemote

    @PUT("accounts/{id}")
    suspend fun updateAccount(
        @Path("id") id: String,
        @Body account: AccountRemote
    ): AccountRemote
}
