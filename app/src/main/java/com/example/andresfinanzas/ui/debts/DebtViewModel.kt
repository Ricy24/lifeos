package com.example.andresfinanzas.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.DebtEntity
import com.example.andresfinanzas.data.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtUiState(
    val debts: List<DebtEntity> = emptyList(),
    val totalActiveDebt: Double = 0.0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false
)

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val debtRepository: DebtRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebtUiState())
    val uiState: StateFlow<DebtUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            debtRepository.getAllDebts().collectLatest { debts ->
                _uiState.value = _uiState.value.copy(
                    debts = debts,
                    totalActiveDebt = debts
                        .filter { it.status != "paid" }
                        .sumOf { it.remainingAmount },
                    isLoading = false
                )
            }
        }
        sync()
    }

    fun sync() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            debtRepository.syncDebts()
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }

    fun addDebt(personOrEntity: String, amount: Double) {
        viewModelScope.launch {
            debtRepository.addDebt(personOrEntity, amount)
            sync()
        }
    }

    fun registerPayment(debt: com.example.andresfinanzas.data.local.entities.DebtEntity, amount: Double) {
        viewModelScope.launch {
            debtRepository.registerPayment(debt, amount)
            sync()
        }
    }
}