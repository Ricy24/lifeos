package com.example.andresfinanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.andresfinanzas.data.local.entities.VisitedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPlaceDao {
    @Query("SELECT * FROM visited_places ORDER BY visitedDate DESC")
    fun getAllVisitedPlaces(): Flow<List<VisitedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(places: List<VisitedPlaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: VisitedPlaceEntity)

    @Update
    suspend fun update(place: VisitedPlaceEntity)

    @Delete
    suspend fun delete(place: VisitedPlaceEntity)

    @Query("DELETE FROM visited_places WHERE id = :id")
    suspend fun deleteById(id: String)
}
