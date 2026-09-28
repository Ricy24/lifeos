package com.example.andresfinanzas.data.remote.api

import com.example.andresfinanzas.data.remote.models.OutingPlanRequestRemote
import com.example.andresfinanzas.data.remote.models.OutingPlanResponseRemote
import com.example.andresfinanzas.data.remote.models.VisitedPlaceRemote
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface OutingApi {

    @POST("outings/generate-plan")
    suspend fun generatePlan(
        @Body request: OutingPlanRequestRemote
    ): OutingPlanResponseRemote

    @GET("outings/places")
    suspend fun getVisitedPlaces(): List<VisitedPlaceRemote>

    @POST("outings/places")
    suspend fun createVisitedPlace(
        @Body place: VisitedPlaceRemote
    ): VisitedPlaceRemote

    @PUT("outings/places/{place_id}")
    suspend fun updateVisitedPlace(
        @Path("place_id") placeId: String,
        @Body place: VisitedPlaceRemote
    ): VisitedPlaceRemote

    @DELETE("outings/places/{place_id}")
    suspend fun deleteVisitedPlace(
        @Path("place_id") placeId: String
    ): Response<Unit>
}
