package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.TransactionDao
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.data.remote.api.TransactionApi
import com.example.andresfinanzas.data.remote.models.TransactionRemote
import kotlinx.coroutines.flow.Flow
import com.example.andresfinanzas.data.local.dao.AccountDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val localDao: TransactionDao,
    private val remoteApi: TransactionApi,
    private val accountDao: AccountDao
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> = localDao.getAllTransactions()

    suspend fun addTransaction(transaction: TransactionEntity) {
        localDao.insertTransaction(transaction)
        if (transaction.accountId != null) {
            val account = accountDao.getAccountById(transaction.accountId)
            if (account != null) {
                val newBalance = if (transaction.transactionType == "income") {
                    account.balance + transaction.amount
                } else {
                    account.balance - transaction.amount
                }
                accountDao.updateAccount(
                    account.copy(
                        balance = newBalance,
                        isSynced = false,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }
        try {
            val remote = remoteApi.createTransaction(transaction.toRemote())
            localDao.updateTransaction(remote.toLocal().copy(syncStatus = "synced"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncTransactions() {
        try {
            // Push unsynced
            val unsynced = localDao.getUnsyncedTransactions()
            for (local in unsynced) {
                try {
                    val remote = remoteApi.createTransaction(local.toRemote())
                    localDao.updateTransaction(remote.toLocal().copy(syncStatus = "synced"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fetch remote
            val response = remoteApi.getTransactions(page = 1, size = 100)
            val locals = response.transactions.map { it.toLocal().copy(syncStatus = "synced") }
            localDao.insertTransactions(locals)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun TransactionEntity.toRemote(): TransactionRemote {
        return TransactionRemote(
            id = this.id,
            account_id = this.accountId,
            amount = this.amount,
            transaction_type = this.transactionType,
            category = this.category,
            description = this.description,
            notes = this.notes,
            transaction_date = isoFormat.format(Date(this.transactionDate)),
            location = this.location,
            is_recurring = this.isRecurring,
            recurring_pattern = this.recurringPattern
        )
    }

    private fun TransactionRemote.toLocal(): TransactionEntity {
        return TransactionEntity(
            id = this.id,
            accountId = this.account_id,
            amount = this.amount,
            transactionType = this.transaction_type,
            category = this.category,
            description = this.description,
            notes = this.notes,
            transactionDate = try { isoFormat.parse(this.transaction_date)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() },
            location = this.location,
            isRecurring = this.is_recurring,
            recurringPattern = this.recurring_pattern,
            syncStatus = "synced"
        )
    }
}
