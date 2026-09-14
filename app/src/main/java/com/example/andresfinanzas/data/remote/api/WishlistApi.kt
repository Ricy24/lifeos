package com.example.andresfinanzas.data.remote.api

import com.example.andresfinanzas.data.remote.models.WishlistRemote
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface WishlistApi {
    @GET("goals/wishlist")
    suspend fun getItems(): List<WishlistRemote>

    @POST("goals/wishlist")
    suspend fun createItem(@Body item: WishlistRemote): WishlistRemote

    @PUT("goals/wishlist/{id}")
    suspend fun updateItem(@Path("id") id: String, @Body item: WishlistRemote): WishlistRemote

    @DELETE("goals/wishlist/{id}")
    suspend fun deleteItem(@Path("id") id: String)
}
