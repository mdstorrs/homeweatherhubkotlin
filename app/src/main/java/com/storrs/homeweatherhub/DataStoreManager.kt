package com.storrs.homeweatherhub

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "home_weather_hub_prefs")

object DataStoreManager {
    private val selectedStationId = stringPreferencesKey("selected_station_id")
    private val selectedStationName = stringPreferencesKey("selected_station_name")
    private val selectedStationAddress = stringPreferencesKey("selected_station_address")
    private val selectedStationCoordinates = stringPreferencesKey("selected_station_coordinates")

    suspend fun saveSelectedStation(context: Context, station: WeatherStation) {
        context.dataStore.edit { preferences ->
            preferences[selectedStationId] = station.id
            preferences[selectedStationName] = station.name
            station.address?.let { preferences[selectedStationAddress] = it }
                ?: preferences.remove(selectedStationAddress)
            station.coordinates?.let { preferences[selectedStationCoordinates] = it }
                ?: preferences.remove(selectedStationCoordinates)
        }
    }

    suspend fun loadSelectedStation(context: Context): WeatherStation? {
        val preferences = context.dataStore.data.first()
        val id = preferences[selectedStationId] ?: return null
        val name = preferences[selectedStationName] ?: return null
        return WeatherStation(
            id = id,
            name = name,
            address = preferences[selectedStationAddress],
            coordinates = preferences[selectedStationCoordinates]
        )
    }

    suspend fun clearSelectedStation(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(selectedStationId)
            preferences.remove(selectedStationName)
            preferences.remove(selectedStationAddress)
            preferences.remove(selectedStationCoordinates)
        }
    }
}
