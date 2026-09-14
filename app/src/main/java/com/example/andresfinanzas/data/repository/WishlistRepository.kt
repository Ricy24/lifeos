package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.WishlistDao
import com.example.andresfinanzas.data.local.entities.WishlistEntity
import com.example.andresfinanzas.data.remote.api.WishlistApi
import com.example.andresfinanzas.data.remote.models.WishlistRemote
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepository @Inject constructor(
    private val localDao: WishlistDao,
    private val remoteApi: WishlistApi
) {
    fun getAllItems(): Flow<List<WishlistEntity>> = localDao.getAllItems()

    suspend fun addItem(item: WishlistEntity) = localDao.insertItem(item)

    suspend fun deleteItem(id: String) {
        localDao.deleteItem(id)
        try {
            remoteApi.deleteItem(id)
        } catch (_: Exception) {
            // Local deletion is immediate while offline.
        }
    }

    suspend fun syncItems() {
        try {
            for (local in localDao.getUnsyncedItems()) {
                try {
                    val remote = remoteApi.updateItem(local.id, local.toRemote())
                    localDao.insertItem(remote.toLocal())
                } catch (_: Exception) {
                    val remote = remoteApi.createItem(local.toRemote())
                    localDao.insertItem(remote.toLocal())
                }
            }
            localDao.insertItems(remoteApi.getItems().map { it.toLocal() })
        } catch (_: Exception) {
            // Keep local wishlist available while offline.
        }
    }

    private fun WishlistEntity.toRemote() = WishlistRemote(
        id = id,
        name = name,
        price = price,
        url = url,
        store = store,
        image_url = imageUrl,
        category = category,
        priority = priority,
        saved_amount = savedAmount,
        status = status,
        notes = notes
    )

    private fun WishlistRemote.toLocal() = WishlistEntity(
        id = id,
        name = name,
        price = price,
        url = url,
        store = store,
        imageUrl = image_url,
        category = category,
        priority = priority,
        savedAmount = saved_amount,
        status = status,
        notes = notes,
        syncStatus = "synced"
    )
}
