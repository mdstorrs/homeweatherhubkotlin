package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    currentTheme: ThemeMode,
    currentMeasurement: Int,
    onDismiss: () -> Unit,
    onSave: (ThemeMode, Int) -> Unit
) {
    var selectedTheme by remember(currentTheme) { mutableStateOf(currentTheme) }
    var selectedMeasurement by remember(currentMeasurement) { mutableStateOf(currentMeasurement) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Settings") },
        text = {
            Column {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium
                )
                ThemeMode.values().forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> "Use System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                    SettingOptionRow(
                        text = label,
                        selected = selectedTheme == mode,
                        onClick = { selectedTheme = mode }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Units",
                    style = MaterialTheme.typography.titleMedium
                )
                listOf(1 to "Metric", 0 to "Freedom Units").forEach { (value, label) ->
                    SettingOptionRow(
                        text = label,
                        selected = selectedMeasurement == value,
                        onClick = { selectedMeasurement = value }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedTheme, selectedMeasurement) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
