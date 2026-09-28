package com.example.andresfinanzas.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.ui.theme.ElectricIndigo
import com.example.andresfinanzas.ui.theme.EmeraldGreen
import com.example.andresfinanzas.ui.theme.RoseRed
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenAccounts: () -> Unit,
    onOpenMetrics: () -> Unit,
    onOpenAddTransaction: () -> Unit,
    onOpenAddAccount: () -> Unit,
    onEditAccount: (AccountEntity) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenOutings: () -> Unit = {}
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    var isBalanceVisible by remember { mutableStateOf(true) }

    val format = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
    }

    val totalIncomeMonth = remember(uiState.allTransactions) {
        uiState.allTransactions.filter { it.transactionType == "income" }.sumOf { it.amount }
    }
    val totalExpenseMonth = remember(uiState.allTransactions) {
        uiState.allTransactions.filter { it.transactionType == "expense" }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Top Bar (Circular menu/theme button, Wallet title & subtitle, Circular "+" button)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left round icon button (Theme / Settings)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable { onToggleTheme() },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu / Tema",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Center Title & Subtitle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Wallet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "My Cards & Transaction",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // Right round icon button ("+" Quick Add Transaction)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable { onOpenAddTransaction() },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar Transacción",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 2. Hero 3D Cardholder Wallet with Peeking Card
        item {
            HeroWalletCardHolder(
                totalBalance = uiState.totalBalance,
                netWorth = uiState.netWorth,
                isBalanceVisible = isBalanceVisible,
                onToggleVisibility = { isBalanceVisible = !isBalanceVisible },
                onAddBalance = onOpenAddTransaction,
                onOpenAccounts = onOpenAccounts,
                format = format
            )
        }

        // 3. Quick Stats Mini Bar (Ingresos vs Gastos)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                    Column {
                        Text("Ingresos Mes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text(if (isBalanceVisible) format.format(totalIncomeMonth) else "••••", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = RoseRed.copy(alpha = 0.15f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = RoseRed, modifier = Modifier.size(16.dp))
                        }
                    }
                    Column {
                        Text("Gastos Mes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text(if (isBalanceVisible) format.format(totalExpenseMonth) else "••••", color = RoseRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // 4. Quick Top-Up (Cuentas Rápidas)
        item {
            QuickTopUpSection(
                accounts = uiState.accounts,
                onOpenAddAccount = onOpenAddAccount,
                onOpenAccounts = onOpenAccounts,
                onEditAccount = onEditAccount
            )
        }

        // 5. Latest Transactions (Horizontal scrolling cards from reference design)
        item {
            LatestTransactionsHorizontalSection(
                transactions = uiState.recentTransactions,
                isBalanceVisible = isBalanceVisible,
                onOpenMetrics = onOpenMetrics,
                onAddTransaction = onOpenAddTransaction,
                format = format
            )
        }

        // 6. Planificador de Salidas & Citas con Google Maps Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenOutings() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Salidas, Citas & Mapas ✨",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Planifica con IA según tu dinero libre y descubre sitios únicos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 7. Monthly Expenses Chart
        item {
            MonthlyExpensesSection(uiState.monthlyExpenses)
        }

        // 8. Detailed Transactions List
        item {
            RecentTransactionsSection(
                transactions = uiState.recentTransactions,
                onAddTransaction = onOpenAddTransaction
            )
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

/**
 * 3D-styled Layered Cardholder Wallet with Peeking Bank Card
 */
@Composable
private fun HeroWalletCardHolder(
    totalBalance: Double,
    netWorth: Double,
    isBalanceVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onAddBalance: () -> Unit,
    onOpenAccounts: () -> Unit,
    format: NumberFormat
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // 1. Back / Peeking Bank Card
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .height(115.dp)
                .offset(y = 0.dp),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF9370DB), // Light Purple / Lilac highlight
                                Color(0xFF7C3AED), // Vibrant Violet
                                Color(0xFF6366F1)  // Electric Indigo
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                // Subtle sheen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Andrés",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "•••• •••• 5678",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "VISA",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                        Text(
                            text = "Valid: 05/29",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Front Cardholder Pocket (Overlaps the card, creating the real 3D pocket slot)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 52.dp),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF5B21B6), // Deep Royal Purple
                                Color(0xFF4338CA), // Electric Indigo
                                Color(0xFF312E81)  // Dark Indigo Base
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                // Top curved pocket rim notch effect
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Pocket Header: Balance title + Net worth pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Balance",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Patrimonio: ${if (isBalanceVisible) format.format(netWorth) else "••••"}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Balance Amount
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isBalanceVisible) format.format(totalBalance) else "••••••••",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        if (isBalanceVisible) {
                            Text(
                                text = "COP",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    // Pocket Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "+ Add Balance" pill
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onAddBalance() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Add Balance",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right icons (Swap / Eye)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenAccounts() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = "Cuentas",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleVisibility() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Balance",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick Top-Up (Cuentas Rápidas) section with horizontal avatars
 */
@Composable
private fun QuickTopUpSection(
    accounts: List<AccountEntity>,
    onOpenAddAccount: () -> Unit,
    onOpenAccounts: () -> Unit,
    onEditAccount: (AccountEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Top-Up",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "See more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenAccounts() }
                    .padding(4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First item: Dashed / Outlined "+" Add Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenAddAccount() }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Añadir Cuenta",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = "Add",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Divider vertical dot
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            // Dynamic User Accounts or Default Samples if empty
            if (accounts.isEmpty()) {
                val sampleAccounts = listOf(
                    Triple("Bancolombia", Color(0xFFFDB913), Icons.Default.AccountBalance),
                    Triple("Nequi", Color(0xFFE91E63), Icons.Default.Smartphone),
                    Triple("Nu", Color(0xFF8B5CF6), Icons.Default.CreditCard),
                    Triple("Efectivo", EmeraldGreen, Icons.Default.AttachMoney)
                )
                sampleAccounts.forEach { (name, color, icon) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenAddAccount() }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = color.copy(alpha = 0.2f),
                            border = BorderStroke(1.2.dp, color.copy(alpha = 0.5f)),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = name, tint = color, modifier = Modifier.size(24.dp))
                            }
                        }
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                accounts.forEach { acc ->
                    val accColor = getAccountColor(acc.name)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onEditAccount(acc) }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.5.dp, accColor.copy(alpha = 0.6f)),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = acc.name.take(2).uppercase(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = accColor
                                )
                            }
                        }
                        Text(
                            text = acc.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Latest Transactions (Últimos Movimientos) horizontal cards matching reference
 */
@Composable
private fun LatestTransactionsHorizontalSection(
    transactions: List<TransactionEntity>,
    isBalanceVisible: Boolean,
    onOpenMetrics: () -> Unit,
    onAddTransaction: () -> Unit,
    format: NumberFormat
) {
    val dateFormat = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Latest Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "See more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenMetrics() }
                    .padding(4.dp)
            )
        }

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddTransaction() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column {
                        Text("No hay transacciones aún", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Toca aquí para registrar tu primer movimiento", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                transactions.take(6).forEach { tx ->
                    val isIncome = tx.transactionType == "income"
                    val txColor = if (isIncome) EmeraldGreen else RoseRed
                    val catColor = getCategoryColor(tx.category)
                    val formattedDate = remember(tx.transactionDate) {
                        dateFormat.format(Date(tx.transactionDate))
                    }

                    Card(
                        modifier = Modifier
                            .width(135.dp)
                            .height(165.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Category / Avatar Icon container
                            Surface(
                                shape = CircleShape,
                                color = catColor.copy(alpha = 0.18f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = getCategoryIcon(tx.category),
                                        contentDescription = tx.category,
                                        tint = catColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Title & Date
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tx.description?.takeIf { it.isNotBlank() } ?: tx.category,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formattedDate,
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Amount
                            Text(
                                text = if (isBalanceVisible) {
                                    "${if (isIncome) "+" else "-"}${format.format(tx.amount)}"
                                } else "••••",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = txColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Monthly Expenses Chart
 */
@Composable
private fun MonthlyExpensesSection(expenses: List<Float>) {
    val modelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(expenses) {
        if (expenses.isNotEmpty()) {
            val entries = expenses.mapIndexed { index, expense ->
                FloatEntry(x = index.toFloat(), y = expense)
            }
            modelProducer.setEntries(entries)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Tendencia de Gastos (Últimos 6 meses)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (expenses.all { it == 0f }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no hay gastos registrados este semestre",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Chart(
                    chart = lineChart(),
                    chartModelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Vertical detailed list of recent transactions
 */
@Composable
fun RecentTransactionsSection(
    transactions: List<TransactionEntity>,
    onAddTransaction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Detalle de Movimientos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (transactions.isNotEmpty()) {
                TextButton(onClick = onAddTransaction) {
                    Text("+ Nuevo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No tienes movimientos registrados",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Registra tus compras diarias o ingresos para tener control total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            transactions.take(8).forEach { transaction ->
                TransactionItem(transaction)
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: TransactionEntity) {
    val isIncome = transaction.transactionType == "income"
    val context = LocalContext.current
    val format = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }
    }
    val catColor = getCategoryColor(transaction.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = catColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getCategoryIcon(transaction.category),
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = transaction.description ?: (if (isIncome) "Ingreso" else "Gasto"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncome) "+" else "-"}${format.format(transaction.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) EmeraldGreen else RoseRed
                )
                transaction.location?.takeIf { it.isNotBlank() }?.let { location ->
                    TextButton(
                        onClick = {
                            val mapsUrl = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(location)}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Mapa", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Helpers for vibrant category colors and icons
 */
private fun getCategoryColor(category: String): Color {
    val cat = category.lowercase(Locale.ROOT)
    return when {
        cat.contains("comida") || cat.contains("restaurante") || cat.contains("alimento") -> Color(0xFFF59E0B) // Amber
        cat.contains("transporte") || cat.contains("gasolina") || cat.contains("uber") || cat.contains("moto") -> Color(0xFF3B82F6) // Blue
        cat.contains("mercado") || cat.contains("supermercado") || cat.contains("compras") -> Color(0xFF10B981) // Emerald
        cat.contains("salud") || cat.contains("farmacia") || cat.contains("médico") -> Color(0xFFEF4444) // Red
        cat.contains("entretenimiento") || cat.contains("cine") || cat.contains("juego") -> Color(0xFF8B5CF6) // Violet
        cat.contains("salario") || cat.contains("ingreso") || cat.contains("nómina") -> Color(0xFF10B981) // Green
        cat.contains("servicios") || cat.contains("luz") || cat.contains("agua") || cat.contains("internet") -> Color(0xFF06B6D4) // Cyan
        else -> ElectricIndigo
    }
}

private fun getCategoryIcon(category: String): ImageVector {
    val cat = category.lowercase(Locale.ROOT)
    return when {
        cat.contains("comida") || cat.contains("restaurante") || cat.contains("alimento") -> Icons.Default.Fastfood
        cat.contains("transporte") || cat.contains("gasolina") || cat.contains("uber") || cat.contains("moto") -> Icons.Default.DirectionsCar
        cat.contains("mercado") || cat.contains("supermercado") || cat.contains("compras") -> Icons.Default.ShoppingBag
        cat.contains("salud") || cat.contains("farmacia") || cat.contains("médico") -> Icons.Default.LocalHospital
        cat.contains("salario") || cat.contains("ingreso") || cat.contains("nómina") -> Icons.Default.TrendingUp
        cat.contains("servicios") || cat.contains("luz") || cat.contains("agua") -> Icons.Default.Receipt
        else -> Icons.Default.Payments
    }
}

private fun getAccountColor(accountName: String): Color {
    val name = accountName.lowercase(Locale.ROOT)
    return when {
        name.contains("bancolombia") -> Color(0xFFFDB913) // Yellow
        name.contains("nequi") -> Color(0xFFE91E63) // Magenta
        name.contains("nu") -> Color(0xFF8B5CF6) // Purple
        name.contains("daviplata") || name.contains("davivienda") -> Color(0xFFED1C24) // Crimson Red
        name.contains("efectivo") || name.contains("cash") -> EmeraldGreen
        else -> ElectricIndigo
    }
}
