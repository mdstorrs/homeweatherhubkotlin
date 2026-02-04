package com.storrs.homeweatherhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable
fun StationSelectionScreen(
    stations: List<WeatherStation>,
    onStationSelected: (WeatherStation) -> Unit,
    stationListUiState: StationListUiState,
    onSearch: (String) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by rememberSaveable { mutableStateOf(stationListUiState.filter) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val valueColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                onSearch(it)
            },
            label = { Text("Search by name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (stationListUiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (stationListUiState.error != null) {
            Text(
                text = stationListUiState.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(stationListUiState.stations) { station ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onStationSelected(station) },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(station.name, style = MaterialTheme.typography.titleMedium)
                            station.address?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = valueColor)
                            }
                            station.coordinates?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = valueColor)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onPrevPage,
                    enabled = stationListUiState.page > 1
                ) { Text("Previous") }
                Text("Page ${stationListUiState.page} of ${stationListUiState.totalPages}")
                Button(
                    onClick = onNextPage,
                    enabled = stationListUiState.page < stationListUiState.totalPages
                ) { Text("Next") }
            }
        }
    }
}
