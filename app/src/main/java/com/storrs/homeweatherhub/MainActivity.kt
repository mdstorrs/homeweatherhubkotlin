package com.storrs.homeweatherhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
                    onSettingsDismiss = viewModel::closeSettings,
                    onSettingsSave = viewModel::applySettings,
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
    onSettingsDismiss: () -> Unit,
    onSettingsSave: (ThemeMode, Int) -> Unit,
    onOpenStationList: () -> Unit,
    onTabSelected: (WeatherTab) -> Unit,
    onExit: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val stationListUiState by viewModel.stationListUiState.collectAsState()
    var isAboutOpen by remember { mutableStateOf(false) }

    if (uiState.isSettingsOpen) {
        SettingsDialog(
            currentTheme = uiState.themeMode,
            currentMeasurement = uiState.measurementSystem,
            onDismiss = onSettingsDismiss,
            onSave = onSettingsSave
        )
    }

    if (isAboutOpen) {
        AboutDialog(onDismiss = { isAboutOpen = false })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                TextButton(onClick = { scope.launch { drawerState.close() }; onOpenSettings() }) {
                    Text("Settings")
                }
                TextButton(onClick = { scope.launch { drawerState.close() }; isAboutOpen = true }) {
                    Text("About")
                }
                TextButton(onClick = { scope.launch { drawerState.close() }; onExit() }) {
                    Text("Exit")
                }
            }
        }
    ) {
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
                    Column(modifier = Modifier.padding(padding)) {
                        TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                            Tab(
                                selected = uiState.selectedTab == WeatherTab.CURRENT,
                                onClick = { onTabSelected(WeatherTab.CURRENT) },
                                text = { Text("Current") }
                            )
                            Tab(
                                selected = uiState.selectedTab == WeatherTab.HISTORY,
                                onClick = { onTabSelected(WeatherTab.HISTORY) },
                                text = { Text("History") }
                            )
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
                        }
                    }
                }
            }
        )
    }
}