package com.example.andresfinanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.andresfinanzas.data.local.entities.MotorcycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MotorcycleDao {
    @Query("SELECT * FROM motorcycles LIMIT 1")
    fun getMotorcycle(): Flow<MotorcycleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMotorcycle(motorcycle: MotorcycleEntity)

    @Update
    suspend fun updateMotorcycle(motorcycle: MotorcycleEntity)

    @Query("UPDATE motorcycles SET currentMileage = :newMileage, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateMileage(id: String, newMileage: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE motorcycles SET lastOilChangeMileage = :mileage, lastUpdated = :timestamp WHERE id = :id")
    suspend fun recordOilChange(id: String, mileage: Int, timestamp: Long = System.currentTimeMillis())
}
