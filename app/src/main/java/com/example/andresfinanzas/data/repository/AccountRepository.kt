package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.AccountDao
import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.remote.api.AccountApi
import com.example.andresfinanzas.data.remote.models.AccountRemote
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val localDao: AccountDao,
    private val remoteApi: AccountApi
) {
    fun getAllAccounts(): Flow<List<AccountEntity>> = localDao.getAllAccounts()
    fun getTotalBalance(): Flow<Double?> = localDao.getTotalBalance()

    suspend fun getAccountById(id: String): AccountEntity? = localDao.getAccountById(id)

    suspend fun addAccount(account: AccountEntity) {
        localDao.insertAccount(account)
        try {
            val remoteAccount = remoteApi.createAccount(account.toRemote())
            localDao.updateAccount(remoteAccount.toLocal().copy(isSynced = true))
        } catch (e: Exception) {
            // Handle error, leave isSynced = false
            e.printStackTrace()
        }
    }

    suspend fun syncAccounts() {
        try {
            // 1. Push unsynced local accounts
            val unsynced = localDao.getUnsyncedAccounts()
            for (local in unsynced) {
                try {
                    val remote = remoteApi.updateAccount(local.id, local.toRemote())
                    localDao.updateAccount(remote.toLocal().copy(isSynced = true))
                } catch (e: Exception) {
                    // Try create if update fails (404)
                    try {
                        val remote = remoteApi.createAccount(local.toRemote())
                        localDao.updateAccount(remote.toLocal().copy(isSynced = true))
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }

            // 2. Fetch all remote accounts
            val remotes = remoteApi.getAccounts()
            val localsToInsert = remotes.map { it.toLocal().copy(isSynced = true) }
            localDao.insertAccounts(localsToInsert)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateAccountBalance(accountId: String, newBalance: Double) {
        val account = localDao.getAccountById(accountId) ?: return
        val updated = account.copy(balance = newBalance, isSynced = false, lastUpdated = System.currentTimeMillis())
        localDao.updateAccount(updated)
        try {
            val remote = remoteApi.updateAccount(updated.id, updated.toRemote())
            localDao.updateAccount(remote.toLocal().copy(isSynced = true))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun AccountEntity.toRemote(): AccountRemote {
        return AccountRemote(
            id = this.id,
            name = this.name,
            account_type = this.accountType,
            balance = this.balance,
            currency = this.currency,
            description = this.description,
            color = this.color,
            icon = this.icon,
            include_in_total = this.includeInTotal
        )
    }

    private fun AccountRemote.toLocal(): AccountEntity {
        return AccountEntity(
            id = this.id,
            name = this.name,
            accountType = this.account_type,
            balance = this.balance,
            currency = this.currency,
            description = this.description,
            color = this.color,
            icon = this.icon,
            includeInTotal = this.include_in_total,
            isSynced = true
        )
    }
}
