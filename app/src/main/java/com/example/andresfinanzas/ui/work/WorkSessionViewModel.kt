package com.example.andresfinanzas.ui.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.WorkSessionEntity
import com.example.andresfinanzas.domain.work.GetWorkMetricsUseCase
import com.example.andresfinanzas.domain.work.StartWorkSessionUseCase
import com.example.andresfinanzas.domain.work.StopWorkSessionUseCase
import com.example.andresfinanzas.domain.work.WorkMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkSessionViewModel @Inject constructor(
    private val getWorkMetricsUseCase: GetWorkMetricsUseCase,
    private val startWorkSessionUseCase: StartWorkSessionUseCase,
    private val stopWorkSessionUseCase: StopWorkSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkSessionUiState())
    val uiState: StateFlow<WorkSessionUiState> = _uiState.asStateFlow()

    private var currentUserId: String = "default_user_id" // In a real app, this comes from Auth

    init {
        loadMetrics()
    }

    private fun loadMetrics() {
        viewModelScope.launch {
            getWorkMetricsUseCase(currentUserId).collect { metrics ->
                _uiState.value = _uiState.value.copy(metrics = metrics)
            }
        }
    }

    fun startSession(activityType: String) {
        viewModelScope.launch {
            val session = startWorkSessionUseCase(currentUserId, activityType)
            _uiState.value = _uiState.value.copy(activeSession = session)
        }
    }

    fun stopSession(income: Int) {
        val activeSession = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            stopWorkSessionUseCase(activeSession, income)
            _uiState.value = _uiState.value.copy(activeSession = null)
        }
    }
}

data class WorkSessionUiState(
    val activeSession: WorkSessionEntity? = null,
    val metrics: WorkMetrics? = null
)
