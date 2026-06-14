package com.storrs.homeweatherhub

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "home_weather_hub_prefs")

object DataStoreManager {
    private val selectedStationId = stringPreferencesKey("selected_station_id")
    private val selectedStationName = stringPreferencesKey("selected_station_name")
    private val selectedStationAddress = stringPreferencesKey("selected_station_address")
    private val selectedStationCoordinates = stringPreferencesKey("selected_station_coordinates")
    private val selectedStationHasPower = booleanPreferencesKey("selected_station_has_power")
    private val selectedStationSettings = stringPreferencesKey("selected_station_settings")

    suspend fun saveSelectedStation(context: Context, station: WeatherStation) {
        context.dataStore.edit { preferences ->
            preferences[selectedStationId] = station.id
            preferences[selectedStationName] = station.name
            station.address?.let { preferences[selectedStationAddress] = it }
                ?: preferences.remove(selectedStationAddress)
            station.coordinates?.let { preferences[selectedStationCoordinates] = it }
                ?: preferences.remove(selectedStationCoordinates)
            preferences[selectedStationHasPower] = station.hasPower
            preferences[selectedStationSettings] = encodeSettings(station.settings)
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
            coordinates = preferences[selectedStationCoordinates],
            hasPower = preferences[selectedStationHasPower] ?: false,
            settings = decodeSettings(preferences[selectedStationSettings])
        )
    }

    suspend fun clearSelectedStation(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(selectedStationId)
            preferences.remove(selectedStationName)
            preferences.remove(selectedStationAddress)
            preferences.remove(selectedStationCoordinates)
            preferences.remove(selectedStationHasPower)
            preferences.remove(selectedStationSettings)
        }
    }

    private fun encodeSettings(settings: List<StationSetting>): String {
        return JSONArray().apply {
            settings.forEach { setting ->
                put(
                    JSONObject().apply {
                        put("key", setting.key)
                        put("value", setting.value)
                    }
                )
            }
        }.toString()
    }

    private fun decodeSettings(raw: String?): List<StationSetting> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    if (key.isBlank()) continue
                    add(StationSetting(key = key, value = item.optString("value")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
