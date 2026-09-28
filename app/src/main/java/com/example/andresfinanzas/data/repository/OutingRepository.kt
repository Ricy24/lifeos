package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.VisitedPlaceDao
import com.example.andresfinanzas.data.local.entities.VisitedPlaceEntity
import com.example.andresfinanzas.data.remote.api.OutingApi
import com.example.andresfinanzas.data.remote.models.OutingPlanRequestRemote
import com.example.andresfinanzas.data.remote.models.OutingPlanResponseRemote
import com.example.andresfinanzas.data.remote.models.VisitedPlaceRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutingRepository @Inject constructor(
    private val visitedPlaceDao: VisitedPlaceDao,
    private val outingApi: OutingApi
) {
    val visitedPlaces: Flow<List<VisitedPlaceEntity>> = visitedPlaceDao.getAllVisitedPlaces()

    suspend fun syncVisitedPlaces() = withContext(Dispatchers.IO) {
        try {
            val remotePlaces = outingApi.getVisitedPlaces()
            val entities = remotePlaces.map { remote ->
                VisitedPlaceEntity(
                    id = remote.id ?: UUID.randomUUID().toString(),
                    name = remote.name,
                    category = remote.category,
                    addressOrArea = remote.address_or_area,
                    rating = remote.rating,
                    averageCost = remote.average_cost,
                    notes = remote.notes,
                    mapsUrl = remote.maps_url,
                    visitedDate = remote.visited_date ?: System.currentTimeMillis(),
                    createdAt = remote.created_at ?: System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) {
                visitedPlaceDao.insertAll(entities)
            }
        } catch (e: Exception) {
            // Offline fallback - use local data
        }
    }

    suspend fun generateOutingPlan(
        outingType: String,
        budget: Double?,
        areaOrCity: String,
        preferences: String?,
        useCurrentLocation: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Int? = 5
    ): OutingPlanResponseRemote = withContext(Dispatchers.IO) {
        val request = OutingPlanRequestRemote(
            outing_type = outingType,
            budget = budget,
            area_or_city = areaOrCity,
            preferences = preferences,
            use_current_location = useCurrentLocation,
            latitude = latitude,
            longitude = longitude,
            radius_km = radiusKm,
            google_maps_api_key = com.example.andresfinanzas.BuildConfig.MAPS_API_KEY
        )
        outingApi.generatePlan(request)
    }

    suspend fun addVisitedPlace(
        name: String,
        category: String,
        addressOrArea: String,
        rating: Int,
        averageCost: Double,
        notes: String?,
        mapsUrl: String?,
        visitedDate: Long = System.currentTimeMillis()
    ): VisitedPlaceEntity = withContext(Dispatchers.IO) {
        val remotePayload = VisitedPlaceRemote(
            name = name,
            category = category,
            address_or_area = addressOrArea,
            rating = rating,
            average_cost = averageCost,
            notes = notes,
            maps_url = mapsUrl,
            visited_date = visitedDate
        )

        var finalId = UUID.randomUUID().toString()
        var finalCreatedAt = System.currentTimeMillis()

        try {
            val created = outingApi.createVisitedPlace(remotePayload)
            created.id?.let { finalId = it }
            created.created_at?.let { finalCreatedAt = it }
        } catch (e: Exception) {
            // Offline: fallback to local ID
        }

        val entity = VisitedPlaceEntity(
            id = finalId,
            name = name,
            category = category,
            addressOrArea = addressOrArea,
            rating = rating,
            averageCost = averageCost,
            notes = notes,
            mapsUrl = mapsUrl,
            visitedDate = visitedDate,
            createdAt = finalCreatedAt
        )
        visitedPlaceDao.insert(entity)
        entity
    }

    suspend fun updateVisitedPlace(place: VisitedPlaceEntity) = withContext(Dispatchers.IO) {
        visitedPlaceDao.update(place)
        try {
            val remotePayload = VisitedPlaceRemote(
                id = place.id,
                name = place.name,
                category = place.category,
                address_or_area = place.addressOrArea,
                rating = place.rating,
                average_cost = place.averageCost,
                notes = place.notes,
                maps_url = place.mapsUrl,
                visited_date = place.visitedDate
            )
            outingApi.updateVisitedPlace(place.id, remotePayload)
        } catch (e: Exception) {
            // Keep local changes
        }
    }

    suspend fun deleteVisitedPlace(id: String) = withContext(Dispatchers.IO) {
        visitedPlaceDao.deleteById(id)
        try {
            outingApi.deleteVisitedPlace(id)
        } catch (e: Exception) {
            // Keep local deletion
        }
    }
}
