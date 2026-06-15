package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

private const val MissingValue = "--"

private data class PowerRow(
    val label: String,
    val value: String,
    val isAlertValue: Boolean = false
)

private data class PowerSection(
    val title: String,
    val rows: List<PowerRow>
)

@OptIn(ExperimentalMaterialApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun PowerUsageScreen(
    station: WeatherStation,
    froniusState: PowerDeviceUiState<FroniusInverterData>,
    goodWeState: PowerDeviceUiState<GoodWeData>,
    zappiState: PowerDeviceUiState<ZappiData>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val headerColor = MaterialTheme.colorScheme.tertiary
    val isRefreshing = froniusState.isLoading || goodWeState.isLoading || zappiState.isLoading
    val hasData = froniusState.data != null || goodWeState.data != null || zappiState.data != null
    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing && hasData,
        onRefresh = onRefresh
    )

    val froniusPv = froniusState.data?.let { it.pv1Power + it.pv2Power }
    val goodWeBattery = goodWeState.data?.batteryData
    val goodWePv = goodWeBattery?.pvTotalPower
    val zappi = zappiState.data

    val batterySoc = goodWeBattery?.stateOfCharge?.let { "$it%" } ?: MissingValue
    val batteryPowerLabel = when (goodWeBattery?.state) {
        "Charging" -> "Charging"
        "Discharging" -> "Discharging"
        "Idle" -> "Idle"
        else -> "State"
    }
    val batteryPowerValue = goodWeBattery?.power?.let { formatWatts(abs(it)) } ?: MissingValue
    val batteryIsDischarging = goodWeBattery?.state == "Discharging"

    val solarGenerated = (froniusPv ?: 0.0) + (goodWePv ?: 0.0)
    val gridFromZappi = zappi?.gridPower ?: 0.0
    val gridImport = max(gridFromZappi, 0.0)
    val gridExport = max(-gridFromZappi, 0.0)
    val batteryDischarge = max(goodWeBattery?.power ?: 0.0, 0.0)
    val batteryCharge = max(-(goodWeBattery?.power ?: 0.0), 0.0)
    val carCharging = max(zappi?.chargingPower ?: 0.0, 0.0)
    val estimatedHousePower =
        solarGenerated + gridImport + batteryDischarge - gridExport - carCharging - batteryCharge
    val zappiGridPower = zappi?.gridPower
    val zappiGridLabel = when {
        zappiGridPower == null -> "Grid"
        zappiGridPower < 0 -> "Exporting"
        else -> "Importing"
    }

    val sections = listOf(
        PowerSection(
            title = "SOLAR GENERATION",
            rows = listOf(
                PowerRow("Fronius", froniusPv?.let { formatWatts(it) }.orMissing()),
                PowerRow("GoodWe", goodWePv?.let { formatWatts(it) }.orMissing())
            )
        ),
        PowerSection(
            title = "BATTERY",
            rows = listOf(
                PowerRow("SOC", batterySoc),
                PowerRow(
                    label = batteryPowerLabel,
                    value = batteryPowerValue,
                    isAlertValue = batteryIsDischarging
                )
            )
        ),
        PowerSection(
            title = "ZAPPI",
            rows = listOf(
                PowerRow("ChargingPower", zappi?.chargingPower?.let { formatWatts(it) }.orMissing()),
                PowerRow("ChargeAdded", zappi?.chargeAdded?.let { "${formatOneDecimal(it)} kWh" }.orMissing()),
                PowerRow("Status", zappi?.status.orMissing()),
                PowerRow("PlugStatus", zappi?.plugStatus.orMissing()),
                PowerRow("Mode", zappi?.mode.orMissing()),
                PowerRow("MinimumGreenLevel", zappi?.minimumGreenLevel?.let { "$it%" }.orMissing())
            )
        ),
        PowerSection(
            title = "HOUSE",
            rows = listOf(
                PowerRow("Estimated Usage", if (hasData) formatWatts(estimatedHousePower) else MissingValue)
            )
        ),
        PowerSection(
            title = "GRID",
            rows = listOf(
                PowerRow(
                    label = zappiGridLabel,
                    value = zappiGridPower?.let { formatWatts(abs(it)) }.orMissing(),
                    isAlertValue = (zappiGridPower ?: 0.0) > 0
                )
            )
        )
    )

    Box(modifier = modifier.fillMaxSize().pullRefresh(refreshState, enabled = hasData)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                isRefreshing && !hasData -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !hasData && (froniusState.error != null || goodWeState.error != null || zappiState.error != null) -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOfNotNull(froniusState.error, goodWeState.error, zappiState.error).forEach { errorText ->
                            Text(text = errorText, color = MaterialTheme.colorScheme.error)
                        }
                        Text("Pull down to retry when devices are reachable.")
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        sections.forEach { section ->
                            item {
                                PowerSectionCard(section = section, valueColor = valueColor, headerColor = headerColor)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing && hasData,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun PowerSectionCard(section: PowerSection, valueColor: Color, headerColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = headerColor,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            section.rows.forEach { row ->
                PowerKeyValueRow(
                    label = row.label,
                    value = row.value,
                    valueColor = valueColor,
                    isAlertValue = row.isAlertValue
                )
            }
        }
    }
}

@Composable
private fun PowerKeyValueRow(label: String, value: String, valueColor: Color, isAlertValue: Boolean = false) {
    val displayValueColor = if (isAlertValue) MaterialTheme.colorScheme.error else valueColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = displayValueColor)
    }
}

private fun formatWatts(value: Double): String = "${formatOneDecimal(value)} W"

private fun formatOneDecimal(value: Double): String = "%.1f".format(value)

private fun String?.orMissing(): String = if (this.isNullOrBlank()) MissingValue else this

private fun Any?.orMissing(): String = this?.toString() ?: MissingValue
