package com.example.andresfinanzas.ui.metrics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.ui.theme.ElectricIndigo
import com.example.andresfinanzas.ui.theme.EmeraldGreen
import com.example.andresfinanzas.ui.theme.RoseRed
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MetricsInsightsScreen(
    transactions: List<TransactionEntity>,
    totalBalance: Double,
    netWorth: Double
) {
    val context = LocalContext.current
    var telegramSentMessage by remember { mutableStateOf<String?>(null) }
    var isSendingTelegram by remember { mutableStateOf(false) }

    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
    }

    // Calculations
    val totalIncome = transactions.filter { it.transactionType == "income" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.transactionType == "expense" }.sumOf { it.amount }
    val netCashflow = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) {
        (((totalIncome - totalExpense) / totalIncome) * 100).coerceIn(0.0, 100.0)
    } else 0.0

    // Financial Health Score (0 to 100)
    val healthScore = remember(totalIncome, totalExpense, totalBalance) {
        var score = 50
        if (totalBalance > 0) score += 20
        if (savingsRate > 20) score += 20 else if (savingsRate > 0) score += 10
        if (totalExpense > totalIncome && totalIncome > 0) score -= 30
        score.coerceIn(10, 100)
    }

    // Expenses by Category
    val categoryBreakdown = remember(transactions) {
        val expenses = transactions.filter { it.transactionType == "expense" }
        val sumExpenses = expenses.sumOf { it.amount }
        expenses.groupBy { it.category }
            .map { (cat, list) ->
                val amount = list.sumOf { it.amount }
                val pct = if (sumExpenses > 0) (amount / sumExpenses * 100).toFloat() else 0f
                Triple(cat, amount, pct)
            }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Métricas & Recomendaciones",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Diagnóstico financiero en tiempo real y asistente con IA.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Financial Health Score Card
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
                                    ElectricIndigo.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Score de Salud Financiera",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        healthScore >= 80 -> "Excelente estado financiero 🎉"
                                        healthScore >= 60 -> "Buen control de ingresos y gastos 👍"
                                        else -> "Oportunidad de optimizar gastos ⚠️"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    healthScore >= 80 -> EmeraldGreen.copy(alpha = 0.2f)
                                    healthScore >= 60 -> ElectricIndigo.copy(alpha = 0.2f)
                                    else -> RoseRed.copy(alpha = 0.2f)
                                },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$healthScore",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            healthScore >= 80 -> EmeraldGreen
                                            healthScore >= 60 -> ElectricIndigo
                                            else -> RoseRed
                                        }
                                    )
                                }
                            }
                        }

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { healthScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                healthScore >= 80 -> EmeraldGreen
                                healthScore >= 60 -> ElectricIndigo
                                else -> RoseRed
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Monthly Financial Flow (Income vs Expense)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total Income
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Ingresos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = format.format(totalIncome),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }

                // Total Expenses
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = RoseRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Gastos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = format.format(totalExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoseRed
                        )
                    }
                }
            }
        }

        // Savings Rate & Runway
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tasa de Ahorro",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${savingsRate.toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (savingsRate >= 20) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Flujo Neto",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = format.format(netCashflow),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (netCashflow >= 0) EmeraldGreen else RoseRed
                        )
                    }
                }
            }
        }

        // Smart Recommendations Card (AI Insights)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricIndigo
                        )
                        Text(
                            text = "Recomendaciones Inteligentes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val recommendations = when {
                        transactions.isEmpty() -> listOf(
                            "Comienza registrando tus cuentas y tu primer gasto con el botón '+' para generar métricas personalizadas.",
                            "La regla ideal es destinar 50% a necesidades, 30% a deseos y 20% a ahorro/deudas."
                        )
                        totalExpense > totalIncome && totalIncome > 0 -> listOf(
                            "Alerta: Tus gastos actuales superan tus ingresos este mes por ${format.format(totalExpense - totalIncome)}. Te recomendamos reducir categorías no esenciales.",
                            "Prioriza pagar el saldo de tus tarjetas antes de incurrir en nuevos compromisos."
                        )
                        savingsRate >= 20 -> listOf(
                            "¡Excelente disciplina! Estás ahorrando más del 20% de tus ingresos (${savingsRate.toInt()}%). Considera mover ese excedente a tu meta principal.",
                            "Mantén un fondo de emergencias equivalente a 3-6 meses de gastos fijos."
                        )
                        else -> listOf(
                            "Tu flujo de dinero está balanceado. Si reduces un 10% en gastos hormiga, podrás acelerar el cumplimiento de tus metas.",
                            "Revisa periódicamente tus suscripciones recurrentes para eliminar gastos innecesarios."
                        )
                    }

                    recommendations.forEach { rec ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💡", fontSize = 16.sp)
                            Text(
                                text = rec,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Expenses by Category Breakdown
        if (categoryBreakdown.isNotEmpty()) {
            item {
                Text(
                    text = "Gastos por Categoría",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(categoryBreakdown.size) { index ->
                val (cat, amount, pct) = categoryBreakdown[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${format.format(amount)} (${pct.toInt()}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ElectricIndigo,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Telegram Bot Status & Test Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = TelegramBlue.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(22.dp)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = TelegramBlue.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TelegramBlue,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✈️", fontSize = 18.sp)
                            }
                        }
                        Column {
                            Text(
                                text = "Bot de Telegram LifeOS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Vinculado a tu chat personal",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "El bot de Telegram está programado para enviarte resúmenes diarios a las 10:00 PM y un balance semanal todos los domingos con tus saldos, metas y deudas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            // Open Telegram chat directly with the bot
                            val telegramIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/Andresfinanzas_bot")
                            )
                            try {
                                context.startActivity(telegramIntent)
                            } catch (e: Exception) {
                                // Fallback to browser
                                val webIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://web.telegram.org")
                                )
                                context.startActivity(webIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abrir Bot en Telegram (@LifeOS_bot)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
