package com.example.andresfinanzas.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.andresfinanzas.data.local.entities.WishlistEntity
import java.text.NumberFormat
import java.util.Locale

@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    viewModel: WishlistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.setSharedUrl(sharedUrl)
            onSharedUrlConsumed()
        }
    }

    if (uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("Volver") }
                TextButton(onClick = viewModel::sync, enabled = !uiState.isSyncing) {
                    Text(if (uiState.isSyncing) "Sincronizando..." else "Sincronizar")
                }
            }
        }
        if (uiState.sharedUrl != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nuevo producto compartido", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.sharedUrl!!,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nombre") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it.filter { character -> character.isDigit() || character == '.' } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Precio estimado") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.addSharedItem(name, price.toDoubleOrNull() ?: 0.0)
                            },
                            enabled = price.toDoubleOrNull()?.let { it > 0 } == true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar en wishlist")
                        }
                    }
                }
            }
        }
        if (uiState.items.isEmpty()) {
            item { Text("No hay productos guardados", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)) }
        } else {
            items(uiState.items, key = { it.id }) { item ->
                WishlistItem(item, onDelete = { viewModel.deleteItem(item) })
            }
        }
    }
}

@Composable
private fun WishlistItem(item: WishlistEntity, onDelete: () -> Unit) {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(format.format(item.price), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${item.store ?: "Sin tienda"} · Prioridad ${item.priority}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            item.url?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            TextButton(onClick = onDelete) { Text("Eliminar") }
        }
    }
}
