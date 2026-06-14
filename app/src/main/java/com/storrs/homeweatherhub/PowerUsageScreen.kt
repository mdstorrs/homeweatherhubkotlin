package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PowerUsageScreen(
    station: WeatherStation,
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
        Text(
            text = "Power data endpoint is not configured yet. This tab is now available only for stations where HasPower is enabled.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Fronius IP: ${settingsMap["FroniusIP"].orEmpty()}")
        Text("GoodWe IP: ${settingsMap["GoodWeIP"].orEmpty()}")
    }
}

