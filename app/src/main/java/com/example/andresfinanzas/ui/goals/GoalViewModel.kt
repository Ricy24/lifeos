package com.example.andresfinanzas.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.GoalEntity
import com.example.andresfinanzas.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalUiState(
    val goals: List<GoalEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            goalRepository.getAllGoals().collectLatest { goals ->
                _uiState.value = _uiState.value.copy(goals = goals, isLoading = false)
            }
        }
        sync()
    }

    fun sync() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            goalRepository.syncGoals()
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }

    fun addGoal(name: String, targetAmount: Double) {
        viewModelScope.launch {
            goalRepository.addGoal(name, targetAmount)
            sync()
        }
    }

    fun addContribution(goal: GoalEntity, amount: Double) {
        viewModelScope.launch {
            goalRepository.updateGoal(
                goal.copy(currentAmount = (goal.currentAmount + amount).coerceAtMost(goal.targetAmount))
            )
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            goalRepository.deleteGoal(goal.id)
        }
    }
}
