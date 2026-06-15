package com.storrs.homeweatherhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storrs.homeweatherhub.ui.theme.HomeWeatherHubTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WeatherStationViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            HomeWeatherHubTheme(themeMode = uiState.themeMode) {
                MainScaffold(
                    uiState = uiState,
                    viewModel = viewModel,
                    onStationSelected = viewModel::selectStation,
                    onOpenSettings = viewModel::openSettings,
                    onOpenStationSettings = viewModel::openStationSettings,
                    onSettingsDismiss = viewModel::closeSettings,
                    onSettingsSave = viewModel::applySettings,
                    onStationSettingsDismiss = viewModel::closeStationSettings,
                    onStationSettingsSave = viewModel::saveStationSettings,
                    onOpenStationList = viewModel::openStationList,
                    onTabSelected = viewModel::selectTab,
                    onExit = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    uiState: WeatherStationUiState,
    viewModel: WeatherStationViewModel,
    onStationSelected: (WeatherStation) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStationSettings: () -> Unit,
    onSettingsDismiss: () -> Unit,
    onSettingsSave: (ThemeMode, Int) -> Unit,
    onStationSettingsDismiss: () -> Unit,
    onStationSettingsSave: (String, String, String, String, Boolean, List<StationSetting>) -> Unit,
    onOpenStationList: () -> Unit,
    onTabSelected: (WeatherTab) -> Unit,
    onExit: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val stationListUiState by viewModel.stationListUiState.collectAsState()
    var isAboutOpen by remember { mutableStateOf(false) }

    if (isAboutOpen) {
        AboutDialog(onDismiss = { isAboutOpen = false })
    }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ModalDrawerSheet {
                            TextButton(onClick = { scope.launch { drawerState.close() }; onOpenSettings() }) {
                                Text("Settings")
                            }
                            TextButton(
                                enabled = !uiState.isStationListOpen && uiState.selectedStation != null,
                                onClick = { scope.launch { drawerState.close() }; onOpenStationSettings() }
                            ) {
                                Text("Station Settings")
                            }
                            TextButton(onClick = { scope.launch { drawerState.close() }; isAboutOpen = true }) {
                                Text("About")
                            }
                            TextButton(onClick = { scope.launch { drawerState.close() }; onExit() }) {
                                Text("Exit")
                            }
                        }
                    }
                }
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    val titleText = if (uiState.selectedStation == null || uiState.isStationListOpen) {
                                        "Search"
                                    } else {
                                        uiState.selectedStation.name
                                    }
                                    Text(titleText)
                                },
                                navigationIcon = {
                                    IconButton(onClick = onOpenStationList) {
                                        Icon(Icons.Filled.Search, contentDescription = "Select Station")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        },
                        content = { padding ->
                            if (uiState.selectedStation == null || uiState.isStationListOpen) {
                                StationSelectionScreen(
                                    stations = stationListUiState.stations,
                                    onStationSelected = onStationSelected,
                                    stationListUiState = stationListUiState,
                                    onSearch = viewModel::searchStations,
                                    onNextPage = viewModel::nextPage,
                                    onPrevPage = viewModel::prevPage,
                                    modifier = Modifier.padding(padding)
                                )
                            } else {
                                val visibleTabs = buildList {
                                    add(WeatherTab.CURRENT)
                                    add(WeatherTab.HISTORY)
                                    if (uiState.selectedStation.hasPower) {
                                        add(WeatherTab.POWER_USAGE)
                                    }
                                }
                                val selectedTabIndex = visibleTabs.indexOf(uiState.selectedTab).coerceAtLeast(0)

                                Column(modifier = Modifier.padding(padding)) {
                                    TabRow(selectedTabIndex = selectedTabIndex) {
                                        visibleTabs.forEach { tab ->
                                            Tab(
                                                selected = uiState.selectedTab == tab,
                                                onClick = { onTabSelected(tab) },
                                                text = {
                                                    Text(
                                                        when (tab) {
                                                            WeatherTab.CURRENT -> "Current"
                                                            WeatherTab.HISTORY -> "History"
                                                            WeatherTab.POWER_USAGE -> "Power Usage"
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    when (uiState.selectedTab) {
                                        WeatherTab.CURRENT -> CurrentWeatherScreen(
                                            station = uiState.selectedStation,
                                            weather = uiState.currentWeather,
                                            isLoading = uiState.currentWeatherLoading,
                                            error = uiState.currentWeatherError,
                                            onRefresh = viewModel::refreshCurrentWeather
                                        )
                                        WeatherTab.HISTORY -> HistoryScreen(
                                            report = uiState.historyReport,
                                            isLoading = uiState.historyLoading,
                                            error = uiState.historyError,
                                            dateRangeLabel = uiState.historyDateRangeLabel,
                                            period = uiState.historyPeriod,
                                            nextEnabled = uiState.historyNextEnabled,
                                            onPrev = viewModel::historyPrev,
                                            onNext = viewModel::historyNext,
                                            onPeriodChange = viewModel::setHistoryPeriod,
                                            onRefresh = viewModel::refreshHistory
                                        )
                                        WeatherTab.POWER_USAGE -> PowerUsageScreen(
                                            station = uiState.selectedStation,
                                            froniusState = uiState.froniusPower,
                                            goodWeState = uiState.goodWePower,
                                            zappiState = uiState.zappiPower,
                                            onRefresh = viewModel::refreshPowerUsage
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            SettingsDialog(
                currentTheme = uiState.themeMode,
                currentMeasurement = uiState.measurementSystem,
                onDismiss = onSettingsDismiss,
                onSave = onSettingsSave
            )
        }

        AnimatedVisibility(
            visible = uiState.isStationSettingsOpen && uiState.selectedStation != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            uiState.selectedStation?.let { station ->
                StationSettingsDialog(
                    station = station,
                    isSaving = uiState.stationSettingsSaving,
                    error = uiState.stationSettingsError,
                    onDismiss = onStationSettingsDismiss,
                    onSave = onStationSettingsSave
                )
            }
        }
    }
}