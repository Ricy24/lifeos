package com.example.andresfinanzas.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.andresfinanzas.data.local.entities.DebtEntity
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DebtScreen(
    onBack: () -> Unit,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var paymentDebt by remember { mutableStateOf<DebtEntity?>(null) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Volver") }
                TextButton(onClick = viewModel::sync, enabled = !uiState.isSyncing) {
                    Text(if (uiState.isSyncing) "Sincronizando..." else "Sincronizar")
                }
            }
        }
        item {
            Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Nueva deuda")
            }
        }
        item { DebtSummary(uiState.totalActiveDebt, uiState.debts.count { it.status != "paid" }) }
        if (uiState.debts.isEmpty()) {
            item {
                Text(
                    text = "No hay deudas registradas",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
            }
        } else {
            items(uiState.debts, key = { it.id }) { debt ->
                DebtItem(debt, onPay = { paymentDebt = debt })
            }
        }
    }

    if (showAddDialog) {
        DebtFormDialog(
            onDismiss = { showAddDialog = false },
            onSave = { person, amount ->
                viewModel.addDebt(person, amount)
                showAddDialog = false
            }
        )
    }

    paymentDebt?.let { debt ->
        PaymentDialog(
            debt = debt,
            onDismiss = { paymentDebt = null },
            onSave = { amount ->
                viewModel.registerPayment(debt, amount)
                paymentDebt = null
            }
        )
    }
}

@Composable
private fun DebtFormDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val parsedAmount = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva deuda") },
        text = {
            Column {
                OutlinedTextField(person, { person = it }, label = { Text("Persona o entidad") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount,
                    { amount = it.filter { character -> character.isDigit() || character == '.' } },
                    label = { Text("Importe") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(person.trim(), parsedAmount ?: 0.0) },
                enabled = person.isNotBlank() && parsedAmount != null && parsedAmount > 0
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PaymentDialog(
    debt: DebtEntity,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val parsedAmount = amount.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar pago") },
        text = {
            OutlinedTextField(
                amount,
                { amount = it.filter { character -> character.isDigit() || character == '.' } },
                label = { Text("Maximo ${debt.remainingAmount}") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsedAmount ?: 0.0) },
                enabled = parsedAmount != null && parsedAmount > 0 && parsedAmount <= debt.remainingAmount
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DebtSummary(total: Double, count: Int) {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Deuda activa", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(format.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("$count pendientes", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DebtItem(debt: DebtEntity, onPay: () -> Unit) {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(debt.personOrEntity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(format.format(debt.remainingAmount), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Prioridad ${debt.priority} · ${debt.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            if (debt.status != "paid") {
                TextButton(onClick = onPay) { Text("Registrar pago") }
            }
        }
    }
}