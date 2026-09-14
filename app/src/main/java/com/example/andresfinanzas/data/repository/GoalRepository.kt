package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.GoalDao
import com.example.andresfinanzas.data.local.entities.GoalEntity
import com.example.andresfinanzas.data.remote.api.GoalApi
import com.example.andresfinanzas.data.remote.models.GoalRemote
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val localDao: GoalDao,
    private val remoteApi: GoalApi
) {
    fun getAllGoals(): Flow<List<GoalEntity>> = localDao.getAllGoals()

    suspend fun addGoal(name: String, targetAmount: Double) {
        localDao.insertGoal(
            GoalEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                targetAmount = targetAmount,
                currentAmount = 0.0,
                category = "purchase",
                description = null,
                priority = 3,
                targetDate = null,
                status = "active"
            )
        )
    }

    suspend fun updateGoal(goal: GoalEntity) {
        val pending = goal.copy(syncStatus = "pending")
        localDao.updateGoal(pending)
        try {
            localDao.updateGoal(remoteApi.updateGoal(goal.id, pending.toRemote()).toLocal())
        } catch (_: Exception) {
            // Keep the pending local update for the next sync.
        }
    }

    suspend fun deleteGoal(id: String) {
        localDao.deleteGoal(id)
        try {
            remoteApi.deleteGoal(id)
        } catch (_: Exception) {
            // Local deletion is immediate while offline.
        }
    }

    suspend fun syncGoals() {
        try {
            for (local in localDao.getUnsyncedGoals()) {
                try {
                    val remote = remoteApi.updateGoal(local.id, local.toRemote())
                    localDao.updateGoal(remote.toLocal())
                } catch (_: Exception) {
                    val remote = remoteApi.createGoal(local.toRemote())
                    localDao.updateGoal(remote.toLocal())
                }
            }
            localDao.insertGoals(remoteApi.getGoals().map { it.toLocal() })
        } catch (_: Exception) {
            // Keep the local Room state available while offline.
        }
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun GoalEntity.toRemote() = GoalRemote(
        id = id,
        name = name,
        target_amount = targetAmount,
        current_amount = currentAmount,
        category = category,
        description = description,
        priority = priority,
        target_date = targetDate?.let { isoFormat.format(Date(it)) },
        status = status
    )

    private fun GoalRemote.toLocal() = GoalEntity(
        id = id,
        name = name,
        targetAmount = target_amount,
        currentAmount = current_amount,
        category = category,
        description = description,
        priority = priority,
        targetDate = target_date?.let { parseDate(it) },
        status = status,
        syncStatus = "synced"
    )

    private fun parseDate(value: String): Long? = try {
        isoFormat.parse(value)?.time
    } catch (_: Exception) {
        null
    }
}
