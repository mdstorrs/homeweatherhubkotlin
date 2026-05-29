package com.storrs.homeweatherhub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Placeholder data classes
data class WeatherStation(
    val id: String,
    val name: String,
    val address: String? = null,
    val coordinates: String? = null
)
data class CurrentWeather(
    val serverTime: String,
    val lastUpdated: String,
    val tempOutside: String,
    val tempInside: String,
    val humidityOutside: String,
    val humidityInside: String,
    val pressure: String,
    val uvIndex: Int,
    val rainRate: String,
    val rainAccumulation: String,
    val windDirAngle: Int,
    val windDirection: String,
    val windSpeed: String,
    val windGust: String,
    val tempFeel: String,
    val wsid: Int,
    val wsName: String,
    val type: Int,
    val measurement: Int,
    val measurementSymbol: String
)
enum class WeatherTab { CURRENT, HISTORY }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class WeatherStationUiState(
    val stations: List<WeatherStation> = emptyList(),
    val selectedStation: WeatherStation? = null,
    val isStationListOpen: Boolean = false,
    val currentWeather: CurrentWeather? = null,
    val currentWeatherLoading: Boolean = false,
    val currentWeatherError: String? = null,
    val historyReport: HistoryReport? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val historyPeriod: HistoryPeriod = HistoryPeriod.DAY,
    val historyStartDate: LocalDate = LocalDate.now(),
    val historyDateRangeLabel: String = "",
    val historyNextEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val selectedTab: WeatherTab = WeatherTab.CURRENT,
    val measurementSystem: Int = 1,
    val isSettingsOpen: Boolean = false
)

data class StationListUiState(
    val stations: List<WeatherStation> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val filter: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class HistoryReport(
    val startDate: String,
    val endDate: String,
    val outsideTemperatureMin: String,
    val outsideTemperatureMax: String,
    val insideTemperatureMin: String,
    val insideTemperatureMax: String,
    val totalRain: String,
    val rainRateMax: String,
    val windSpeedMax: String,
    val windGustMax: String,
    val windDirectionAngleAvg: Int,
    val windDirectionAvg: String,
    val outsideHumidityMax: String,
    val outsideHumidityMin: String,
    val insideHumidityMax: String,
    val insideHumidityMin: String,
    val pressureMin: String,
    val pressureMax: String,
    val uvIndexMax: Int,
    val wsid: Int,
    val wsName: String,
    val type: Int,
    val measurement: Int,
    val measurementSymbol: String
)

enum class HistoryPeriod(val apiValue: Int, val label: String, val step: Period) {
    DAY(1, "Day", Period.ofDays(1)),
    WEEK(2, "Week", Period.ofWeeks(1)),
    MONTH(3, "Month", Period.ofMonths(1)),
    YEAR(4, "Year", Period.ofYears(1)),
    ALL(5, "All", Period.ZERO)
}

class WeatherStationViewModel(application: Application) : AndroidViewModel(application) {
    private val labelDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

    private val _uiState = MutableStateFlow(WeatherStationUiState())
    val uiState: StateFlow<WeatherStationUiState> = _uiState.asStateFlow()

    private val _stationListUiState = MutableStateFlow(StationListUiState())
    val stationListUiState: StateFlow<StationListUiState> = _stationListUiState.asStateFlow()

    init {
        restoreSelectedStation()
        loadStations()
    }

    private fun restoreSelectedStation() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val savedStation = DataStoreManager.loadSelectedStation(context)
            if (savedStation == null) {
                _uiState.value = _uiState.value.copy(isStationListOpen = true)
                loadStationsPage()
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                selectedStation = savedStation,
                isStationListOpen = false
            )
            loadCurrentWeather(savedStation)
            loadHistory(savedStation, _uiState.value.historyPeriod, _uiState.value.historyStartDate)
        }
    }

    fun loadStations() {
        viewModelScope.launch {
            // TODO: Fetch from API
            _uiState.value = _uiState.value.copy(
                stations = listOf(
                    WeatherStation("1", "Home"),
                    WeatherStation("2", "Cottage")
                )
            )
        }
    }

    fun selectStation(station: WeatherStation) {
        _uiState.value = _uiState.value.copy(selectedStation = station, isStationListOpen = false)
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            DataStoreManager.saveSelectedStation(context, station)
        }
        loadCurrentWeather(station)
        loadHistory(station, _uiState.value.historyPeriod, _uiState.value.historyStartDate)
    }

    fun openStationList() {
        _uiState.value = _uiState.value.copy(isStationListOpen = true)
        val state = _stationListUiState.value
        if (state.stations.isEmpty() && !state.isLoading) {
            loadStationsPage(page = state.page, filter = state.filter)
        }
    }

    fun openSettings() {
        _uiState.value = _uiState.value.copy(isSettingsOpen = true)
    }

    fun closeSettings() {
        _uiState.value = _uiState.value.copy(isSettingsOpen = false)
    }

    fun applySettings(themeMode: ThemeMode, measurementSystem: Int) {
        _uiState.value = _uiState.value.copy(
            themeMode = themeMode,
            measurementSystem = measurementSystem,
            isSettingsOpen = false
        )
        val station = _uiState.value.selectedStation ?: return
        loadCurrentWeather(station)
        loadHistory(station, _uiState.value.historyPeriod, _uiState.value.historyStartDate)
        // TODO: Persist settings to DataStore
    }

    fun setTheme(theme: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = theme)
        // TODO: Save theme to DataStore
    }

    fun selectTab(tab: WeatherTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == WeatherTab.CURRENT) {
            val station = _uiState.value.selectedStation
            if (station != null && _uiState.value.currentWeather == null && !_uiState.value.currentWeatherLoading) {
                loadCurrentWeather(station)
            }
        } else if (tab == WeatherTab.HISTORY) {
            val station = _uiState.value.selectedStation
            if (station != null && _uiState.value.historyReport == null && !_uiState.value.historyLoading) {
                loadHistory(station, _uiState.value.historyPeriod, _uiState.value.historyStartDate)
            }
        }
    }

    fun setHistoryPeriod(period: HistoryPeriod) {
        val today = LocalDate.now()
        val startDate = defaultStartDate(period, today)
        _uiState.value = _uiState.value.copy(
            historyPeriod = period,
            historyStartDate = startDate
        )
        val station = _uiState.value.selectedStation
        if (station != null) {
            loadHistory(station, period, startDate)
        }
    }

    fun historyPrev() {
        val state = _uiState.value
        if (state.historyPeriod == HistoryPeriod.ALL) return
        val newStart = state.historyStartDate.minus(state.historyPeriod.step)
        _uiState.value = state.copy(historyStartDate = newStart)
        val station = state.selectedStation ?: return
        loadHistory(station, state.historyPeriod, newStart)
    }

    fun historyNext() {
        val state = _uiState.value
        if (!state.historyNextEnabled || state.historyPeriod == HistoryPeriod.ALL) return
        val newStart = state.historyStartDate.plus(state.historyPeriod.step)
        _uiState.value = state.copy(historyStartDate = newStart)
        val station = state.selectedStation ?: return
        loadHistory(station, state.historyPeriod, newStart)
    }

    fun refreshCurrentWeather() {
        val station = _uiState.value.selectedStation ?: return
        loadCurrentWeather(station)
    }

    fun refreshHistory() {
        val state = _uiState.value
        val station = state.selectedStation ?: return
        loadHistory(station, state.historyPeriod, state.historyStartDate)
    }

    private fun loadCurrentWeather(station: WeatherStation) {
        val stationId = station.id.toIntOrNull()
        if (stationId == null) {
            _uiState.value = _uiState.value.copy(
                currentWeather = null,
                currentWeatherLoading = false,
                currentWeatherError = "Invalid station id"
            )
            return
        }
        val measurement = _uiState.value.measurementSystem
        _uiState.value = _uiState.value.copy(currentWeatherLoading = true, currentWeatherError = null)
        viewModelScope.launch {
            val result = WeatherStationRepository.fetchCurrentWeather(
                stationId = stationId,
                measurement = measurement
            )
            if (result.success && result.weather != null) {
                _uiState.value = _uiState.value.copy(
                    currentWeather = result.weather,
                    currentWeatherLoading = false,
                    currentWeatherError = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    currentWeather = null,
                    currentWeatherLoading = false,
                    currentWeatherError = result.error ?: result.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadStationsPage(page: Int = 1, filter: String = "") {
        _stationListUiState.value = _stationListUiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = WeatherStationRepository.fetchStations(page = page, pageSize = 5, filter = if (filter.isBlank()) null else filter)
            if (result.success) {
                _stationListUiState.value = _stationListUiState.value.copy(
                    stations = result.stations,
                    page = page,
                    totalPages = result.totalPages,
                    filter = filter,
                    isLoading = false,
                    error = null
                )
            } else {
                _stationListUiState.value = _stationListUiState.value.copy(
                    isLoading = false,
                    error = result.error ?: result.message ?: "Unknown error"
                )
            }
        }
    }

    fun searchStations(filter: String) {
        loadStationsPage(page = 1, filter = filter)
    }

    fun nextPage() {
        val state = _stationListUiState.value
        if (state.page < state.totalPages) {
            loadStationsPage(page = state.page + 1, filter = state.filter)
        }
    }

    fun prevPage() {
        val state = _stationListUiState.value
        if (state.page > 1) {
            loadStationsPage(page = state.page - 1, filter = state.filter)
        }
    }

    private fun loadHistory(station: WeatherStation, period: HistoryPeriod, startDate: LocalDate) {
        val stationId = station.id.toIntOrNull()
        if (stationId == null) {
            _uiState.value = _uiState.value.copy(
                historyReport = null,
                historyLoading = false,
                historyError = "Invalid station id"
            )
            return
        }
        _uiState.value = _uiState.value.copy(historyLoading = true, historyError = null)
        val measurement = _uiState.value.measurementSystem
        val startDateParam = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        viewModelScope.launch {
            val result = WeatherStationRepository.fetchHistory(
                stationId = stationId,
                period = period.apiValue,
                startDate = startDateParam,
                measurement = measurement
            )
            if (result.success && result.report != null) {
                val rangeLabel = buildHistoryRangeLabel(result.report.startDate, result.report.endDate, period, startDate)
                _uiState.value = _uiState.value.copy(
                    historyReport = result.report,
                    historyLoading = false,
                    historyError = null,
                    historyDateRangeLabel = rangeLabel,
                    historyNextEnabled = isNextEnabled(period, startDate)
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    historyReport = null,
                    historyLoading = false,
                    historyError = result.error ?: result.message ?: "Unknown error",
                    historyNextEnabled = isNextEnabled(period, startDate)
                )
            }
        }
    }

    private fun defaultStartDate(period: HistoryPeriod, today: LocalDate): LocalDate {
        return when (period) {
            HistoryPeriod.DAY -> today
            HistoryPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            HistoryPeriod.MONTH -> today.withDayOfMonth(1)
            HistoryPeriod.YEAR -> today.withDayOfYear(1)
            HistoryPeriod.ALL -> today
        }
    }

    private fun isNextEnabled(period: HistoryPeriod, startDate: LocalDate): Boolean {
        if (period == HistoryPeriod.ALL) return false
        val today = LocalDate.now()
        val endDate = when (period) {
            HistoryPeriod.DAY -> startDate
            HistoryPeriod.WEEK -> startDate.plusDays(6)
            HistoryPeriod.MONTH -> startDate.plusMonths(1).minusDays(1)
            HistoryPeriod.YEAR -> startDate.plusYears(1).minusDays(1)
            HistoryPeriod.ALL -> startDate
        }
        return endDate.isBefore(today)
    }

    private fun buildHistoryRangeLabel(startDate: String, endDate: String, period: HistoryPeriod, requestedStart: LocalDate? = null): String {
        if (period == HistoryPeriod.DAY) {
            val day = requestedStart ?: parseHistoryDate(startDate) ?: return ""
            val today = LocalDate.now()
            return when (day) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> day.format(labelDateFormatter)
            }
        }

        if (period == HistoryPeriod.WEEK) {
            val weekStart = requestedStart ?: parseHistoryDate(startDate) ?: return ""
            val today = LocalDate.now()
            val thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val lastWeekMonday = thisWeekMonday.minusWeeks(1)
            return when (weekStart) {
                thisWeekMonday -> "This Week (Mon-Sun)"
                lastWeekMonday -> "Last Week (Mon-Sun)"
                else -> {
                    val start = parseDateLabel(startDate)
                    val end = parseDateLabel(endDate)
                    if (start.isNotBlank() && end.isNotBlank()) "$start to $end" else ""
                }
            }
        }

        if (period == HistoryPeriod.MONTH) {
            val date = requestedStart ?: parseHistoryDate(startDate) ?: return ""
            return date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }

        if (period == HistoryPeriod.YEAR) {
            val date = requestedStart ?: parseHistoryDate(startDate) ?: return ""
            return date.year.toString()
        }

        if (period == HistoryPeriod.ALL) {
            return "All"
        }

        val start = parseDateLabel(startDate)
        val end = parseDateLabel(endDate)
        return if (start.isNotBlank() && end.isNotBlank()) "$start to $end" else ""
    }

    private fun parseHistoryDate(value: String): LocalDate? {
        return try {
            LocalDateTime.parse(value).toLocalDate()
        } catch (e: Exception) {
            try {
                LocalDate.parse(value)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseDateLabel(value: String): String {
        val date = parseHistoryDate(value) ?: return value
        return date.format(labelDateFormatter)
    }
}
