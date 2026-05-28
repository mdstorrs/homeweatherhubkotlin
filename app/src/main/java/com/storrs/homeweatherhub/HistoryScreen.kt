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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi

private val MinMaxColumnWidth = 110.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HistoryScreen(
    report: HistoryReport?,
    isLoading: Boolean,
    error: String?,
    dateRangeLabel: String,
    period: HistoryPeriod,
    nextEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPeriodChange: (HistoryPeriod) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSwipeRefresh = report != null
    val refreshState = rememberPullRefreshState(
        refreshing = isLoading && canSwipeRefresh,
        onRefresh = onRefresh
    )
    val valueColor = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier.fillMaxSize().pullRefresh(refreshState, enabled = canSwipeRefresh)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                isLoading && report == null -> {
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
                report == null -> {
                    Text(text = "No history data available.")
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            HistoryHeaderCard(
                                title = report.wsName,
                                dateRangeLabel = dateRangeLabel
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            SectionMinMaxCard(
                                title = "TEMPS",
                                rows = listOf(
                                    Triple("OUTSIDE", withTempSymbol(report.outsideTemperatureMin, report.measurementSymbol), withTempSymbol(report.outsideTemperatureMax, report.measurementSymbol)),
                                    Triple("INSIDE", withTempSymbol(report.insideTemperatureMin, report.measurementSymbol), withTempSymbol(report.insideTemperatureMax, report.measurementSymbol))
                                ),
                                valueColor = valueColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            SectionMinMaxCard(
                                title = "RAIN",
                                rows = listOf(
                                    Triple("ACCUM.", "", report.totalRain),
                                    Triple("RATE", "", report.rainRateMax)
                                ),
                                minLabel = "",
                                maxLabel = "Max",
                                valueColor = valueColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            SectionMinMaxCard(
                                title = "WIND",
                                rows = listOf(
                                    Triple("MAX. SPEED", "", report.windSpeedMax),
                                    Triple("MAX. GUSTS", "", report.windGustMax),
                                    Triple("AVG. DIRECTION", "", report.windDirectionAvg)
                                ),
                                minLabel = "",
                                maxLabel = "Max",
                                valueColor = valueColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            SectionMinMaxCard(
                                title = "HUMIDITY",
                                rows = listOf(
                                    Triple("OUTSIDE", report.outsideHumidityMin, report.outsideHumidityMax),
                                    Triple("INSIDE", report.insideHumidityMin, report.insideHumidityMax)
                                ),
                                valueColor = valueColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            SectionMinMaxCard(
                                title = "MISC",
                                rows = listOf(
                                    Triple("PRESSURE", report.pressureMin, report.pressureMax),
                                    Triple("UV INDEX", "-", report.uvIndexMax.toString())
                                ),
                                valueColor = valueColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            HistoryControlsRow(
                                period = period,
                                nextEnabled = nextEnabled,
                                onPrev = onPrev,
                                onNext = onNext,
                                onPeriodChange = onPeriodChange
                            )
                        }
                    }
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isLoading && canSwipeRefresh,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun HistoryHeaderCard(title: String, dateRangeLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp)) {
             if (dateRangeLabel.isNotBlank()) {
                 Text(
                     text = dateRangeLabel,
                     style = MaterialTheme.typography.titleSmall,
                     color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                     textAlign = TextAlign.Center
                 )
             }
         }
    }
}

@Composable
private fun SectionMinMaxCard(
    title: String,
    rows: List<Triple<String, String, String>>,
    minLabel: String = "Min",
    maxLabel: String = "Max",
    valueColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (minLabel.isNotBlank() || maxLabel.isNotBlank()) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        if (minLabel.isNotBlank()) {
                            Text(
                                text = minLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = valueColor,
                                modifier = Modifier.width(MinMaxColumnWidth),
                                textAlign = TextAlign.End
                            )
                        }
                        if (maxLabel.isNotBlank()) {
                            Text(
                                text = maxLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = valueColor,
                                modifier = Modifier.width(MinMaxColumnWidth),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            rows.forEach { row ->
                MinMaxRow(label = row.first, min = row.second, max = row.third, minLabel = minLabel, maxLabel = maxLabel, valueColor = valueColor)
            }
        }
    }
}

@Composable
private fun MinMaxRow(
    label: String,
    min: String,
    max: String,
    minLabel: String,
    maxLabel: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        Row(horizontalArrangement = Arrangement.End) {
            if (minLabel.isNotBlank()) {
                Text(
                    text = min,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    modifier = Modifier.width(MinMaxColumnWidth),
                    textAlign = TextAlign.End
                )
            }
            if (maxLabel.isNotBlank()) {
                Text(
                    text = max,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    modifier = Modifier.width(MinMaxColumnWidth),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryControlsRow(
    period: HistoryPeriod,
    nextEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPeriodChange: (HistoryPeriod) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = HistoryPeriod.entries.toList()
    val controlHeight = 56.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrev,
            enabled = period != HistoryPeriod.ALL,
            modifier = Modifier
                .weight(1f)
                .height(controlHeight)
        ) {
            Text("BACK", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1.25f)
        ) {
            OutlinedTextField(
                value = period.label,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .height(controlHeight)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onPeriodChange(option)
                        }
                    )
                }
            }
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier
                .weight(1f)
                .height(controlHeight)
        ) {
            Text("NEXT", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun withTempSymbol(value: String, symbol: String): String {
    return if (symbol.isBlank()) value else "$value $symbol"
}
