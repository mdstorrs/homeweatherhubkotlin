package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val predefinedPowerKeys = listOf("FroniusIP", "GoodWeIP", "ZappiAPIKey", "ZappiSerial")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSettingsDialog(
    station: WeatherStation,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (stationId: String, name: String, address: String, coordinates: String, hasPower: Boolean, settings: List<StationSetting>) -> Unit
) {
    val settingsMap = remember(station.settings) { station.settings.associate { it.key to it.value } }
    var stationId by remember(station.id) { mutableStateOf(station.id) }
    var stationName by remember(station.name) { mutableStateOf(station.name) }
    var stationAddress by remember(station.address) { mutableStateOf(station.address.orEmpty()) }
    var stationCoordinates by remember(station.coordinates) { mutableStateOf(station.coordinates.orEmpty()) }
    var hasPower by remember(station.hasPower) { mutableStateOf(station.hasPower) }
    var froniusIp by remember(settingsMap) { mutableStateOf(settingsMap["FroniusIP"].orEmpty()) }
    var goodWeIp by remember(settingsMap) { mutableStateOf(settingsMap["GoodWeIP"].orEmpty()) }
    var zappiApiKey by remember(settingsMap) { mutableStateOf(settingsMap["ZappiAPIKey"].orEmpty()) }
    var zappiSerial by remember(settingsMap) { mutableStateOf(settingsMap["ZappiSerial"].orEmpty()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Close")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !isSaving && stationName.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            val editedSettings = listOf(
                                StationSetting("FroniusIP", froniusIp.trim()),
                                StationSetting("GoodWeIP", goodWeIp.trim()),
                                StationSetting("ZappiAPIKey", zappiApiKey.trim()),
                                StationSetting("ZappiSerial", zappiSerial.trim())
                            )
                            val settingsToSave = if (hasPower) {
                                mergeExtraSettings(station.settings, editedSettings)
                            } else {
                                // Keep existing values intact when power is disabled.
                                station.settings
                            }
                            onSave(
                                stationId.trim(),
                                stationName.trim(),
                                stationAddress.trim(),
                                stationCoordinates.trim(),
                                hasPower,
                                settingsToSave
                            )
                        }
                    ) {
                        Text(if (isSaving) "Saving..." else "Done")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Station Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = stationId,
                onValueChange = {},
                label = { Text("Station ID") },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = { Text("Station Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = stationAddress,
                onValueChange = { stationAddress = it },
                label = { Text("Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = stationCoordinates,
                onValueChange = { stationCoordinates = it },
                label = { Text("Coordinates") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Has Power", style = MaterialTheme.typography.titleSmall)
            Switch(checked = hasPower, onCheckedChange = { hasPower = it })
            Spacer(modifier = Modifier.height(12.dp))
            Text("Power Settings", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "These settings are enabled only when Has Power is turned on.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LabeledSettingInput(
                label = "Fronius IP",
                value = froniusIp,
                enabled = hasPower,
                onValueChange = { froniusIp = it }
            )
            LabeledSettingInput(
                label = "GoodWe IP",
                value = goodWeIp,
                enabled = hasPower,
                onValueChange = { goodWeIp = it }
            )
            LabeledSettingInput(
                label = "Zappi API Key",
                value = zappiApiKey,
                enabled = hasPower,
                onValueChange = { zappiApiKey = it }
            )
            LabeledSettingInput(
                label = "Zappi Serial",
                value = zappiSerial,
                enabled = hasPower,
                onValueChange = { zappiSerial = it }
            )

            if (!error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LabeledSettingInput(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

private fun mergeExtraSettings(existing: List<StationSetting>, edited: List<StationSetting>): List<StationSetting> {
    val editedKeys = predefinedPowerKeys.toSet()
    val keep = existing.filter { !editedKeys.contains(it.key) }
    return keep + edited
}


