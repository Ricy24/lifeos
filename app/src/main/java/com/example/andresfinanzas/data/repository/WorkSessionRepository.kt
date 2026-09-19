package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.WorkSessionDao
import com.example.andresfinanzas.data.local.entities.WorkSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WorkSessionRepository @Inject constructor(
    private val dao: WorkSessionDao
) {
    fun getActiveAndCompletedSessions(userId: String): Flow<List<WorkSessionEntity>> {
        return dao.getActiveAndCompletedSessions(userId)
    }

    suspend fun insertSession(session: WorkSessionEntity) {
        dao.insert(session)
    }

    suspend fun updateSession(session: WorkSessionEntity) {
        dao.update(session.copy(
            version = session.version + 1,
            updatedAt = java.util.Date()
        ))
    }

    suspend fun softDeleteSession(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}
