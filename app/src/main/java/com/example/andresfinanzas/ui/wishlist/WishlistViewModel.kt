package com.example.andresfinanzas.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andresfinanzas.data.local.entities.WishlistEntity
import com.example.andresfinanzas.data.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WishlistUiState(
    val items: List<WishlistEntity> = emptyList(),
    val sharedUrl: String? = null,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false
)

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllItems().collectLatest { items ->
                _uiState.value = _uiState.value.copy(items = items, isLoading = false)
            }
        }
        sync()
    }

    fun setSharedUrl(url: String?) {
        _uiState.value = _uiState.value.copy(sharedUrl = url?.takeIf { it.isNotBlank() })
    }

    fun addSharedItem(name: String, price: Double) {
        val url = _uiState.value.sharedUrl ?: return
        viewModelScope.launch {
            repository.addItem(
                WishlistEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Producto compartido" },
                    price = price,
                    url = url,
                    store = if (url.contains("mercadolibre", ignoreCase = true)) "Mercado Libre" else null,
                    imageUrl = null,
                    category = null,
                    priority = 3,
                    savedAmount = 0.0,
                    status = "wanted",
                    notes = null
                )
            )
            _uiState.value = _uiState.value.copy(sharedUrl = null)
            sync()
        }
    }

    fun sync() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            repository.syncItems()
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }

    fun deleteItem(item: WishlistEntity) {
        viewModelScope.launch {
            repository.deleteItem(item.id)
        }
    }
}
