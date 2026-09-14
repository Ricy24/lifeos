package com.example.andresfinanzas.data.remote.api

import com.example.andresfinanzas.data.remote.models.GoalRemote
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface GoalApi {
    @GET("goals")
    suspend fun getGoals(): List<GoalRemote>

    @POST("goals")
    suspend fun createGoal(@Body goal: GoalRemote): GoalRemote

    @PUT("goals/{id}")
    suspend fun updateGoal(@Path("id") id: String, @Body goal: GoalRemote): GoalRemote

    @DELETE("goals/{id}")
    suspend fun deleteGoal(@Path("id") id: String)
}
