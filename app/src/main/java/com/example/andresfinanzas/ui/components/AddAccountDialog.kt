package com.example.andresfinanzas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.ui.theme.EmeraldGreen
import java.util.UUID

@Composable
fun AddAccountDialog(
    accountToEdit: AccountEntity? = null,
    onDismiss: () -> Unit,
    onSaveAccount: (AccountEntity) -> Unit
) {
    var name by remember { mutableStateOf(accountToEdit?.name ?: "") }
    var balanceText by remember { mutableStateOf(accountToEdit?.balance?.toString() ?: "") }
    var accountType by remember { mutableStateOf(accountToEdit?.accountType ?: "savings") }
    var includeInTotal by remember { mutableStateOf(accountToEdit?.includeInTotal ?: true) }

    val accountTypes = listOf(
        "savings" to "Ahorros",
        "checking" to "Corriente",
        "cash" to "Efectivo",
        "credit" to "Crédito",
        "investment" to "Inversión"
    )

    val quickPresets = listOf("Nequi", "Bancolombia", "Daviplata", "Efectivo", "Nu", "Billetera")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (accountToEdit == null) "Registrar Nueva Cuenta" else "Editar Saldo de Cuenta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Registra aquí el dinero que tienes en tus bancos, bolsillos o efectivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Presets (only for new)
                if (accountToEdit == null) {
                    Column {
                        Text(
                            text = "Sugerencias rápidas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickPresets.forEach { preset ->
                                SuggestionChip(
                                    onClick = {
                                        name = preset
                                        if (preset == "Efectivo" || preset == "Billetera") {
                                            accountType = "cash"
                                        }
                                    },
                                    label = { Text(preset) }
                                )
                            }
                        }
                    }
                }

                // Account Name
                Column {
                    Text(
                        text = "Nombre de la cuenta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej. Bancolombia Ahorros") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Current Balance (Dinero actual)
                Column {
                    Text(
                        text = "¿Cuánto dinero tienes aquí?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' || it == '-' }) {
                                balanceText = input
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0") },
                        prefix = { Text("$ ", fontWeight = FontWeight.Bold, color = EmeraldGreen) },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Type of account
                Column {
                    Text(
                        text = "Tipo de cuenta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accountTypes.forEach { (key, label) ->
                            FilterChip(
                                selected = accountType == key,
                                onClick = { accountType = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Include in total balance switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sumar al balance total",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Incluir en el patrimonio neto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeInTotal,
                        onCheckedChange = { includeInTotal = it }
                    )
                }

                // Buttons
                val balanceVal = balanceText.toDoubleOrNull() ?: 0.0
                val canSave = name.isNotBlank()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (canSave) {
                                val account = AccountEntity(
                                    id = accountToEdit?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    accountType = accountType,
                                    balance = balanceVal,
                                    currency = "COP",
                                    description = null,
                                    color = null,
                                    icon = null,
                                    includeInTotal = includeInTotal,
                                    isSynced = false,
                                    lastUpdated = System.currentTimeMillis()
                                )
                                onSaveAccount(account)
                                onDismiss()
                            }
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (accountToEdit == null) "Guardar Cuenta" else "Actualizar Saldo")
                    }
                }
            }
        }
    }
}
