package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PowerUsageScreen(
    station: WeatherStation,
    froniusState: PowerDeviceUiState<FroniusInverterData>,
    goodWeState: PowerDeviceUiState<GoodWeData>,
    zappiState: PowerDeviceUiState<ZappiData>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsMap = station.settings.associate { it.key to it.value }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Power Usage", style = MaterialTheme.typography.headlineSmall)
        Text("Station: ${station.name}", style = MaterialTheme.typography.titleMedium)

        val isRefreshing = froniusState.isLoading || goodWeState.isLoading || zappiState.isLoading
        Button(onClick = onRefresh, enabled = !isRefreshing) {
            Text(if (isRefreshing) "Refreshing..." else "Refresh")
        }

        Text("Fronius IP: ${settingsMap["FroniusIP"].orEmpty()}")
        Text("GoodWe IP: ${settingsMap["GoodWeIP"].orEmpty()}")
        Text("Zappi Serial: ${settingsMap["ZappiSerial"].orEmpty()}")

        Text("Fronius", style = MaterialTheme.typography.titleMedium)
        when {
            froniusState.isLoading -> {
                Text("Loading Fronius data...")
            }
            froniusState.error != null -> {
                Text(froniusState.error, color = MaterialTheme.colorScheme.error)
            }
            froniusState.data != null -> {
                val data = froniusState.data
                val totalPvPower = data.pv1Power + data.pv2Power
                Text("Total PV Output: ${totalPvPower} W")
                if (!froniusState.infoMessage.isNullOrBlank()) {
                    Text(froniusState.infoMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                Text("No Fronius data loaded yet.")
            }
        }

        Text("GoodWe", style = MaterialTheme.typography.titleMedium)
        when {
            goodWeState.isLoading -> {
                Text("Loading GoodWe data...")
            }
            goodWeState.error != null -> {
                Text(goodWeState.error, color = MaterialTheme.colorScheme.error)
            }
            goodWeState.data != null -> {
                val battery = goodWeState.data.batteryData
                val inverter = goodWeState.data.inverterInfo
                if (battery != null) {
                    Text("Total PV Output: ${battery.pvTotalPower} W")
                    Text("Battery Power: ${battery.power} W")
                    Text("Battery SOC: ${battery.stateOfCharge}%")
                    Text("Battery State: ${battery.state}")
                } else {
                    Text("No battery payload parsed.")
                }
                if (!inverter?.modelLine.isNullOrBlank()) {
                    Text("Model: ${inverter.modelLine}")
                }
                if (!goodWeState.infoMessage.isNullOrBlank()) {
                    Text(goodWeState.infoMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                Text("No GoodWe data loaded yet.")
            }
        }

        Text("Zappi", style = MaterialTheme.typography.titleMedium)
        when {
            zappiState.isLoading -> {
                Text("Loading Zappi data...")
            }
            zappiState.error != null -> {
                Text(zappiState.error, color = MaterialTheme.colorScheme.error)
                if (!zappiState.infoMessage.isNullOrBlank()) {
                    Text(zappiState.infoMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            zappiState.data != null -> {
                val data = zappiState.data
                val gridDirection = when {
                    data.gridPower < 0 -> "Exporting"
                    data.gridPower > 0 -> "Importing"
                    else -> "Idle"
                }
                Text("EV Charging: ${if (data.isCharging) "Yes" else "No"}")
                Text("Power to Car: ${data.chargingPower} W")
                Text("Grid: ${"%.0f".format(data.gridPower)} W (${"%.2f".format(data.gridPower / 1000.0)} kW) - $gridDirection")
                Text("Status: ${data.status}")
                if (!zappiState.infoMessage.isNullOrBlank()) {
                    Text(zappiState.infoMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                Text("No Zappi data loaded yet.")
            }
        }

        Text(
            text = "Totals will appear once all three APIs are implemented and successful.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
