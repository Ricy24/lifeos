package com.example.andresfinanzas.ui.motorcycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.andresfinanzas.R
import com.example.andresfinanzas.data.repository.ComponentHealth
import com.example.andresfinanzas.data.repository.DocumentStatus
import com.example.andresfinanzas.data.repository.MaintenanceStatus
import com.example.andresfinanzas.ui.theme.CyanAccent
import com.example.andresfinanzas.ui.theme.ElectricIndigo
import com.example.andresfinanzas.ui.theme.EmeraldGreen
import com.example.andresfinanzas.ui.theme.PrimaryBrand
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
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var componentToEdit by remember { mutableStateOf<ComponentHealth?>(null) }
    var documentToEdit by remember { mutableStateOf<Pair<String, Int>?>(null) } // title, days
    var showCostPerKmDialog by remember { mutableStateOf(false) }

    val moto = uiState.motorcycle
    val isDark = isSystemInDarkTheme()

    val formatCop = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
    }

    // 3D floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "moto3DFloat")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    // Subtle atmospheric glow consistent with LifeOS dark & light palettes
    val atmosphericGlow = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                PrimaryBrand.copy(alpha = 0.12f),
                Color.Transparent
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                PrimaryBrand.copy(alpha = 0.05f),
                Color.Transparent
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Mi Moto",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${moto?.name ?: "Yamaha"} • Modelo ${moto?.model ?: "2024"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Right actions (Cloud sync & settings)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = EmeraldGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Nube",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.syncWithBackend() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (uiState.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryBrand)
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sincronizar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            IconButton(
                                onClick = { showEditInfoDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Configuración", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
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
                CircularProgressIndicator(color = PrimaryBrand)
            }
        } else {
            // Find key components for quick pods
            val oilHealth = uiState.componentsHealth.firstOrNull { it.name.contains("Aceite", ignoreCase = true) }
            val brakeHealth = uiState.componentsHealth.firstOrNull { it.name.contains("Freno", ignoreCase = true) }
            val tireHealth = uiState.componentsHealth.firstOrNull { it.name.contains("Llanta", ignoreCase = true) }
            val chainHealth = uiState.componentsHealth.firstOrNull { it.name.contains("Cadena", ignoreCase = true) }

            // Dynamic companion dialogue based on moto status
            val companionSpeech = remember(moto.currentMileage, oilHealth?.remainingKm, chainHealth?.remainingKm, uiState.soatStatus?.daysRemaining) {
                when {
                    (oilHealth?.remainingKm ?: 1000) < 300 -> {
                        "¡Hey Andrés! El cambio de aceite está muy cerca (${oilHealth?.remainingKm} km restantes). ¡Cuidemos el motor! 🛢️"
                    }
                    (chainHealth?.remainingKm ?: 500) < 100 -> {
                        "¡Hey Andrés! Toca lubricar y tensionar la cadena pronto ⛓️"
                    }
                    (uiState.soatStatus?.daysRemaining ?: 365) < 30 -> {
                        "¡Atención Andrés! Tu SOAT vence en ${uiState.soatStatus?.daysRemaining} días. Tenlo presente 🛡️"
                    }
                    else -> {
                        "¡Hey Andrés! Tu ${moto.name} está al 100% de salud. ¿Listo para salir a rodar hoy? 🏍️✨"
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .background(brush = atmosphericGlow)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. HERO 3D MOTORCYCLE SECTION (with speech bubble & glowing halo arc)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Companion Speech Bubble in LifeOS styling
                        LifeOSSpeechBubble(
                            text = companionSpeech,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3D Motorcycle floating over glowing arc
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Glowing Halo Arc behind bike in LifeOS royal blue & cyan
                            Canvas(modifier = Modifier.size(280.dp, 160.dp)) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            PrimaryBrand.copy(alpha = 0.10f),
                                            CyanAccent.copy(alpha = 0.85f),
                                            PrimaryBrand,
                                            ElectricIndigo.copy(alpha = 0.85f),
                                            PrimaryBrand.copy(alpha = 0.10f)
                                        )
                                    ),
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // 3D Motorcycle Avatar
                            Image(
                                painter = painterResource(id = R.drawable.moto_3d_avatar),
                                contentDescription = "Moto 3D Companion",
                                modifier = Modifier
                                    .size(240.dp)
                                    .graphicsLayer {
                                        translationY = floatOffsetY
                                    }
                            )
                        }

                        // Digital Odometer Quick Capsule Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                            modifier = Modifier.clickable { showMileageDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "⚡ ${NumberFormat.getNumberInstance(Locale.US).format(moto.currentMileage)} km",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryBrand
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = PrimaryBrand.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable { showMileageDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryBrand)
                                        Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBrand)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { viewModel.updateMileage(moto.currentMileage + 50) }
                                    ) {
                                        Text("+50", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { viewModel.updateMileage(moto.currentMileage + 100) }
                                    ) {
                                        Text("+100", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. CIRCULAR QUICK ACTION PODS (Matching the 4 round pods from the reference UI)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pod 1: Aceite
                        QuickMetricPod(
                            iconEmoji = "🛢️",
                            label = "Aceite",
                            valueText = "${oilHealth?.remainingKm ?: 1500} km",
                            isWarning = (oilHealth?.remainingKm ?: 1000) < 500,
                            onClick = {
                                if (oilHealth != null) componentToEdit = oilHealth
                                else showMileageDialog = true
                            }
                        )

                        // Pod 2: Frenos
                        val brakeLife = brakeHealth?.let { ((1f - it.progress) * 100).toInt().coerceIn(0, 100) } ?: 85
                        QuickMetricPod(
                            iconEmoji = "🛑",
                            label = "Frenos",
                            valueText = "$brakeLife%",
                            isWarning = brakeLife < 30,
                            onClick = {
                                if (brakeHealth != null) componentToEdit = brakeHealth
                            }
                        )

                        // Pod 3: Llantas
                        val tireLife = tireHealth?.let { ((1f - it.progress) * 100).toInt().coerceIn(0, 100) } ?: 90
                        QuickMetricPod(
                            iconEmoji = "⚙️",
                            label = "Llantas",
                            valueText = "$tireLife%",
                            isWarning = tireLife < 30,
                            onClick = {
                                if (tireHealth != null) componentToEdit = tireHealth
                            }
                        )

                        // Pod 4: SOAT / Documentos
                        QuickMetricPod(
                            iconEmoji = "🛡️",
                            label = "SOAT",
                            valueText = "${uiState.soatStatus?.daysRemaining ?: 365} d",
                            isWarning = (uiState.soatStatus?.daysRemaining ?: 365) < 30,
                            onClick = {
                                documentToEdit = Pair("SOAT", uiState.soatStatus?.daysRemaining ?: 365)
                            }
                        )
                    }
                }

                // 3. COACH CARD / INSIGHT CARD (Matching LifeOS Design System)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryBrand)
                                    )
                                    Text(
                                        text = "COACH INTELIGENTE MOTO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryBrand,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PrimaryBrand.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Gasto Optimizado",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBrand
                                    )
                                }
                            }

                            Text(
                                text = "Tu costo de rodaje es de ${formatCop.format(moto.costPerKm)}/km. Se sugiere provisionar ${formatCop.format(uiState.monthlyReserveEstimated)} al mes para cubrir cambios de aceite, kit de arrastre y documentos sin desajustar tus finanzas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 21.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showCostPerKmDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrand)
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fondo COP/km", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showMileageDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBrand)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Odómetro", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // 4. "PLAN DE MANTENIMIENTO" - COMPONENTES
                item {
                    Text(
                        text = "Plan de Mantenimiento",
                        style = MaterialTheme.typography.titleLarge,
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
                                "Llanta Trasera" -> viewModel.editTireSettings(true, moto.currentMileage, component.intervalKm)
                                "Llanta Delantera" -> viewModel.editTireSettings(false, moto.currentMileage, component.intervalKm)
                            }
                        },
                        onEditValues = {
                            componentToEdit = component
                        }
                    )
                }

                // 5. DOCUMENTACIÓN LEGAL
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Documentación Legal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Toca para editar fechas",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBrand
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DocumentCard(
                            status = uiState.soatStatus,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    documentToEdit = Pair("SOAT", uiState.soatStatus?.daysRemaining ?: 365)
                                }
                        )
                        DocumentCard(
                            status = uiState.technoStatus,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    documentToEdit = Pair("Tecnomecánica", uiState.technoStatus?.daysRemaining ?: 365)
                                }
                        )
                    }
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
            title = { Text("Actualizar Kilometraje Total") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ingresa la lectura actual del odómetro de tu moto:")
                    OutlinedTextField(
                        value = inputMileage,
                        onValueChange = { inputMileage = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Kilómetros totales (km)") },
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrand)
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

    // Dialog for Editing Component (Exact km and interval)
    if (componentToEdit != null && moto != null) {
        val comp = componentToEdit!!
        var inputLastKm by remember { mutableStateOf(comp.lastServiceMileage.toString()) }
        var inputInterval by remember { mutableStateOf(comp.intervalKm.toString()) }

        AlertDialog(
            onDismissRequest = { componentToEdit = null },
            title = { Text("Editar: ${comp.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputLastKm,
                        onValueChange = { inputLastKm = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Último cambio/servicio a los (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputInterval,
                        onValueChange = { inputInterval = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Intervalo / Vida útil recomendada (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lastKm = inputLastKm.toIntOrNull() ?: 0
                        val interval = inputInterval.toIntOrNull() ?: 1000
                        when (comp.name) {
                            "Aceite de Motor" -> viewModel.editOilSettings(lastKm, interval)
                            "Cadena (Lubricación)" -> viewModel.editChainSettings(lastKm, interval)
                            "Pastillas de Freno" -> viewModel.editBrakeSettings(lastKm, interval)
                            "Llanta Trasera" -> viewModel.editTireSettings(true, lastKm, interval)
                            "Llanta Delantera" -> viewModel.editTireSettings(false, lastKm, interval)
                        }
                        componentToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrand)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { componentToEdit = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog for Document Expiry Days
    if (documentToEdit != null) {
        val (docName, initialDays) = documentToEdit!!
        var inputDays by remember { mutableStateOf(initialDays.toString()) }

        AlertDialog(
            onDismissRequest = { documentToEdit = null },
            title = { Text("Vigencia de $docName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ingresa los días que le quedan de vigencia a tu $docName:")
                    OutlinedTextField(
                        value = inputDays,
                        onValueChange = { inputDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Días restantes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = inputDays.toIntOrNull() ?: 365
                        viewModel.editDocumentDays(docName == "SOAT", days)
                        documentToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrand)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToEdit = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog for Cost Per Km
    if (showCostPerKmDialog && moto != null) {
        var inputCost by remember { mutableStateOf(moto.costPerKm.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showCostPerKmDialog = false },
            title = { Text("Ahorro Preventivo por Kilómetro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿Cuántos pesos (COP) deseas destinar por cada km rodado a tu fondo de mantenimiento?")
                    OutlinedTextField(
                        value = inputCost,
                        onValueChange = { inputCost = it.filter { ch -> ch.isDigit() } },
                        label = { Text("COP por km (ej. 45)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cost = inputCost.toDoubleOrNull() ?: 45.0
                        viewModel.editCostPerKm(cost)
                        showCostPerKmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrand)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCostPerKmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog for Editing Moto Details
    if (showEditInfoDialog && moto != null) {
        var inputName by remember { mutableStateOf(moto.name) }
        var inputModel by remember { mutableStateOf(moto.model) }
        var inputOilInterval by remember { mutableStateOf(moto.oilChangeInterval.toString()) }

        AlertDialog(
            onDismissRequest = { showEditInfoDialog = false },
            title = { Text("Configuración de Moto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nombre o Referencia (ej. Yamaha FZ25)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputModel,
                        onValueChange = { inputModel = it },
                        label = { Text("Modelo / Año (ej. 2024)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputOilInterval,
                        onValueChange = { inputOilInterval = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Intervalo Cambio Aceite (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val interval = inputOilInterval.toIntOrNull() ?: 2500
                        viewModel.updateMotoInfo(inputName.trim(), inputModel.trim(), interval, moto.costPerKm)
                        showEditInfoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrand)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInfoDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Speech bubble styled seamlessly with LifeOS design tokens.
 */
@Composable
private fun LifeOSSpeechBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val bubbleBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = bubbleBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }

        // Tail triangle pointing down to the bike
        Canvas(modifier = Modifier.size(12.dp, 6.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, color = bubbleBg)
        }
    }
}

/**
 * Circular Quick Action Pod styled with LifeOS surface & brand colors.
 */
@Composable
private fun QuickMetricPod(
    iconEmoji: String,
    label: String,
    valueText: String,
    isWarning: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Main Circular Pod
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.2.dp,
                        color = if (isWarning) RoseRed.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconEmoji, fontSize = 24.sp)
            }

            // Small '+' action badge in LifeOS PrimaryBrand
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isWarning) RoseRed else PrimaryBrand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isWarning) RoseRed else MaterialTheme.colorScheme.onSurfaceVariant
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
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
    onPerformService: () -> Unit,
    onEditValues: () -> Unit
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            text = "Último a los: ${health.lastServiceMileage} km",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onPerformService()
                                expanded = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hice cambio hoy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onEditValues()
                                expanded = false
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar datos", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
