package com.example.andresfinanzas.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.data.repository.AccountRepository
import com.example.andresfinanzas.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val accounts: List<AccountEntity> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val netWorth: Double = 0.0,
    val monthlyExpenses: List<Float> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
        syncData()
    }

    private fun loadData() {
        viewModelScope.launch {
            accountRepository.getTotalBalance().collectLatest { balance ->
                _uiState.value = _uiState.value.copy(
                    totalBalance = balance ?: 0.0,
                    netWorth = balance ?: 0.0 // Debt calculation will be added in Phase 2
                )
            }
        }

        viewModelScope.launch {
            accountRepository.getAllAccounts().collectLatest { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    isLoading = false
                )
            }
        }

        viewModelScope.launch {
            transactionRepository.getAllTransactions().collectLatest { transactions ->
                _uiState.value = _uiState.value.copy(
                    allTransactions = transactions,
                    recentTransactions = transactions.take(8),
                    monthlyExpenses = calculateMonthlyExpenses(transactions)
                )
            }
        }
    }

    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.addTransaction(transaction)
        }
    }

    fun saveAccount(account: AccountEntity) {
        viewModelScope.launch {
            accountRepository.addAccount(account)
        }
    }

    fun updateAccountBalance(accountId: String, newBalance: Double) {
        viewModelScope.launch {
            accountRepository.updateAccountBalance(accountId, newBalance)
        }
    }

    fun syncData() {
        viewModelScope.launch {
            try {
                accountRepository.syncAccounts()
                transactionRepository.syncTransactions()
            } catch (e: Exception) {
                // Silently fail for now, offline first works
                e.printStackTrace()
            }
        }
    }

    private fun calculateMonthlyExpenses(transactions: List<TransactionEntity>): List<Float> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val monthStarts = (5 downTo 0).map { offset ->
            (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }.timeInMillis
        }
        val nextMonthStarts = monthStarts.drop(1) +
            (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis

        return monthStarts.zip(nextMonthStarts).map { (start, end) ->
            transactions
                .asSequence()
                .filter { it.transactionType == "expense" && it.transactionDate >= start && it.transactionDate < end }
                .sumOf { it.amount }
                .toFloat()
        }
    }
}
