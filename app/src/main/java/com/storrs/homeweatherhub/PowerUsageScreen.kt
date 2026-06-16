package com.storrs.homeweatherhub

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

private const val MissingValue = "--"
private val ChargedValueColor = Color(0xFF2E7D32)

private data class PowerRow(
    val label: String,
    val value: String,
    val isAlertValue: Boolean = false,
    val valueColorOverride: Color? = null,
    val onLabelClick: (() -> Unit)? = null
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
    var showFroniusDetails by remember(station.id) { mutableStateOf(false) }
    var showGoodWeDetails by remember(station.id) { mutableStateOf(false) }
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val headerColor = MaterialTheme.colorScheme.tertiary
    val froniusConfigured = station.settingValue("FroniusIP").isNotBlank()
    val goodWeConfigured = station.settingValue("GoodWeIP").isNotBlank()
    val zappiConfigured = station.settingValue("ZappiAPIKey").isNotBlank() && station.settingValue("ZappiSerial").isNotBlank()
    val hasConfiguredInterface = froniusConfigured || goodWeConfigured || zappiConfigured
    val isRefreshing =
        (froniusConfigured && froniusState.isLoading) ||
            (goodWeConfigured && goodWeState.isLoading) ||
            (zappiConfigured && zappiState.isLoading)
    val hasData =
        (froniusConfigured && froniusState.data != null) ||
            (goodWeConfigured && goodWeState.data != null) ||
            (zappiConfigured && zappiState.data != null)
    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing && hasData,
        onRefresh = onRefresh
    )

    val froniusPv = froniusState.data?.let { it.pv1Power + it.pv2Power }
    val goodWeBattery = goodWeState.data?.batteryData
    val goodWePv = goodWeBattery?.pvTotalPower
    val zappi = zappiState.data

    val batterySocPercent = goodWeBattery?.stateOfCharge
    val isBatteryFullyCharged = batterySocPercent == 100
    val batterySoc = when {
        batterySocPercent == null -> MissingValue
        isBatteryFullyCharged -> "Charged"
        else -> "$batterySocPercent%"
    }
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
    val canCalculateHomeValue = froniusConfigured && goodWeConfigured && zappiConfigured && froniusState.data != null && goodWeBattery != null && zappi != null
    val zappiGridPower = zappi?.gridPower
    val zappiGridLabel = when {
        zappiGridPower == null -> "Grid"
        zappiGridPower < 0 -> "Exporting"
        else -> "Importing"
    }

    val sections = buildList {
        val solarRows = buildList {
            if (froniusConfigured) {
                val froniusLabel = if (showFroniusDetails) "Fronius (Hide details)" else "Fronius (Show details)"
                add(
                    PowerRow(
                        label = froniusLabel,
                        value = froniusPv?.let { formatWatts(it) }.orMissing(),
                        onLabelClick = { showFroniusDetails = !showFroniusDetails }
                    )
                )
            }
            if (goodWeConfigured) {
                val goodWeLabel = if (showGoodWeDetails) "GoodWe (Hide details)" else "GoodWe (Show details)"
                add(
                    PowerRow(
                        label = goodWeLabel,
                        value = goodWePv?.let { formatWatts(it) }.orMissing(),
                        onLabelClick = { showGoodWeDetails = !showGoodWeDetails }
                    )
                )
            }
        }
        if (solarRows.isNotEmpty()) {
            add(PowerSection(title = "SOLAR GENERATION", rows = solarRows))
        }

        if (froniusConfigured && showFroniusDetails) {
            add(PowerSection(title = "FRONIUS DETAILS", rows = buildFroniusDetailsRows(froniusState.data, froniusState.infoMessage)))
        }

        if (goodWeConfigured && showGoodWeDetails) {
            add(PowerSection(title = "GOODWE DETAILS", rows = buildGoodWeDetailsRows(goodWeState.data)))
        }

        if (goodWeConfigured) {
            add(
                PowerSection(
                    title = "BATTERY",
                    rows = listOf(
                        PowerRow(
                            label = "State of Charge",
                            value = batterySoc,
                            valueColorOverride = if (isBatteryFullyCharged) ChargedValueColor else null
                        ),
                        PowerRow(
                            label = batteryPowerLabel,
                            value = batteryPowerValue,
                            isAlertValue = batteryIsDischarging
                        )
                    )
                )
            )
        }

        if (zappiConfigured) {
            add(
                PowerSection(
                    title = "ZAPPI",
                    rows = listOf(
                        PowerRow("Charging Power", zappi?.chargingPower?.let { formatWatts(it) }.orMissing()),
                        PowerRow("Charge Added", zappi?.chargeAdded?.let { "${formatOneDecimal(it)} kWh" }.orMissing()),
                        PowerRow("Charging Status", zappi?.status.orMissing()),
                        PowerRow("Plug Status", zappi?.plugStatus.orMissing()),
                        PowerRow("Set Mode", zappi?.mode.orMissing()),
                        PowerRow("Minimum Green Level", zappi?.minimumGreenLevel?.let { "$it%" }.orMissing())
                    )
                )
            )
        }

        if (canCalculateHomeValue) {
            add(
                PowerSection(
                    title = "HOUSE",
                    rows = listOf(PowerRow("Estimated Usage", formatWatts(estimatedHousePower)))
                )
            )
        }

        if (zappiConfigured && zappi != null) {
            add(
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
        }
    }
    val visibleErrors = listOfNotNull(
        froniusState.error.takeIf { froniusConfigured },
        goodWeState.error.takeIf { goodWeConfigured },
        zappiState.error.takeIf { zappiConfigured }
    )

    Box(modifier = modifier.fillMaxSize().pullRefresh(refreshState, enabled = hasConfiguredInterface)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                isRefreshing && !hasData -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !hasData && visibleErrors.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleErrors.forEach { errorText ->
                            Text(text = errorText, color = MaterialTheme.colorScheme.error)
                        }
                        Text("Pull down to retry when devices are reachable.")
                    }
                }
                sections.isEmpty() -> {
                    Text("No configured power interfaces for this station.")
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
                    isAlertValue = row.isAlertValue,
                    valueColorOverride = row.valueColorOverride,
                    onLabelClick = row.onLabelClick
                )
            }
        }
    }
}

@Composable
private fun PowerKeyValueRow(
    label: String,
    value: String,
    valueColor: Color,
    isAlertValue: Boolean = false,
    valueColorOverride: Color? = null,
    onLabelClick: (() -> Unit)? = null
) {
    val displayValueColor = valueColorOverride ?: if (isAlertValue) MaterialTheme.colorScheme.error else valueColor
    if (value.isBlank()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = displayValueColor)
    }
}

private fun formatWatts(value: Double): String = "${formatOneDecimal(value)} W"

private fun formatOneDecimal(value: Double): String = "%.1f".format(value)

private fun formatTwoDecimals(value: Double): String = "%.2f".format(value)

private fun formatUptimeSeconds(seconds: Double): String {
    if (seconds <= 0.0) return MissingValue
    val totalSeconds = seconds.toLong()
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    return "${days}d ${hours}h ${minutes}m"
}

private fun buildFroniusDetailsRows(data: FroniusInverterData?, infoMessage: String?): List<PowerRow> {
    if (data == null) {
        return listOf(
            PowerRow("=== FRONIUS INVERTER INFO ===", ""),
            PowerRow("Status", "No Fronius data loaded")
        )
    }

    val totalPv = data.pv1Power + data.pv2Power
    return listOf(
        PowerRow("=== FRONIUS INVERTER INFO ===", ""),
        PowerRow("Model", data.model.orMissing()),
        PowerRow("Status", data.deviceStatus.orMissing()),
        PowerRow("Uptime", formatUptimeSeconds(data.uptime)),

        PowerRow("=== SOLAR PV INPUT ===", ""),
        PowerRow(
            "PV1 Power",
            "${formatOneDecimal(data.pv1Power)} W (${formatOneDecimal(data.pv1Voltage)} V x ${formatTwoDecimals(data.pv1Current)} A)"
        ),
        PowerRow(
            "PV2 Power",
            "${formatOneDecimal(data.pv2Power)} W (${formatOneDecimal(data.pv2Voltage)} V x ${formatTwoDecimals(data.pv2Current)} A)"
        ),
        PowerRow("Total PV", "${formatOneDecimal(totalPv)} W (${formatTwoDecimals(totalPv / 1000.0)} kW)"),

        PowerRow("=== AC OUTPUT ===", ""),
        PowerRow("Power", "${formatOneDecimal(data.currentPower)} W (${formatTwoDecimals(data.currentPower / 1000.0)} kW)"),
        PowerRow("Voltage", "${formatOneDecimal(data.acVoltage)} V"),
        PowerRow("Current", "${formatTwoDecimals(data.acCurrent)} A"),
        PowerRow("Frequency", "${formatTwoDecimals(data.acFrequency)} Hz"),

        PowerRow("=== GRID CONNECTION ===", ""),
        PowerRow("Voltage", "${formatOneDecimal(data.gridVoltage)} V"),
        PowerRow("Frequency", "${formatTwoDecimals(data.gridFrequency)} Hz"),

        PowerRow("=== SYSTEM ===", ""),
        PowerRow("Temperature", "${formatOneDecimal(data.ambientTemperature)} C"),

        PowerRow("=== WARNINGS ===", ""),
        PowerRow("Info", infoMessage.orMissing())
    )
}

private fun buildGoodWeDetailsRows(data: GoodWeData?): List<PowerRow> {
    val inverter = data?.inverterInfo
    val battery = data?.batteryData
    if (inverter == null && battery == null) {
        return listOf(
            PowerRow("=== INVERTER INFO ===", ""),
            PowerRow("Status", "No GoodWe data loaded")
        )
    }

    val gridPower = battery?.gridPower?.toDouble()
    val gridDirection = when {
        gridPower == null -> MissingValue
        gridPower > 0 -> "Exporting"
        gridPower < 0 -> "Importing"
        else -> "Balanced"
    }

    return listOf(
        PowerRow("=== INVERTER INFO ===", ""),
        PowerRow("Model", inverter?.modelLine.orMissing()),
        PowerRow("Serial", inverter?.serialLine.orMissing()),
        PowerRow("Firmware", inverter?.firmwareLine.orMissing()),

        PowerRow("=== SOLAR INPUT ===", ""),
        PowerRow("PV1 Voltage", battery?.pv1Voltage?.let { "${formatOneDecimal(it)} V" }.orMissing()),
        PowerRow("PV1 Current", battery?.pv1Current?.let { "${formatOneDecimal(it)} A" }.orMissing()),
        PowerRow("PV1 Power", battery?.let { "${formatOneDecimal(it.pv1Voltage * it.pv1Current)} W" }.orMissing()),
        PowerRow("PV2 Voltage", battery?.pv2Voltage?.let { "${formatOneDecimal(it)} V" }.orMissing()),
        PowerRow("PV2 Current", battery?.pv2Current?.let { "${formatOneDecimal(it)} A" }.orMissing()),
        PowerRow("PV2 Power", battery?.let { "${formatOneDecimal(it.pv2Voltage * it.pv2Current)} W" }.orMissing()),
        PowerRow("Total PV Power", battery?.pvTotalPower?.let { "${formatOneDecimal(it)} W" }.orMissing()),

        PowerRow("=== GRID ===", ""),
        PowerRow(
            "Grid Power",
            if (gridPower == null) {
                MissingValue
            } else {
                "${formatOneDecimal(kotlin.math.abs(gridPower))} W (${formatTwoDecimals(kotlin.math.abs(gridPower) / 1000.0)} kW) - $gridDirection"
            }
        ),

        PowerRow("=== INVERTER OUTPUT ===", ""),
        PowerRow("Output Voltage", battery?.inverterVoltage?.let { "${formatOneDecimal(it)} V" }.orMissing()),
        PowerRow("Backup Voltage", battery?.backupVoltage?.let { "${formatOneDecimal(it)} V" }.orMissing()),

        PowerRow("=== BATTERY STATUS ===", ""),
        PowerRow("SOC", battery?.stateOfCharge?.let { "$it%" }.orMissing()),
        PowerRow("Voltage", battery?.voltage?.let { "${formatOneDecimal(it)} V" }.orMissing()),
        PowerRow("Current", battery?.current?.let { "${formatOneDecimal(it)} A" }.orMissing()),
        PowerRow("Power", battery?.power?.let { "${formatOneDecimal(it)} W" }.orMissing()),
        PowerRow("State", battery?.state.orMissing()),
        PowerRow("Temperature", battery?.temperature?.let { "${formatOneDecimal(it)} C" }.orMissing()),
        PowerRow("Health Index", battery?.healthIndex?.let { "$it%" }.orMissing()),
        PowerRow("Charge Limit", battery?.chargeLimit?.let { "$it A" }.orMissing()),
        PowerRow("Discharge Limit", battery?.dischargeLimit?.let { "$it A" }.orMissing())
    )
}

private fun WeatherStation.settingValue(key: String): String =
    settings.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim().orEmpty()

private fun String?.orMissing(): String = if (this.isNullOrBlank()) MissingValue else this

private fun Any?.orMissing(): String = this?.toString() ?: MissingValue
