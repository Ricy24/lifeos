package com.example.andresfinanzas.ui.outings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.andresfinanzas.data.local.entities.VisitedPlaceEntity
import com.example.andresfinanzas.data.remote.models.OutingStopRemote
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingScreen(
    onBack: () -> Unit = {},
    viewModel: OutingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(0) } // 0: Planificador IA, 1: Radar Anti-Repetición
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var placeToEdit by remember { mutableStateOf<VisitedPlaceEntity?>(null) }
    var placeToDelete by remember { mutableStateOf<VisitedPlaceEntity?>(null) }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
    }

    // Handle feedback messages
    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Salidas, Citas & Mapas",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Inteligencia Financiera & Ocio Seguro",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncWithBackend() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs: Planificador IA vs Radar Anti-Repetición
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Planificador IA ✨", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Radar Anti-Repetición", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${uiState.visitedPlaces.size}", fontSize = 11.sp)
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> OutingPlannerTab(
                    uiState = uiState,
                    currencyFormatter = currencyFormatter,
                    onGeneratePlan = { type, budget, area, prefs, useLoc, lat, lon, rad ->
                        viewModel.generatePlan(type, budget, area, prefs, useLoc, lat, lon, rad)
                    },
                    onOpenMaps = { mapsUrl ->
                        openUrlInBrowserOrMaps(context, mapsUrl)
                    },
                    onSaveStopAsVisited = { stop ->
                        viewModel.addPlaceFromStop(stop)
                    }
                )
                1 -> VisitedPlacesRadarTab(
                    places = uiState.visitedPlaces,
                    currencyFormatter = currencyFormatter,
                    onAddNewPlace = { showAddPlaceDialog = true },
                    onEditPlace = { placeToEdit = it },
                    onDeletePlace = { placeToDelete = it },
                    onOpenMaps = { url ->
                        openUrlInBrowserOrMaps(context, url)
                    }
                )
            }
        }
    }

    // Dialog for adding a new visited place manually
    if (showAddPlaceDialog) {
        VisitedPlaceEditDialog(
            title = "Registrar Lugar Visitado",
            initialPlace = null,
            onDismiss = { showAddPlaceDialog = false },
            onConfirm = { name, category, address, rating, cost, notes, mapsUrl ->
                viewModel.addCustomPlace(
                    name = name,
                    category = category,
                    addressOrArea = address,
                    rating = rating,
                    averageCost = cost,
                    notes = notes,
                    mapsUrl = mapsUrl
                )
                showAddPlaceDialog = false
            }
        )
    }

    // Dialog for editing ANY field of an existing place (User requirement: full editability)
    placeToEdit?.let { place ->
        VisitedPlaceEditDialog(
            title = "Editar Lugar Visitado",
            initialPlace = place,
            onDismiss = { placeToEdit = null },
            onConfirm = { name, category, address, rating, cost, notes, mapsUrl ->
                val updated = place.copy(
                    name = name,
                    category = category,
                    addressOrArea = address,
                    rating = rating,
                    averageCost = cost,
                    notes = notes,
                    mapsUrl = mapsUrl
                )
                viewModel.updatePlace(updated)
                placeToEdit = null
            }
        )
    }

    // Confirmation dialog for deleting a place
    placeToDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { placeToDelete = null },
            title = { Text("Eliminar del Radar") },
            text = { Text("¿Deseas eliminar '${place.name}'? La IA podría volver a sugerirlo en futuras salidas.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlace(place.id)
                        placeToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { placeToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun OutingPlannerTab(
    uiState: OutingUiState,
    currencyFormatter: NumberFormat,
    onGeneratePlan: (
        type: String,
        budget: Double?,
        area: String,
        prefs: String?,
        useLoc: Boolean,
        lat: Double?,
        lon: Double?,
        rad: Int
    ) -> Unit,
    onOpenMaps: (String) -> Unit,
    onSaveStopAsVisited: (OutingStopRemote) -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf("Cita Romántica") }
    var areaOrCity by remember { mutableStateOf("Bogotá") }
    var customBudgetStr by remember { mutableStateOf("") }
    var selectedBudgetPreset by remember { mutableStateOf<Double?>(null) }
    var preferencesText by remember { mutableStateOf("") }

    // GPS & Location state
    var useCurrentLocation by remember { mutableStateOf(true) }
    var currentLatitude by remember { mutableStateOf<Double?>(null) }
    var currentLongitude by remember { mutableStateOf<Double?>(null) }
    var selectedRadiusKm by remember { mutableStateOf(3) }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var locationStatusMessage by remember { mutableStateOf<String?>(null) }

    // View mode for results: 0 = Interactive Map, 1 = Stop List
    var resultViewMode by remember { mutableStateOf(0) }
    var selectedStopIndex by remember { mutableStateOf(0) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Location fetcher helper with high accuracy
    val requestLocationUpdates: () -> Unit = {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            isDetectingLocation = true
            locationStatusMessage = "Detectando satélites GPS precisos..."
            try {
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            currentLatitude = loc.latitude
                            currentLongitude = loc.longitude
                            isDetectingLocation = false
                            locationStatusMessage = "GPS Conectado (${String.format(Locale.US, "%.3f", loc.latitude)}, ${String.format(Locale.US, "%.3f", loc.longitude)})"
                        } else {
                            // Fallback to lastLocation
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                isDetectingLocation = false
                                if (lastLoc != null) {
                                    currentLatitude = lastLoc.latitude
                                    currentLongitude = lastLoc.longitude
                                    locationStatusMessage = "GPS Conectado"
                                } else {
                                    locationStatusMessage = "Enciende la ubicación de tu teléfono"
                                }
                            }.addOnFailureListener {
                                isDetectingLocation = false
                                locationStatusMessage = "No se pudo leer la ubicación"
                            }
                        }
                    }.addOnFailureListener {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            isDetectingLocation = false
                            if (lastLoc != null) {
                                currentLatitude = lastLoc.latitude
                                currentLongitude = lastLoc.longitude
                                locationStatusMessage = "GPS Conectado"
                            } else {
                                locationStatusMessage = "Enciende la ubicación de tu teléfono"
                            }
                        }.addOnFailureListener {
                            isDetectingLocation = false
                            locationStatusMessage = "Error al leer GPS"
                        }
                    }
            } catch (e: SecurityException) {
                isDetectingLocation = false
                locationStatusMessage = "Permiso de ubicación denegado"
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            useCurrentLocation = true
            requestLocationUpdates()
        } else {
            useCurrentLocation = false
            locationStatusMessage = "Permiso de ubicación no concedido"
        }
    }

    // Auto-check on launch if permission already granted
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            requestLocationUpdates()
        }
    }

    val outingTypes = listOf(
        "Cita Romántica" to "💖",
        "Rodada en Moto" to "🏍️",
        "Tarde de Amigos" to "🍻",
        "Café & Charla" to "☕",
        "Gourmet & Cena" to "🍽️"
    )

    val budgetPresets = listOf(
        30000.0 to "$30.000 COP",
        60000.0 to "$60.000 COP",
        100000.0 to "$100.000 COP",
        uiState.safeDiscretionaryBudget to "Seguro LifeOS (${currencyFormatter.format(uiState.safeDiscretionaryBudget)})"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safe Discretionary Budget Meter
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Presupuesto Seguro de Ocio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            currencyFormatter.format(uiState.safeDiscretionaryBudget),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Calculado sin poner en riesgo tus compromisos ni deudas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Configuration Card: Filters for Outing Type, Location & Price
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Filter 1: Outing Type
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "1. Tipo de Salida o Plan",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(outingTypes) { (type, emoji) ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    label = { Text("$emoji $type") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Filter 2: Location & Proximity (GPS Permission handling)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "2. Ubicación & Proximidad",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (useCurrentLocation && currentLatitude != null) "📍 GPS Detectado con precisión"
                                    else if (isDetectingLocation) "🛰️ Conectando con satélite GPS..."
                                    else if (useCurrentLocation) "📍 Toca para activar permiso GPS"
                                    else "Búsqueda por ciudad o barrio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (useCurrentLocation && currentLatitude != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = useCurrentLocation,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        if (hasFine || hasCoarse) {
                                            useCurrentLocation = true
                                            requestLocationUpdates()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                            )
                                        }
                                    } else {
                                        useCurrentLocation = false
                                    }
                                }
                            )
                        }

                        // Prominent button to grant permission if not yet granted
                        val hasLocPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                        if (useCurrentLocation && !hasLocPerm) {
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Permitir acceso a mi Ubicación GPS", fontWeight = FontWeight.Bold)
                            }
                        } else if (useCurrentLocation && currentLatitude == null && !isDetectingLocation) {
                            OutlinedButton(
                                onClick = { requestLocationUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Reintentar capturar GPS actual")
                            }
                        }

                        // Radius selection chips (1km, 3km, 5km, 10km, 15km)
                        if (useCurrentLocation) {
                            Text(
                                "Radio máximo a tu alrededor:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val radii = listOf(1, 3, 5, 10, 15)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(radii) { rad ->
                                    FilterChip(
                                        selected = selectedRadiusKm == rad,
                                        onClick = { selectedRadiusKm = rad },
                                        label = { Text("$rad km") },
                                        leadingIcon = if (selectedRadiusKm == rad) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = areaOrCity,
                                onValueChange = { areaOrCity = it },
                                label = { Text("Zona o Ciudad") },
                                placeholder = { Text("ej. Usaquén, La Calera, Chapinero...") },
                                leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Filter 3: Budget / Price Level
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "3. Presupuesto & Precio",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(budgetPresets) { (presetValue, label) ->
                                FilterChip(
                                    selected = selectedBudgetPreset == presetValue && customBudgetStr.isEmpty(),
                                    onClick = {
                                        selectedBudgetPreset = presetValue
                                        customBudgetStr = ""
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customBudgetStr,
                            onValueChange = {
                                customBudgetStr = it.filter { ch -> ch.isDigit() }
                                if (customBudgetStr.isNotEmpty()) selectedBudgetPreset = null
                            },
                            label = { Text("O escribe un presupuesto en COP") },
                            placeholder = { Text("ej. 50000") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // Preferences (optional)
                    OutlinedTextField(
                        value = preferencesText,
                        onValueChange = { preferencesText = it },
                        label = { Text("Preferencias opcionales") },
                        placeholder = { Text("ej. Con parqueadero de motos, terraza, pet-friendly...") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    // Action Button: Search & View
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                            if (useCurrentLocation && !hasPermission) {
                                locationPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                                return@Button
                            }
                            if (useCurrentLocation && (currentLatitude == null || currentLongitude == null)) {
                                requestLocationUpdates()
                            }
                            val finalBudget = customBudgetStr.toDoubleOrNull() ?: selectedBudgetPreset
                            onGeneratePlan(
                                selectedType,
                                finalBudget,
                                areaOrCity,
                                preferencesText,
                                useCurrentLocation,
                                currentLatitude,
                                currentLongitude,
                                selectedRadiusKm
                            )
                        },
                        enabled = !uiState.isGeneratingPlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (uiState.isGeneratingPlan) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Buscando lugares reales en Maps con IA...")
                        } else {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscar Lugares & Ver en Mapa", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // Generated Plan Display
        uiState.currentPlan?.let { plan ->
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                plan.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            plan.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Presupuesto Estimado Total:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                currencyFormatter.format(plan.total_estimated_cost),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // View Mode Selector: 🗺️ Ver en Mapa vs 📋 Lista
            item {
                TabRow(
                    selectedTabIndex = resultViewMode,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                ) {
                    Tab(
                        selected = resultViewMode == 0,
                        onClick = { resultViewMode = 0 },
                        text = { Text("🗺️ Ver en Mapa Interactivo", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = resultViewMode == 1,
                        onClick = { resultViewMode = 1 },
                        text = { Text("📋 Lista de Paradas (${plan.stops.size})", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Mode 0: Interactive Google Map with Marker Selection & Floating Detail Card
            if (resultViewMode == 0) {
                item {
                    val userLatLng = remember(currentLatitude, currentLongitude) {
                        if (currentLatitude != null && currentLongitude != null) LatLng(currentLatitude!!, currentLongitude!!)
                        else null
                    }
                    val firstStopCoord = remember(plan) {
                        val s = plan.stops.firstOrNull { it.latitude != null && it.longitude != null }
                        if (s != null) LatLng(s.latitude!!, s.longitude!!) else null
                    }
                    val mapCenter = userLatLng ?: firstStopCoord ?: LatLng(4.6097, -74.0817)

                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(mapCenter, if (selectedRadiusKm <= 3) 14.5f else 13.5f)
                    }

                    val currentSelectedStop = plan.stops.getOrNull(selectedStopIndex) ?: plan.stops.firstOrNull()

                    // Move camera when selected stop changes
                    LaunchedEffect(selectedStopIndex) {
                        currentSelectedStop?.let { stop ->
                            if (stop.latitude != null && stop.longitude != null) {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(stop.latitude, stop.longitude),
                                        15.5f
                                    )
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Google Map Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(22.dp))
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                properties = MapProperties(isMyLocationEnabled = userLatLng != null),
                                uiSettings = MapUiSettings(
                                    zoomControlsEnabled = false,
                                    myLocationButtonEnabled = true
                                )
                            ) {
                                // Draw search radius circle around user
                                userLatLng?.let { uPos ->
                                    Circle(
                                        center = uPos,
                                        radius = (selectedRadiusKm * 1000).toDouble(),
                                        fillColor = Color(0x221E88E5),
                                        strokeColor = Color(0xFF1E88E5),
                                        strokeWidth = 3f
                                    )
                                }

                                // Place markers on map
                                plan.stops.forEachIndexed { idx, stop ->
                                    if (stop.latitude != null && stop.longitude != null) {
                                        Marker(
                                            state = MarkerState(position = LatLng(stop.latitude, stop.longitude)),
                                            title = "${stop.order}. ${stop.title}",
                                            snippet = "${currencyFormatter.format(stop.estimated_cost)} • ⭐ ${stop.rating ?: 4.8}",
                                            onClick = {
                                                selectedStopIndex = idx
                                                false
                                            }
                                        )
                                    }
                                }
                            }

                            // Overlay badge indicating places on map
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${plan.stops.size} lugares en radio de $selectedRadiusKm km",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Floating / Detail Venue Card for the currently selected stop
                        currentSelectedStop?.let { stop ->
                            VenueMapDetailCard(
                                stop = stop,
                                currentIndex = selectedStopIndex,
                                totalStops = plan.stops.size,
                                currencyFormatter = currencyFormatter,
                                onPrevious = {
                                    if (selectedStopIndex > 0) selectedStopIndex--
                                    else selectedStopIndex = plan.stops.size - 1
                                },
                                onNext = {
                                    if (selectedStopIndex < plan.stops.size - 1) selectedStopIndex++
                                    else selectedStopIndex = 0
                                },
                                onOpenMaps = onOpenMaps,
                                onSaveVisited = onSaveStopAsVisited
                            )
                        }
                    }
                }
            }

            // Mode 1: Detailed List of Stop Cards
            if (resultViewMode == 1) {
                items(plan.stops) { stop ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!stop.image_url.isNullOrBlank()) {
                                AsyncImage(
                                    model = stop.image_url,
                                    contentDescription = stop.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${stop.order}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                stop.title,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                stop.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }

                                    Text(
                                        currencyFormatter.format(stop.estimated_cost),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB800),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "${stop.rating ?: 4.8}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "(${stop.review_count ?: 120} reseñas en Google)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    stop.distance_km?.let { dist ->
                                        Text(
                                            " • 📍 ${String.format(Locale.US, "%.1f", dist)} km",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    stop.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                stop.highlight_review?.let { review ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.RateReview,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    "Reseña destacada:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "\"$review\"",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onOpenMaps(stop.maps_url) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Google Maps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    OutlinedButton(
                                        onClick = { onSaveStopAsVisited(stop) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Ya Fuí / Guardar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VenueMapDetailCard(
    stop: OutingStopRemote,
    currentIndex: Int,
    totalStops: Int,
    currencyFormatter: NumberFormat,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenMaps: (String) -> Unit,
    onSaveVisited: (OutingStopRemote) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo with category badge & navigation arrows
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (!stop.image_url.isNullOrBlank()) {
                    AsyncImage(
                        model = stop.image_url,
                        contentDescription = stop.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    }
                }

                // Place Counter Pill (e.g. 1 / 3)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        "Lugar ${currentIndex + 1} de $totalStops",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Next/Previous arrows over the image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", tint = Color.White)
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Siguiente", tint = Color.White)
                    }
                }
            }

            // Information Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Name & Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stop.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stop.address ?: stop.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        currencyFormatter.format(stop.estimated_cost),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Distance & Rating Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${stop.rating ?: 4.8}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(" (${stop.review_count ?: 120})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    stop.distance_km?.let { dist ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "📍 ${String.format(Locale.US, "%.1f", dist)} km de ti",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Description
                Text(
                    stop.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenMaps(stop.maps_url) },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cómo Llegar (Maps)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onSaveVisited(stop) },
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Guardar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitedPlacesRadarTab(
    places: List<VisitedPlaceEntity>,
    currencyFormatter: NumberFormat,
    onAddNewPlace: () -> Unit,
    onEditPlace: (VisitedPlaceEntity) -> Unit,
    onDeletePlace: (VisitedPlaceEntity) -> Unit,
    onOpenMaps: (String) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Info Header Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Radar Anti-Repetición",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Cada lugar registrado aquí se envía a la IA como restricción negativa para que nunca repitas planes ni caigas en la monotonía.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Add Button
        item {
            Button(
                onClick = onAddNewPlace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Registrar Nuevo Lugar Visitado", fontWeight = FontWeight.Bold)
            }
        }

        if (places.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ExploreOff,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No hay lugares registrados aún.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Genera un plan con IA o agrega manualmente los sitios a los que ya has ido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(places, key = { it.id }) { place ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title row + category badge + actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    place.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            place.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (place.addressOrArea.isNotBlank()) {
                                        Text(
                                            "• ${place.addressOrArea}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Action icons (Maps, Edit, Delete)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                place.mapsUrl?.let { url ->
                                    if (url.isNotBlank()) {
                                        IconButton(onClick = { onOpenMaps(url) }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                Icons.Default.Navigation,
                                                contentDescription = "Maps",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { onEditPlace(place) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onDeletePlace(place) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Rating & Cost Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Star Rating display
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { index ->
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (index < place.rating) Color(0xFFFFB800) else Color.Gray.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${place.rating}/5",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (place.averageCost > 0) {
                                Text(
                                    "Gasto aprox: ${currencyFormatter.format(place.averageCost)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Notes if present
                        place.notes?.let { notes ->
                            if (notes.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        // Visited Date
                        Text(
                            "Visitado el ${dateFormatter.format(Date(place.visitedDate))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Universal Dialog for Adding or Editing ANY field of a Visited Place.
 * Fulfills the user requirement: "tienes que dejar que absolutamente todo se pueda editar".
 */
@Composable
private fun VisitedPlaceEditDialog(
    title: String,
    initialPlace: VisitedPlaceEntity?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        category: String,
        address: String,
        rating: Int,
        cost: Double,
        notes: String?,
        mapsUrl: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialPlace?.name ?: "") }
    var category by remember { mutableStateOf(initialPlace?.category ?: "Restaurante") }
    var address by remember { mutableStateOf(initialPlace?.addressOrArea ?: "") }
    var rating by remember { mutableStateOf(initialPlace?.rating ?: 5) }
    var costStr by remember { mutableStateOf(if (initialPlace != null && initialPlace.averageCost > 0) initialPlace.averageCost.toLong().toString() else "") }
    var notes by remember { mutableStateOf(initialPlace?.notes ?: "") }
    var mapsUrl by remember { mutableStateOf(initialPlace?.mapsUrl ?: "") }

    val categories = listOf("Restaurante", "Café", "Bar", "Mirador", "Actividad", "Paseo", "Postres")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Sitio *") },
                        placeholder = { Text("ej. Café San Alberto") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Categoría:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Ubicación o Barrio") },
                        placeholder = { Text("ej. Usaquén Calle 119") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Calificación:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) { starIndex ->
                            val starNumber = starIndex + 1
                            IconButton(onClick = { rating = starNumber }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "$starNumber estrellas",
                                    tint = if (starNumber <= rating) Color(0xFFFFB800) else Color.Gray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Costo Aprox Pagado (COP)") },
                        placeholder = { Text("ej. 60000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = mapsUrl,
                        onValueChange = { mapsUrl = it },
                        label = { Text("Link de Google Maps (Opcional)") },
                        placeholder = { Text("https://maps.google.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas o Recomendaciones") },
                        placeholder = { Text("ej. Pedir la mesa de la terraza, fácil parqueo...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val parsedCost = costStr.toDoubleOrNull() ?: 0.0
                        onConfirm(
                            name.trim(),
                            category,
                            address.trim(),
                            rating,
                            parsedCost,
                            notes.trim().ifEmpty { null },
                            mapsUrl.trim().ifEmpty { null }
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Utility helper to launch Google Maps or fallback web browser with the given maps URL.
 */
private fun openUrlInBrowserOrMaps(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback or error handled silently
    }
}
