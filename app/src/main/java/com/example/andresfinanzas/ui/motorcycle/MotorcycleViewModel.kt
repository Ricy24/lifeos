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
    val motorcycle: MotorcycleEntity? = null,
    val componentsHealth: List<ComponentHealth> = emptyList(),
    val soatStatus: DocumentStatus? = null,
    val technoStatus: DocumentStatus? = null,
    val monthlyReserveEstimated: Double = 0.0 // Monthly suggested saving
)

@HiltViewModel
class MotorcycleViewModel @Inject constructor(
    private val repository: MotorcycleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MotorcycleUiState())
    val uiState: StateFlow<MotorcycleUiState> = _uiState.asStateFlow()

    init {
        loadMotorcycle()
    }

    private fun loadMotorcycle() {
        viewModelScope.launch {
            repository.motorcycle.collectLatest { moto ->
                val activeMoto = moto ?: repository.getOrCreateDefault()
                val components = repository.computeComponentsHealth(activeMoto)
                val docs = repository.computeDocumentStatus(activeMoto.soatExpiryDate, activeMoto.technoExpiryDate)
                
                // Estimated 800 km/month average * costPerKm
                val monthlyReserve = 800 * activeMoto.costPerKm

                _uiState.value = MotorcycleUiState(
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

    fun recordFrontTireChange() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.recordFrontTireChange(moto.id, moto.currentMileage)
        }
    }

    fun recordRearTireChange() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.recordRearTireChange(moto.id, moto.currentMileage)
        }
    }

    fun recordBrakePadsChange() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.recordBrakePadsChange(moto.id, moto.currentMileage)
        }
    }

    fun recordChainMaintenance() {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.recordChainMaintenance(moto.id, moto.currentMileage)
        }
    }

    fun updateMotoInfo(name: String, model: String, oilInterval: Int) {
        val moto = _uiState.value.motorcycle ?: return
        viewModelScope.launch {
            repository.updateMotoDetails(
                name = name,
                model = model,
                oilInterval = oilInterval,
                soatExpiry = moto.soatExpiryDate,
                technoExpiry = moto.technoExpiryDate
            )
        }
    }
}
