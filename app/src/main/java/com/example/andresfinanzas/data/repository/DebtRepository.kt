package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.DebtDao
import com.example.andresfinanzas.data.local.entities.DebtEntity
import com.example.andresfinanzas.data.remote.api.DebtApi
import com.example.andresfinanzas.data.remote.models.DebtRemote
import com.example.andresfinanzas.data.remote.models.DebtPaymentRemote
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(
    private val localDao: DebtDao,
    private val remoteApi: DebtApi
) {
    fun getAllDebts(): Flow<List<DebtEntity>> = localDao.getAllDebts()

    suspend fun addDebt(personOrEntity: String, amount: Double) {
        localDao.insertDebt(
            DebtEntity(
                id = UUID.randomUUID().toString(),
                personOrEntity = personOrEntity,
                debtType = "i_owe",
                originalAmount = amount,
                remainingAmount = amount,
                interestRate = 0.0,
                debtDate = System.currentTimeMillis(),
                dueDate = null,
                priority = 3,
                status = "pending",
                description = null,
                notes = null
            )
        )
    }

    suspend fun registerPayment(debt: DebtEntity, amount: Double) {
        val updated = debt.copy(
            remainingAmount = (debt.remainingAmount - amount).coerceAtLeast(0.0),
            status = if (debt.remainingAmount - amount <= 0.0) "paid" else "partially_paid",
            syncStatus = "pending"
        )
        localDao.updateDebt(updated)
        try {
            remoteApi.addPayment(debt.id, DebtPaymentRemote(amount = amount))
            localDao.updateDebt(updated.copy(syncStatus = "synced"))
        } catch (_: Exception) {
            // Keep the local balance visible when the payment API is offline.
        }
    }

    suspend fun syncDebts() {
        try {
            // Push unsynced
            val unsynced = localDao.getUnsyncedDebts()
            for (local in unsynced) {
                try {
                    val remote = remoteApi.updateDebt(local.id, local.toRemote())
                    localDao.updateDebt(remote.toLocal().copy(syncStatus = "synced"))
                } catch (e: Exception) {
                    try {
                        val remote = remoteApi.createDebt(local.toRemote())
                        localDao.updateDebt(remote.toLocal().copy(syncStatus = "synced"))
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }

            // Fetch remote
            val remotes = remoteApi.getDebts()
            val locals = remotes.map { it.toLocal().copy(syncStatus = "synced") }
            localDao.insertDebts(locals)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun DebtEntity.toRemote(): DebtRemote {
        return DebtRemote(
            id = this.id,
            person_or_entity = this.personOrEntity,
            debt_type = this.debtType,
            original_amount = this.originalAmount,
            remaining_amount = this.remainingAmount,
            interest_rate = this.interestRate,
            debt_date = isoFormat.format(Date(this.debtDate)),
            due_date = this.dueDate?.let { isoFormat.format(Date(it)) },
            priority = this.priority,
            status = this.status,
            description = this.description,
            notes = this.notes
        )
    }

    private fun DebtRemote.toLocal(): DebtEntity {
        return DebtEntity(
            id = this.id,
            personOrEntity = this.person_or_entity,
            debtType = this.debt_type,
            originalAmount = this.original_amount,
            remainingAmount = this.remaining_amount,
            interestRate = this.interest_rate,
            debtDate = try { isoFormat.parse(this.debt_date)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() },
            dueDate = this.due_date?.let { try { isoFormat.parse(it)?.time } catch (e: Exception) { null } },
            priority = this.priority,
            status = this.status,
            description = this.description,
            notes = this.notes,
            syncStatus = "synced"
        )
    }
}
