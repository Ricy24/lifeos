package com.example.andresfinanzas.ui.motorcycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.MotorcycleEntity
import com.example.andresfinanzas.data.repository.ComponentHealth
import com.example.andresfinanzas.data.repository.DocumentStatus
import com.example.andresfinanzas.data.repository.MotorcycleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MotorcycleUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val motorcycle: MotorcycleEntity? = null,
    val componentsHealth: List<ComponentHealth> = emptyList(),
    val soatStatus: DocumentStatus? = null,
    val technoStatus: DocumentStatus? = null,
    val monthlyReserveEstimated: Double = 0.0
)

@HiltViewModel
class MotorcycleViewModel @Inject constructor(
    private val repository: MotorcycleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MotorcycleUiState())
    val uiState: StateFlow<MotorcycleUiState> = _uiState.asStateFlow()

    init {
        loadMotorcycle()
        syncWithBackend()
    }

    fun syncWithBackend() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            repository.syncWithBackend()
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }

    private fun loadMotorcycle() {
        viewModelScope.launch {
            repository.motorcycle.collectLatest { moto ->
                val activeMoto = moto ?: repository.getOrCreateDefault()
                val components = repository.computeComponentsHealth(activeMoto)
                val docs = repository.computeDocumentStatus(activeMoto.soatExpiryDate, activeMoto.technoExpiryDate)
                
                // Estimated 800 km/month average * costPerKm
                val monthlyReserve = 800 * activeMoto.costPerKm

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    motorcycle = activeMoto,
                    componentsHealth = components,
                    soatStatus = docs.first,
                    technoStatus = docs.second,
                    monthlyReserveEstimated = monthlyReserve
                )
            }
        }
    }

    fun updateMileage(newMileage: Int) {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.updateMileage(moto.id, newMileage)
        }
    }

    fun recordOilChange() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.recordOilChange(moto.id, moto.currentMileage)
        }
    }

    fun recordChainMaintenance() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            val interval = _uiState.value.componentsHealth.find { it.name.contains("Cadena") }?.intervalKm ?: 1000
            repository.editChainSettings(moto.currentMileage, interval)
        }
    }

    fun recordBrakePadsChange() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            val life = _uiState.value.componentsHealth.find { it.name.contains("Freno") }?.intervalKm ?: 8000
            repository.editBrakeSettings(moto.currentMileage, life)
        }
    }

    fun editOilSettings(lastMileage: Int, interval: Int) {
        viewModelScope.launch {
            repository.editOilSettings(lastMileage, interval)
        }
    }

    fun editTireSettings(isRear: Boolean, installedMileage: Int, lifeKm: Int) {
        viewModelScope.launch {
            repository.editTireSettings(isRear, installedMileage, lifeKm)
        }
    }

    fun editBrakeSettings(installedMileage: Int, lifeKm: Int) {
        viewModelScope.launch {
            repository.editBrakeSettings(installedMileage, lifeKm)
        }
    }

    fun editChainSettings(lubricatedMileage: Int, interval: Int) {
        viewModelScope.launch {
            repository.editChainSettings(lubricatedMileage, interval)
        }
    }

    fun editDocumentDays(isSoat: Boolean, daysRemaining: Int) {
        val timestamp = System.currentTimeMillis() + (daysRemaining.toLong() * 24L * 60 * 60 * 1000)
        viewModelScope.launch {
            repository.editDocumentExpiry(isSoat, timestamp)
        }
    }

    fun editCostPerKm(cost: Double) {
        viewModelScope.launch {
            repository.editCostPerKm(cost)
        }
    }

    fun updateMotoInfo(name: String, model: String, oilInterval: Int, costPerKm: Double) {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.updateEntireMotorcycle(
                moto.copy(
                    name = name,
                    model = model,
                    oilChangeInterval = oilInterval,
                    costPerKm = costPerKm
                )
            )
        }
    }
}
