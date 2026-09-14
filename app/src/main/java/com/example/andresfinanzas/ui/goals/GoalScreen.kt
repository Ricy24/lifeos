package com.example.andresfinanzas.ui.goals

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
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.andresfinanzas.data.local.entities.GoalEntity
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GoalScreen(
    onBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var contributionGoal by remember { mutableStateOf<GoalEntity?>(null) }
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
        item {
            Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Nueva meta")
            }
        }
        if (uiState.goals.isEmpty()) {
            item {
                Text("No hay metas registradas", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
            }
        } else {
            items(uiState.goals, key = { it.id }) { goal ->
                GoalItem(
                    goal = goal,
                    onContribute = { contributionGoal = goal },
                    onDelete = { viewModel.deleteGoal(goal) }
                )
            }
        }
    }

    if (showAddDialog) {
        GoalFormDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, targetAmount ->
                viewModel.addGoal(name, targetAmount)
                showAddDialog = false
            }
        )
    }

    contributionGoal?.let { goal ->
        ContributionDialog(
            goal = goal,
            onDismiss = { contributionGoal = null },
            onSave = { amount ->
                viewModel.addContribution(goal, amount)
                contributionGoal = null
            }
        )
    }
}

@Composable
private fun GoalFormDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    val parsedTarget = target.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva meta") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    target,
                    { target = it.filter { character -> character.isDigit() || character == '.' } },
                    label = { Text("Importe objetivo") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), parsedTarget ?: 0.0) },
                enabled = name.isNotBlank() && parsedTarget != null && parsedTarget > 0
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GoalItem(goal: GoalEntity, onContribute: () -> Unit, onDelete: () -> Unit) {
    val progress = if (goal.targetAmount > 0) {
        (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    } else {
        1f
    }
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Prioridad ${goal.priority}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                "${format.format(goal.currentAmount)} de ${format.format(goal.targetAmount)} · ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                if (goal.status == "active") {
                    TextButton(onClick = onContribute) { Text("Aportar") }
                }
                TextButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun ContributionDialog(
    goal: GoalEntity,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val parsedAmount = amount.toDoubleOrNull()
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aportar a ${goal.name}") },
        text = {
            OutlinedTextField(
                amount,
                { amount = it.filter { character -> character.isDigit() || character == '.' } },
                label = { Text("Pendiente: $remaining") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsedAmount ?: 0.0) },
                enabled = parsedAmount != null && parsedAmount > 0 && parsedAmount <= remaining
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
