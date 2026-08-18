package za.co.statecapture.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.AppDatabase
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.data.repository.SettingsRepository
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.ui.AppViewModelFactory
import za.co.statecapture.android.ui.about.AboutScreen
import za.co.statecapture.android.ui.calculator.CalculationScreen
import za.co.statecapture.android.ui.calculator.CalculationViewModel
import za.co.statecapture.android.ui.components.AdBanner
import za.co.statecapture.android.ui.dashboard.DashboardScreen
import za.co.statecapture.android.ui.dashboard.DashboardViewModel
import za.co.statecapture.android.ui.feedback.FeedbackScreen
import za.co.statecapture.android.ui.meters.MeterScreen
import za.co.statecapture.android.ui.meters.MeterViewModel
import za.co.statecapture.android.ui.settings.SettingsScreen
import za.co.statecapture.android.ui.settings.SettingsViewModel

import za.co.statecapture.android.ui.tariffs.TariffInfoScreen
import za.co.statecapture.android.ui.tariffs.TariffViewModel
import za.co.statecapture.android.ui.theme.DynamicTariffTheme
import za.co.statecapture.android.ui.support.SupportScreen

import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)

        val database = AppDatabase.getDatabase(this)
        val tariffRepository = TariffRepository(this)
        val settingsRepository = SettingsRepository(this)
        
        val factory = AppViewModelFactory(database, tariffRepository, settingsRepository)

        setContent {
            val initialScreen = intent?.getStringExtra("navigateTo") ?: "dashboard"
            var currentScreen by remember { mutableStateOf(initialScreen) }
            var selectedMeter by remember { mutableStateOf<Meter?>(null) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            DynamicTariffTheme {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            windowInsets = WindowInsets.statusBars
                        ) {
                            Spacer(Modifier.height(12.dp))
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                    label = { Text("Dashboard") },
                                    selected = currentScreen == "dashboard",
                                    onClick = { currentScreen = "dashboard"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                                    label = { Text("Meters") },
                                    selected = currentScreen == "meters",
                                    onClick = { currentScreen = "meters"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    label = { Text("Tariffs") },
                                    selected = currentScreen == "tariffs",
                                    onClick = { currentScreen = "tariffs"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text("Settings") },
                                    selected = currentScreen == "settings",
                                    onClick = { currentScreen = "settings"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    label = { Text("Feedback") },
                                    selected = currentScreen == "feedback",
                                    onClick = { currentScreen = "feedback"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                                    label = { Text("Support") },
                                    selected = currentScreen == "support",
                                    onClick = { currentScreen = "support"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                    label = { Text("About") },
                                    selected = currentScreen == "about",
                                    onClick = { currentScreen = "about"; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            val calcViewModel: CalculationViewModel = viewModel(factory = factory)
                            val meterViewModel: MeterViewModel = viewModel(factory = factory)
                            val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
                            val tariffViewModel: TariffViewModel = viewModel(factory = factory)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    when (currentScreen) {
                                        "dashboard" -> {
                                            DynamicTariffTheme(isHomeScreen = true) {
                                                DashboardScreen(
                                                    viewModel = dashboardViewModel,
                                                    onMenuClick = { scope.launch { drawerState.open() } }
                                                )
                                            }
                                        }

                                        "meters" -> {
                                            val meters by meterViewModel.meters.collectAsState()
                                            val availableProviders by meterViewModel.availableProviders.collectAsState()
                                            DynamicTariffTheme(isHomeScreen = true) {
                                                MeterScreen(
                                                    meters = meters,
                                                    availableProviders = availableProviders,
                                                    onAddMeter = meterViewModel::addMeter,
                                                    onUpdateMeter = meterViewModel::updateMeter,
                                                    onReorderMeters = meterViewModel::reorderMeters,
                                                    onDeleteMeter = meterViewModel::deleteMeter,
                                                    onMeterClick = { meter ->
                                                        selectedMeter = meter
                                                        currentScreen = "calculator"
                                                    },
                                                    onMenuClick = { scope.launch { drawerState.open() } }
                                                )
                                            }
                                        }

                                        "calculator" -> {
                                            val uiState by calcViewModel.uiState.collectAsState()

                                            LaunchedEffect(selectedMeter) {
                                                if (selectedMeter != null) {
                                                    calcViewModel.setMeter(selectedMeter!!)
                                                } else {
                                                    calcViewModel.clearMeter()
                                                }
                                            }

                                            DynamicTariffTheme(provider = uiState.selectedProvider) {
                                                CalculationScreen(
                                                    viewModel = calcViewModel,
                                                    onBack = {
                                                        currentScreen =
                                                            if (selectedMeter != null) "meters" else "dashboard"
                                                    }
                                                )
                                            }
                                        }

                                        "tariffs" -> {
                                            val tariffState by tariffViewModel.uiState.collectAsState()
                                            // Dedicated CalculationViewModel for the Tariffs screen —
                                            // keyed separately so it never shares state with the Meter
                                            // Calculator's ViewModel instance.
                                            val tariffCalcViewModel: CalculationViewModel = viewModel(
                                                key = "tariff_calc",
                                                factory = factory
                                            )
                                            DynamicTariffTheme(
                                                provider = tariffState.selectedProvider,
                                                isHomeScreen = tariffState.selectedProvider == null
                                            ) {
                                                TariffInfoScreen(
                                                    viewModel = tariffViewModel,
                                                    calcViewModel = tariffCalcViewModel,
                                                    onMenuClick = { scope.launch { drawerState.open() } }
                                                )
                                            }
                                        }

                                        "feedback" -> {
                                            DynamicTariffTheme(isHomeScreen = true) {
                                                 FeedbackScreen(
                                                     onMenuClick = { scope.launch { drawerState.open() } }
                                                 )
                                            }
                                        }

                                        "settings" -> {
                                            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                                            DynamicTariffTheme(isHomeScreen = true) {
                                                SettingsScreen(
                                                    viewModel = settingsViewModel,
                                                    onMenuClick = { scope.launch { drawerState.open() } }
                                                )
                                            }
                                        }

                                        "support" -> {
                                            DynamicTariffTheme(isHomeScreen = true) {
                                                SupportScreen(
                                                    onMenuClick = { scope.launch { drawerState.open() } }
                                                )
                                            }
                                        }

                                        "about" -> {
                                            DynamicTariffTheme(isHomeScreen = true) {
                                                AboutScreen(
                                                    onMenuClick = { scope.launch { drawerState.open() } }
                                                )
                                            }
                                        }
                                    }
                                }
                                AdBanner()
                            }
                        }
                    }
                }
        }
    }
}
