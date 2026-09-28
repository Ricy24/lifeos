package com.example.andresfinanzas.ui.outings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.VisitedPlaceEntity
import com.example.andresfinanzas.data.remote.models.OutingPlanResponseRemote
import com.example.andresfinanzas.data.remote.models.OutingStopRemote
import com.example.andresfinanzas.data.repository.AccountRepository
import com.example.andresfinanzas.data.repository.OutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutingUiState(
    val isLoadingPlaces: Boolean = true,
    val isGeneratingPlan: Boolean = false,
    val currentPlan: OutingPlanResponseRemote? = null,
    val visitedPlaces: List<VisitedPlaceEntity> = emptyList(),
    val totalBalance: Double = 0.0,
    val safeDiscretionaryBudget: Double = 80000.0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class OutingViewModel @Inject constructor(
    private val outingRepository: OutingRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutingUiState())
    val uiState: StateFlow<OutingUiState> = _uiState.asStateFlow()

    init {
        observeData()
        syncWithBackend()
    }

    private fun observeData() {
        viewModelScope.launch {
            outingRepository.visitedPlaces.collectLatest { places ->
                _uiState.value = _uiState.value.copy(
                    isLoadingPlaces = false,
                    visitedPlaces = places
                )
            }
        }

        viewModelScope.launch {
            accountRepository.getTotalBalance().collectLatest { balance ->
                val total = balance ?: 0.0
                // 15% of total balance or minimum 50,000 COP
                val safeBudget = if (total > 0) kotlin.math.max(50000.0, total * 0.15) else 80000.0
                _uiState.value = _uiState.value.copy(
                    totalBalance = total,
                    safeDiscretionaryBudget = safeBudget
                )
            }
        }
    }

    fun syncWithBackend() {
        viewModelScope.launch {
            try {
                outingRepository.syncVisitedPlaces()
            } catch (e: Exception) {
                // Ignore sync errors offline
            }
        }
    }

    fun generatePlan(
        outingType: String,
        budget: Double?,
        areaOrCity: String,
        preferences: String?,
        useCurrentLocation: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Int? = 5
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPlan = true, errorMessage = null)
            try {
                val plan = outingRepository.generateOutingPlan(
                    outingType = outingType,
                    budget = budget,
                    areaOrCity = areaOrCity,
                    preferences = preferences,
                    useCurrentLocation = useCurrentLocation,
                    latitude = latitude,
                    longitude = longitude,
                    radiusKm = radiusKm
                )
                _uiState.value = _uiState.value.copy(
                    isGeneratingPlan = false,
                    currentPlan = plan,
                    successMessage = "¡Plan generado exitosamente con IA!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingPlan = false,
                    errorMessage = "No se pudo conectar con el servidor. Verifica tu conexión o intenta de nuevo."
                )
            }
        }
    }

    fun addPlaceFromStop(stop: OutingStopRemote) {
        viewModelScope.launch {
            try {
                outingRepository.addVisitedPlace(
                    name = stop.title,
                    category = stop.category,
                    addressOrArea = stop.maps_query,
                    rating = 5,
                    averageCost = stop.estimated_cost,
                    notes = stop.description,
                    mapsUrl = stop.maps_url,
                    visitedDate = System.currentTimeMillis()
                )
                _uiState.value = _uiState.value.copy(
                    successMessage = "¡'${stop.title}' guardado en tu historial anti-repetición!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error guardando lugar: ${e.message}"
                )
            }
        }
    }

    fun addCustomPlace(
        name: String,
        category: String,
        addressOrArea: String,
        rating: Int,
        averageCost: Double,
        notes: String?,
        mapsUrl: String?,
        visitedDate: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            try {
                outingRepository.addVisitedPlace(
                    name = name,
                    category = category,
                    addressOrArea = addressOrArea,
                    rating = rating,
                    averageCost = averageCost,
                    notes = notes,
                    mapsUrl = mapsUrl,
                    visitedDate = visitedDate
                )
                _uiState.value = _uiState.value.copy(
                    successMessage = "¡Lugar registrado correctamente!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al registrar lugar: ${e.message}"
                )
            }
        }
    }

    fun updatePlace(place: VisitedPlaceEntity) {
        viewModelScope.launch {
            try {
                outingRepository.updateVisitedPlace(place)
                _uiState.value = _uiState.value.copy(
                    successMessage = "¡Lugar actualizado correctamente!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al actualizar: ${e.message}"
                )
            }
        }
    }

    fun deletePlace(id: String) {
        viewModelScope.launch {
            try {
                outingRepository.deleteVisitedPlace(id)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Lugar eliminado del radar."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al eliminar: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
