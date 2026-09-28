package com.example.andresfinanzas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.repository.ThemeMode
import com.example.andresfinanzas.ui.accounts.AccountsScreen
import com.example.andresfinanzas.ui.auth.AuthViewModel
import com.example.andresfinanzas.ui.auth.LoginScreen
import com.example.andresfinanzas.ui.components.AddAccountDialog
import com.example.andresfinanzas.ui.components.AddTransactionBottomSheet
import com.example.andresfinanzas.ui.dashboard.DashboardScreen
import com.example.andresfinanzas.ui.dashboard.DashboardViewModel
import com.example.andresfinanzas.ui.debts.DebtScreen
import com.example.andresfinanzas.ui.goals.GoalScreen
import com.example.andresfinanzas.ui.metrics.MetricsInsightsScreen
import com.example.andresfinanzas.ui.settings.SettingsViewModel
import com.example.andresfinanzas.ui.theme.LifeOSTheme
import com.example.andresfinanzas.ui.wishlist.WishlistScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText = extractSharedText(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LifeOSTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        sharedText = sharedText,
                        onSharedTextConsumed = { sharedText = null },
                        onToggleTheme = settingsViewModel::cycleTheme
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = extractSharedText(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authUiState by authViewModel.uiState.collectAsState()

    if (!authUiState.isAuthenticated) {
        LoginScreen(
            onLoginSuccess = {
                dashboardViewModel.syncData()
            },
            viewModel = authViewModel
        )
        return
    }

    LaunchedEffect(authUiState.isAuthenticated) {
        if (authUiState.isAuthenticated) {
            dashboardViewModel.syncData()
        }
    }

    var currentScreen by remember { mutableStateOf("dashboard") }
    var selectedGoalsTab by remember { mutableStateOf(0) } // 0: Metas, 1: Deudas, 2: Wishlist
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }

    val dashboardUiState by dashboardViewModel.uiState.collectAsState()

    // If there's shared text (from Mercado Libre/browser), jump to wishlist
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            currentScreen = "goals_debts"
            selectedGoalsTab = 2
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = currentScreen == "dashboard",
                    onClick = { currentScreen = "dashboard" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == "accounts",
                    onClick = { currentScreen = "accounts" },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Cuentas") },
                    label = { Text("Cuentas", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == "metrics",
                    onClick = { currentScreen = "metrics" },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Métricas") },
                    label = { Text("Métricas", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == "goals_debts",
                    onClick = { currentScreen = "goals_debts" },
                    icon = { Icon(Icons.Default.Flag, contentDescription = "Metas & Deudas") },
                    label = { Text("Metas", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == "motorcycle",
                    onClick = { currentScreen = "motorcycle" },
                    icon = { Icon(Icons.Default.TwoWheeler, contentDescription = "Mi Moto") },
                    label = { Text("Mi Moto", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTransactionSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo Movimiento",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "dashboard" -> {
                    DashboardScreen(
                        uiState = dashboardUiState,
                        onOpenAccounts = { currentScreen = "accounts" },
                        onOpenMetrics = { currentScreen = "metrics" },
                        onOpenAddTransaction = { showAddTransactionSheet = true },
                        onOpenAddAccount = {
                            accountToEdit = null
                            showAddAccountDialog = true
                        },
                        onEditAccount = { acc ->
                            accountToEdit = acc
                            showAddAccountDialog = true
                        },
                        onToggleTheme = onToggleTheme,
                        onOpenOutings = { currentScreen = "outings" }
                    )
                }

                "accounts" -> {
                    AccountsScreen(
                        accounts = dashboardUiState.accounts,
                        totalBalance = dashboardUiState.totalBalance,
                        onSaveAccount = { dashboardViewModel.saveAccount(it) }
                    )
                }

                "metrics" -> {
                    MetricsInsightsScreen(
                        transactions = dashboardUiState.allTransactions,
                        totalBalance = dashboardUiState.totalBalance,
                        netWorth = dashboardUiState.netWorth
                    )
                }

                "motorcycle" -> {
                    com.example.andresfinanzas.ui.motorcycle.MotorcycleScreen(
                        onBack = { currentScreen = "dashboard" }
                    )
                }

                "outings" -> {
                    com.example.andresfinanzas.ui.outings.OutingScreen(
                        onBack = { currentScreen = "dashboard" }
                    )
                }

                "goals_debts" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = selectedGoalsTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = selectedGoalsTab == 0,
                                onClick = { selectedGoalsTab = 0 },
                                text = { Text("Metas", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedGoalsTab == 1,
                                onClick = { selectedGoalsTab = 1 },
                                text = { Text("Deudas", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedGoalsTab == 2,
                                onClick = { selectedGoalsTab = 2 },
                                text = { Text("Wishlist", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedGoalsTab == 3,
                                onClick = { selectedGoalsTab = 3 },
                                text = { Text("Citas & Rutas 📍", fontWeight = FontWeight.Bold) }
                            )
                        }

                        when (selectedGoalsTab) {
                            0 -> GoalScreen(onBack = { currentScreen = "dashboard" })
                            1 -> DebtScreen(onBack = { currentScreen = "dashboard" })
                            2 -> WishlistScreen(
                                onBack = { currentScreen = "dashboard" },
                                sharedUrl = sharedText,
                                onSharedUrlConsumed = onSharedTextConsumed
                            )
                            3 -> com.example.andresfinanzas.ui.outings.OutingScreen(
                                onBack = { currentScreen = "dashboard" }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Add Transaction
    if (showAddTransactionSheet) {
        AddTransactionBottomSheet(
            accounts = dashboardUiState.accounts,
            onDismiss = { showAddTransactionSheet = false },
            onSaveTransaction = { transaction ->
                dashboardViewModel.addTransaction(transaction)
                showAddTransactionSheet = false
            }
        )
    }

    // Dialog for Add / Edit Account
    if (showAddAccountDialog) {
        AddAccountDialog(
            accountToEdit = accountToEdit,
            onDismiss = {
                showAddAccountDialog = false
                accountToEdit = null
            },
            onSaveAccount = { savedAccount ->
                dashboardViewModel.saveAccount(savedAccount)
                showAddAccountDialog = false
                accountToEdit = null
            }
        )
    }
}

private fun extractSharedText(intent: Intent?): String? {
    return if (intent?.action == Intent.ACTION_SEND) {
        intent.getStringExtra(Intent.EXTRA_TEXT)
    } else {
        null
    }
}