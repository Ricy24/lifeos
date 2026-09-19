package com.example.andresfinanzas.ui.work

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun WorkSessionScreen(
    viewModel: WorkSessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var activityType by remember { mutableStateOf("dev") }
    var incomeInput by remember { mutableStateOf("") }
    var elapsedTime by remember { mutableStateOf(0L) }

    // Timer effect
    LaunchedEffect(uiState.activeSession) {
        while (uiState.activeSession != null) {
            val startTime = uiState.activeSession!!.startTime.time
            elapsedTime = System.currentTimeMillis() - startTime
            delay(1000)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Work / Income Engine", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Metrics Display
        uiState.metrics?.let { metrics ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Metrics", style = MaterialTheme.typography.titleMedium)
                    Text("Total Income: ${metrics.totalIncome}")
                    Text("Total Duration: ${metrics.totalDurationMinutes} mins")
                    Text("Income per Hour: ${metrics.incomePerHour ?: "N/A"}")
                    Text("Completed Sessions: ${metrics.completedSessionsCount}")
                }
            }
        }

        // Active Session Controller
        if (uiState.activeSession == null) {
            OutlinedTextField(
                value = activityType,
                onValueChange = { activityType = it },
                label = { Text("Activity Type") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.startSession(activityType) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Work Session")
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Session Active", style = MaterialTheme.typography.titleMedium)
                    
                    val seconds = (elapsedTime / 1000) % 60
                    val minutes = (elapsedTime / (1000 * 60)) % 60
                    val hours = (elapsedTime / (1000 * 60 * 60))
                    
                    Text("Elapsed Time: ${String.format("%02d:%02d:%02d", hours, minutes, seconds)}", style = MaterialTheme.typography.headlineSmall)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = incomeInput,
                        onValueChange = { incomeInput = it },
                        label = { Text("Income Earned (minor units)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val income = incomeInput.toIntOrNull() ?: 0
                            viewModel.stopSession(income)
                            incomeInput = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Session")
                    }
                }
            }
        }
    }
}
