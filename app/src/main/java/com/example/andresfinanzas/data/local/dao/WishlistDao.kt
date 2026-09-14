package com.example.andresfinanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.andresfinanzas.data.local.entities.WishlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items ORDER BY priority ASC, lastUpdated DESC")
    fun getAllItems(): Flow<List<WishlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WishlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<WishlistEntity>)

    @Query("DELETE FROM wishlist_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("SELECT * FROM wishlist_items WHERE syncStatus != 'synced'")
    suspend fun getUnsyncedItems(): List<WishlistEntity>
}
