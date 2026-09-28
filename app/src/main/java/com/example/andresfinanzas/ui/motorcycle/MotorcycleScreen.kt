package com.example.andresfinanzas.ui.motorcycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.andresfinanzas.data.repository.ComponentHealth
import com.example.andresfinanzas.data.repository.DocumentStatus
import com.example.andresfinanzas.data.repository.MaintenanceStatus
import com.example.andresfinanzas.ui.theme.ElectricIndigo
import com.example.andresfinanzas.ui.theme.EmeraldGreen
import com.example.andresfinanzas.ui.theme.RoseRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorcycleScreen(
    onBack: () -> Unit,
    viewModel: MotorcycleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMileageDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    val moto = uiState.motorcycle

    val formatCop = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏍️ ", fontSize = 22.sp)
                        Column {
                            Text(
                                text = moto?.name ?: "Mi Moto",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Modelo ${moto?.model ?: "2024"} • Garage LifeOS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Moto")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading || moto == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Digital Odometer Hero Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF1E293B),
                                            Color(0xFF0F172A)
                                        )
                                    )
                                )
                                .padding(24.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "KILOMETRAJE TOTAL",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF94A3B8),
                                        letterSpacing = 1.5.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EmeraldGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "En marcha",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = EmeraldGreen,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = NumberFormat.getNumberInstance(Locale.US).format(moto.currentMileage),
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "km",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showMileageDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                                    ) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Actualizar Odómetro", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.updateMileage(moto.currentMileage + 50) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("+50 km", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Document Status Cards (SOAT & Tecnomecánica)
                item {
                    Text(
                        text = "Documentación Legal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DocumentCard(
                            status = uiState.soatStatus,
                            modifier = Modifier.weight(1f)
                        )
                        DocumentCard(
                            status = uiState.technoStatus,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Maintenance Provision (Fondo de Ahorro Preventivo)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ElectricIndigo.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricIndigo.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("💰", fontSize = 22.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fondo Preventivo de Moto",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sugerido: ${formatCop.format(uiState.monthlyReserveEstimated)} / mes ($45/km) para que cualquier cambio de repuesto esté prepagado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 4. Component Health Monitoring
                item {
                    Text(
                        text = "Estado de Componentes & Mantenimiento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(uiState.componentsHealth) { component ->
                    ComponentHealthCard(
                        health = component,
                        onPerformService = {
                            when (component.name) {
                                "Aceite de Motor" -> viewModel.recordOilChange()
                                "Cadena (Lubricación)" -> viewModel.recordChainMaintenance()
                                "Pastillas de Freno" -> viewModel.recordBrakePadsChange()
                                "Llanta Trasera" -> viewModel.recordRearTireChange()
                                "Llanta Delantera" -> viewModel.recordFrontTireChange()
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    // Dialog for Updating Mileage
    if (showMileageDialog && moto != null) {
        var inputMileage by remember { mutableStateOf(moto.currentMileage.toString()) }
        AlertDialog(
            onDismissRequest = { showMileageDialog = false },
            title = { Text("Actualizar Kilometraje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ingresa la lectura actual del velocímetro:")
                    OutlinedTextField(
                        value = inputMileage,
                        onValueChange = { inputMileage = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Kilómetros (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = inputMileage.toIntOrNull()
                        if (parsed != null && parsed >= 0) {
                            viewModel.updateMileage(parsed)
                        }
                        showMileageDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMileageDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog for Editing Moto Details
    if (showEditDialog && moto != null) {
        var inputName by remember { mutableStateOf(moto.name) }
        var inputModel by remember { mutableStateOf(moto.model) }
        var inputOilInterval by remember { mutableStateOf(moto.oilChangeInterval.toString()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar Información de Moto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nombre o Referencia (ej. Yamaha FZ25)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inputModel,
                        onValueChange = { inputModel = it },
                        label = { Text("Modelo / Año") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inputOilInterval,
                        onValueChange = { inputOilInterval = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Intervalo Cambio Aceite (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val interval = inputOilInterval.toIntOrNull() ?: 2500
                        viewModel.updateMotoInfo(inputName.trim(), inputModel.trim(), interval)
                        showEditDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun DocumentCard(
    status: DocumentStatus?,
    modifier: Modifier = Modifier
) {
    if (status == null) return

    val statusColor = when (status.status) {
        MaintenanceStatus.OPTIMAL -> EmeraldGreen
        MaintenanceStatus.ATTENTION -> Color(0xFFF59E0B)
        MaintenanceStatus.URGENT -> RoseRed
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale("es", "CO"))

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Text(
                text = if (status.isExpired) "¡Vencido!" else "${status.daysRemaining} días",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = statusColor
            )

            Text(
                text = "Vence: ${dateFormatter.format(Date(status.expiryTimestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ComponentHealthCard(
    health: ComponentHealth,
    onPerformService: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (health.status) {
        MaintenanceStatus.OPTIMAL -> EmeraldGreen
        MaintenanceStatus.ATTENTION -> Color(0xFFF59E0B)
        MaintenanceStatus.URGENT -> RoseRed
    }

    val statusLabel = when (health.status) {
        MaintenanceStatus.OPTIMAL -> "Óptimo"
        MaintenanceStatus.ATTENTION -> "Pronto cambio"
        MaintenanceStatus.URGENT -> "¡Reemplazo urgente!"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(health.icon, fontSize = 24.sp)
                    Column {
                        Text(
                            text = health.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${health.currentUsageKm} km recorridos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { health.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quedan: ${health.remainingKm} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Vida útil: ${health.maxLifeKm} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onPerformService()
                            expanded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar Servicio / Cambio Hoy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
