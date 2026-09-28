package com.example.andresfinanzas.ui.metrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.ui.theme.ElectricIndigo
import com.example.andresfinanzas.ui.theme.EmeraldGreen
import com.example.andresfinanzas.ui.theme.PrimaryBrand
import com.example.andresfinanzas.ui.theme.RoseRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MetricsInsightsScreen(
    transactions: List<TransactionEntity>,
    totalBalance: Double,
    netWorth: Double,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val format = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
    }

    var selectedMonthOffset by remember { mutableIntStateOf(0) }

    val monthCalendar = remember(selectedMonthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, selectedMonthOffset)
        }
    }

    val currentMonthName = remember(monthCalendar) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("es", "CO"))
        sdf.format(monthCalendar.time).replaceFirstChar { it.uppercase() }
    }

    // Calculations
    val totalIncome = remember(transactions) {
        transactions.filter { it.transactionType == "income" }.sumOf { it.amount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.transactionType == "expense" }.sumOf { it.amount }
    }
    val netCashflow = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) {
        (((totalIncome - totalExpense) / totalIncome) * 100).coerceIn(0.0, 100.0)
    } else 0.0

    // Financial Health Score (0 to 100)
    val healthScore = remember(totalIncome, totalExpense, totalBalance, savingsRate) {
        var score = 50
        if (totalBalance > 0) score += 20
        if (savingsRate > 20) score += 20 else if (savingsRate > 0) score += 10
        if (totalExpense > totalIncome && totalIncome > 0) score -= 30
        score.coerceIn(10, 100)
    }

    // Category Breakdown
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
        // 1. Top Bar: "My Diary" / "Métricas" + Date Navigator (< 📅 Month >)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Diary",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )

                // Date Navigator Capsule (< 📅 Date >)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { selectedMonthOffset-- },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Mes anterior",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = currentMonthName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = { selectedMonthOffset++ },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Mes siguiente",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Hero Metric Card ("Mediterranean diet" style card with circular arc gauge)
        item {
            HeroDietStyleMetricCard(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                netCashflow = netCashflow,
                savingsRate = savingsRate,
                format = format
            )
        }

        // 3. Arched Asymmetrical Category Cards ("Meals today" style with 3D badges)
        item {
            ArchedCategoryCardsSection(
                categoryBreakdown = categoryBreakdown,
                format = format
            )
        }

        // 4. Financial Health Score ("Body measurement" style)
        item {
            HealthScoreMetricCard(healthScore = healthScore)
        }

        // 5. Smart AI Recommendations
        item {
            SmartRecommendationsCard(
                transactions = transactions,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                savingsRate = savingsRate,
                format = format
            )
        }

        // 6. Category Breakdown List
        if (categoryBreakdown.isNotEmpty()) {
            item {
                Text(
                    text = "Detalle por Categoría",
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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
                            color = PrimaryBrand,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

/**
 * Hero Card matching the reference "Mediterranean diet" card with Circular Arc Gauge & Nutrition/Finance Bars
 */
@Composable
private fun HeroDietStyleMetricCard(
    totalIncome: Double,
    totalExpense: Double,
    netCashflow: Double,
    savingsRate: Double,
    format: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: "Presupuesto Mensual" + "Details ->"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Balance & Presupuesto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Detalles",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Middle: Left metrics (Ingresos / Gastos) + Right Circular Arc Gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left metrics column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Ingresos (Blue water drop / diamond indicator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF60A5FA).copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("💧", fontSize = 14.sp)
                            }
                        }
                        Column {
                            Text("Ingresos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = format.format(totalIncome),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Gastos (Orange flame indicator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF87171).copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🔥", fontSize = 14.sp)
                            }
                        }
                        Column {
                            Text("Gastos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = format.format(totalExpense),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Right: Circular Arc Gauge ("1503 kcal left" style)
                val expenseRatio = if (totalIncome > 0) (totalExpense / totalIncome).toFloat().coerceIn(0f, 1f) else 0f
                val sweepAngle = (expenseRatio * 260f).coerceAtLeast(10f)

                Box(
                    modifier = Modifier.size(125.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val arcBrush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF818CF8),
                            Color(0xFF6366F1),
                            Color(0xFF4F46E5)
                        )
                    )

                    Canvas(modifier = Modifier.size(115.dp)) {
                        val strokeWidth = 9.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)

                        // Background track arc (open at bottom)
                        drawArc(
                            color = trackColor,
                            startAngle = 140f,
                            sweepAngle = 260f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Active progress arc
                        drawArc(
                            brush = arcBrush,
                            startAngle = 140f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Text inside circular arc
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${((1f - expenseRatio) * 100).toInt()}%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "libre",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom: 3 Mini Nutrition-style Macro Bars (Fijo, Variable, Ahorro)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Fixed Costs
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fijo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("50% meta", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF60A5FA))
                    )
                }

                // Variable Costs
                Column(modifier = Modifier.weight(1f)) {
                    Text("Variable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("30% meta", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFF59E0B))
                    )
                }

                // Savings
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ahorro", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${savingsRate.toInt()}% actual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(EmeraldGreen)
                    )
                }
            }
        }
    }
}

/**
 * Arched Asymmetrical Cards matching the reference "Meals today" (Breakfast, Lunch, Snack)
 * Features iconic topEnd curve (64.dp) and floating 3D icons popping over the top rim!
 */
@Composable
private fun ArchedCategoryCardsSection(
    categoryBreakdown: List<Triple<String, Double, Float>>,
    format: NumberFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gastos por Categoría",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Personalizar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Signature Asymmetrical Arched Shape (topEnd = 64.dp, others = 22.dp)
        val archedShape = RoundedCornerShape(
            topStart = 22.dp,
            topEnd = 64.dp,
            bottomStart = 22.dp,
            bottomEnd = 22.dp
        )

        // Preset theme styles matching Breakfast (Coral/Orange), Lunch (Blue/Periwinkle), Snack (Pink/Fuchsia), Dinner (Teal/Emerald)
        val presets = listOf(
            ArchedCategoryCardData(
                title = "Alimentación",
                subtitle = "Restaurante, Café, Mercado",
                emoji = "🍳",
                gradient = listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88), Color(0xFFFF99AC)),
                defaultAmount = 525000.0
            ),
            ArchedCategoryCardData(
                title = "Transporte",
                subtitle = "Gasolina, Uber, Moto",
                emoji = "🥗",
                gradient = listOf(Color(0xFF818CF8), Color(0xFF6366F1), Color(0xFF4F46E5)),
                defaultAmount = 602000.0
            ),
            ArchedCategoryCardData(
                title = "Ocio & Compras",
                subtitle = "Salidas, Cine, Ropa",
                emoji = "🍉",
                gradient = listOf(Color(0xFFFF758C), Color(0xFFFF7EB3)),
                defaultAmount = 350000.0,
                hasAddButton = true
            ),
            ArchedCategoryCardData(
                title = "Servicios",
                subtitle = "Luz, Agua, Celular",
                emoji = "💡",
                gradient = listOf(Color(0xFF34D399), Color(0xFF059669)),
                defaultAmount = 210000.0
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { preset ->
                // Check if user has recorded expenses matching this category
                val realAmount = categoryBreakdown.find {
                    it.first.contains(preset.title.take(4), ignoreCase = true)
                }?.second ?: preset.defaultAmount

                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .height(210.dp)
                        .padding(top = 14.dp) // Leave space for the floating 3D avatar popping out
                ) {
                    // Main Arched Card Body
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = archedShape,
                        color = Color.Transparent,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(preset.gradient))
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Top spacer to accommodate floating 3D icon
                                Spacer(modifier = Modifier.height(18.dp))

                                // Title & Subtitle description
                                Column {
                                    Text(
                                        text = preset.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.subtitle,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Bottom: Amount or Add Action Button
                                if (preset.hasAddButton) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = format.format(realAmount),
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Añadir",
                                                    tint = preset.gradient.first(),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = format.format(realAmount),
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    // Floating 3D Icon popping out at the top-left edge
                    Surface(
                        modifier = Modifier
                            .offset(x = 14.dp, y = (-12).dp)
                            .size(46.dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = preset.emoji,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ArchedCategoryCardData(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val gradient: List<Color>,
    val defaultAmount: Double,
    val hasAddButton: Boolean = false
)

/**
 * Financial Health Score Card ("Body measurement" style)
 */
@Composable
private fun HealthScoreMetricCard(healthScore: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                healthScore >= 80 -> "Excelente disciplina financiera 🎉"
                                healthScore >= 60 -> "Buen control de ingresos y gastos 👍"
                                else -> "Oportunidad de optimizar gastos hormiga ⚠️"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = when {
                            healthScore >= 80 -> EmeraldGreen.copy(alpha = 0.18f)
                            healthScore >= 60 -> ElectricIndigo.copy(alpha = 0.18f)
                            else -> RoseRed.copy(alpha = 0.18f)
                        },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$healthScore",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    healthScore >= 80 -> EmeraldGreen
                                    healthScore >= 60 -> ElectricIndigo
                                    else -> RoseRed
                                }
                            )
                        }
                    }
                }

                // Smooth Progress Bar
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

/**
 * Smart AI Recommendations Card
 */
@Composable
private fun SmartRecommendationsCard(
    transactions: List<TransactionEntity>,
    totalIncome: Double,
    totalExpense: Double,
    savingsRate: Double,
    format: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
                    tint = PrimaryBrand
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
                    "Prioriza liquidar deudas de alto interés antes de incurrir en nuevas compras a cuotas."
                )
                savingsRate >= 20 -> listOf(
                    "¡Excelente disciplina! Estás ahorrando más del 20% de tus ingresos (${savingsRate.toInt()}%). Considera canalizar ese excedente a tu meta principal.",
                    "Mantén un fondo de emergencias equivalente a 3-6 meses de gastos fijos."
                )
                else -> listOf(
                    "Tu flujo de dinero está balanceado. Si reduces un 10% en gastos hormiga, podrás acelerar el cumplimiento de tus metas.",
                    "Revisa periódicamente tus suscripciones recurrentes para eliminar gastos que ya no utilizas."
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
