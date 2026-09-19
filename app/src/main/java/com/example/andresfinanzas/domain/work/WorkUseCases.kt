package com.example.andresfinanzas.domain.work

import com.example.andresfinanzas.data.local.entities.WorkSessionEntity
import com.example.andresfinanzas.data.repository.WorkSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import java.util.Date
import java.util.UUID

class GetWorkMetricsUseCase @Inject constructor(
    private val repository: WorkSessionRepository
) {
    operator fun invoke(userId: String): Flow<WorkMetrics> {
        return repository.getActiveAndCompletedSessions(userId).map { sessions ->
            WorkCalculator.aggregateMetrics(sessions)
        }
    }
}

class StartWorkSessionUseCase @Inject constructor(
    private val repository: WorkSessionRepository
) {
    suspend operator fun invoke(userId: String, activityType: String): WorkSessionEntity {
        val session = WorkSessionEntity(
            userId = userId,
            startTime = Date(),
            activityType = activityType
        )
        repository.insertSession(session)
        return session
    }
}

class StopWorkSessionUseCase @Inject constructor(
    private val repository: WorkSessionRepository
) {
    suspend operator fun invoke(session: WorkSessionEntity, income: Int): WorkSessionEntity {
        val endTime = Date()
        val durationMs = endTime.time - session.startTime.time
        val durationMinutes = (durationMs / (1000 * 60)).toInt()

        val updatedSession = session.copy(
            endTime = endTime,
            durationMinutes = durationMinutes,
            income = income
        )
        repository.updateSession(updatedSession)
        return updatedSession
    }
}
