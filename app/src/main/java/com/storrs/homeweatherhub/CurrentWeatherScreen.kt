package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@Composable
fun CurrentWeatherScreen(
    station: WeatherStation,
    weather: CurrentWeather?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSwipeRefresh = weather != null
    val refreshState = rememberSwipeRefreshState(isRefreshing = isLoading && canSwipeRefresh)
    SwipeRefresh(
        state = refreshState,
        onRefresh = onRefresh,
        swipeEnabled = canSwipeRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                weather == null -> {
                    Text(text = "No current weather data available.")
                }
                else -> {
                    val tempSuffix = if (weather.measurementSymbol.isNotBlank()) " ${weather.measurementSymbol}" else ""
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            CurrentConditionsCard(
                                tempOutside = "${weather.tempOutside}$tempSuffix",
                                humidityOutside = weather.humidityOutside,
                                lastUpdated = weather.lastUpdated,
                                measurementSymbol = weather.measurementSymbol
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(
                            listOf(
                                WeatherSection(
                                    title = "RAIN",
                                    rows = listOf(
                                        "RATE" to weather.rainRate,
                                        "ACCUM." to weather.rainAccumulation
                                    )
                                ),
                                WeatherSection(
                                    title = "WIND",
                                    rows = listOf(
                                        "DIRECTION" to weather.windDirection,
                                        "SPEED" to weather.windSpeed,
                                        "GUSTS" to weather.windGust
                                    )
                                ),
                                WeatherSection(
                                    title = "INSIDE",
                                    rows = listOf(
                                        "TEMP" to "${weather.tempInside}$tempSuffix",
                                        "HUMIDITY" to weather.humidityInside
                                    )
                                ),
                                WeatherSection(
                                    title = "MISC",
                                    rows = listOf(
                                        "PRESSURE" to weather.pressure,
                                        "UV INDEX" to weather.uvIndex.toString()
                                    )
                                )
                            )
                        ) { section ->
                            SectionCard(section = section)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class WeatherSection(
    val title: String,
    val rows: List<Pair<String, String>>
)

@Composable
private fun CurrentConditionsCard(
    tempOutside: String,
    humidityOutside: String,
    lastUpdated: String,
    measurementSymbol: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp)) {
            Text(
                text = "Current Conditions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = lastUpdated,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = tempOutside,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "°",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 2.dp)
                )
                Text(
                    text = measurementSymbol,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Humidity ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = humidityOutside,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SectionCard(section: WeatherSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            section.rows.forEachIndexed { index, pair ->
                KeyValueRow(label = pair.first, value = pair.second)
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
