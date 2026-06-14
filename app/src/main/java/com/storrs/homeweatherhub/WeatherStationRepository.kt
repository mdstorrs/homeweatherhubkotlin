package com.storrs.homeweatherhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Data class for station API response
data class StationApiResponse(
    val stations: List<WeatherStation>,
    val totalCount: Int,
    val totalPages: Int,
    val success: Boolean,
    val message: String?,
    val error: String?
)

// Data class for current weather API response
data class CurrentWeatherApiResponse(
    val weather: CurrentWeather?,
    val success: Boolean,
    val message: String?,
    val error: String?
)

// Data class for history API response
data class HistoryApiResponse(
    val report: HistoryReport?,
    val success: Boolean,
    val message: String?,
    val error: String?
)

data class ActionApiResponse(
    val success: Boolean,
    val message: String?,
    val error: String?
)

data class StationDetailsApiResponse(
    val station: WeatherStation?,
    val success: Boolean,
    val message: String?,
    val error: String?
)

// TODO: Implement API calls to 'stations', 'current', and 'history' endpoints
object WeatherStationRepository {
    private const val BASE_URL = "https://api.homeweatherhub.com/Stations"
    private const val CURRENT_URL = "https://api.homeweatherhub.com/Current"
    private const val HISTORY_URL = "https://api.homeweatherhub.com/History"

    suspend fun fetchStations(
        page: Int = 1,
        pageSize: Int = 5,
        filter: String? = null
    ): StationApiResponse = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("$BASE_URL/$page/$pageSize/")
        if (!filter.isNullOrBlank()) {
            urlBuilder.append(filter)
        }
        val url = URL(urlBuilder.toString())
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val stationsJson = json.getJSONArray("stations")
            val stations = mutableListOf<WeatherStation>()
            for (i in 0 until stationsJson.length()) {
                val s = stationsJson.getJSONObject(i)
                stations.add(parseStation(s))
            }
            StationApiResponse(
                stations = stations,
                totalCount = json.optInt("totalCount", stations.size),
                totalPages = json.optInt("totalPages", 1),
                success = json.optBoolean("success", true),
                message = json.optString("message"),
                error = json.optString("error")
            )
        } catch (e: Exception) {
            StationApiResponse(
                stations = emptyList(),
                totalCount = 0,
                totalPages = 0,
                success = false,
                message = null,
                error = e.message
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchStationById(stationId: Int): StationDetailsApiResponse = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL?stationid=$stationId")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val station = when {
                json.has("stations") -> {
                    val stationsJson = json.optJSONArray("stations")
                    var matched: WeatherStation? = null
                    if (stationsJson != null) {
                        for (i in 0 until stationsJson.length()) {
                            val candidate = stationsJson.optJSONObject(i) ?: continue
                            val candidateId = candidate.opt("id")?.toString()?.toIntOrNull()
                            if (candidateId == stationId) {
                                matched = parseStation(candidate)
                                break
                            }
                        }
                    }
                    matched
                }
                json.has("id") -> {
                    val parsed = parseStation(json)
                    if (parsed.id.toIntOrNull() == stationId) parsed else null
                }
                else -> null
            }

            StationDetailsApiResponse(
                station = station,
                success = json.optBoolean("success", station != null),
                message = json.optString("message"),
                error = json.optString("error")
            )
        } catch (e: Exception) {
            StationDetailsApiResponse(
                station = null,
                success = false,
                message = null,
                error = e.message
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchCurrentWeather(
        stationId: Int,
        measurement: Int = 1
    ): CurrentWeatherApiResponse = withContext(Dispatchers.IO) {
        val url = URL("$CURRENT_URL/$stationId/$measurement/")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val success = json.optBoolean("success", true)
            val weather = if (success) {
                CurrentWeather(
                    serverTime = json.optString("serverTime"),
                    lastUpdated = json.optString("lastUpdated"),
                    tempOutside = json.optString("tempOutside"),
                    tempInside = json.optString("tempInside"),
                    humidityOutside = json.optString("humidityOutside"),
                    humidityInside = json.optString("humidityInside"),
                    pressure = json.optString("pressure"),
                    uvIndex = json.optInt("uvIndex"),
                    rainRate = json.optString("rainRate"),
                    rainAccumulation = json.optString("rainAccumulation"),
                    windDirAngle = json.optInt("windDirAngle"),
                    windDirection = json.optString("windDirection"),
                    windSpeed = json.optString("windSpeed"),
                    windGust = json.optString("windGust"),
                    tempFeel = json.optString("tempFeel"),
                    wsid = json.optInt("wsid"),
                    wsName = json.optString("wsName"),
                    type = json.optInt("type"),
                    measurement = json.optInt("measurement"),
                    measurementSymbol = json.optString("measurementSymbol")
                )
            } else {
                null
            }
            CurrentWeatherApiResponse(
                weather = weather,
                success = success,
                message = json.optString("message"),
                error = json.optString("error")
            )
        } catch (e: Exception) {
            CurrentWeatherApiResponse(
                weather = null,
                success = false,
                message = null,
                error = e.message
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchHistory(
        stationId: Int,
        period: Int,
        startDate: String,
        measurement: Int = 1
    ): HistoryApiResponse = withContext(Dispatchers.IO) {
        val url = URL("$HISTORY_URL/$stationId/$period/$startDate/$measurement/")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        try {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val success = json.optBoolean("success", true)
            val report = if (success) {
                HistoryReport(
                    startDate = json.optString("startDate"),
                    endDate = json.optString("endDate"),
                    outsideTemperatureMin = json.optString("outsideTemperatureMin"),
                    outsideTemperatureMax = json.optString("outsideTemperatureMax"),
                    insideTemperatureMin = json.optString("insideTemperatureMin"),
                    insideTemperatureMax = json.optString("insideTemperatureMax"),
                    totalRain = json.optString("totalRain"),
                    rainRateMax = json.optString("rainRateMax"),
                    windSpeedMax = json.optString("windSpeedMax"),
                    windGustMax = json.optString("windGustMax"),
                    windDirectionAngleAvg = json.optInt("windDirectionAngleAvg"),
                    windDirectionAvg = json.optString("windDirectionAvg"),
                    outsideHumidityMax = json.optString("outsideHumidityMax"),
                    outsideHumidityMin = json.optString("outsideHumidityMin"),
                    insideHumidityMax = json.optString("insideHumidityMax"),
                    insideHumidityMin = json.optString("insideHumidityMin"),
                    pressureMin = json.optString("pressureMin"),
                    pressureMax = json.optString("pressureMax"),
                    uvIndexMax = json.optInt("uvIndexMax"),
                    wsid = json.optInt("wsid"),
                    wsName = json.optString("wsName"),
                    type = json.optInt("type"),
                    measurement = json.optInt("measurement"),
                    measurementSymbol = json.optString("measurementSymbol")
                )
            } else {
                null
            }
            HistoryApiResponse(
                report = report,
                success = success,
                message = json.optString("message"),
                error = json.optString("error")
            )
        } catch (e: Exception) {
            HistoryApiResponse(
                report = null,
                success = false,
                message = null,
                error = e.message
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun updateStation(station: WeatherStation): ActionApiResponse = withContext(Dispatchers.IO) {
        val conn = URL(BASE_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val stationId = station.id.toIntOrNull()
        if (stationId == null) {
            conn.disconnect()
            return@withContext ActionApiResponse(
                success = false,
                message = null,
                error = "Invalid station id"
            )
        }

        val payload = JSONObject().apply {
            put("id", stationId)
            put("name", station.name)
            put("address", station.address ?: "")
            put("coordinates", station.coordinates ?: "")
            put("hasPower", station.hasPower)
        }

        try {
            conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.readText().orEmpty()
            val responseJson = parseJsonObject(responseText)
            ActionApiResponse(
                success = responseJson?.optBoolean("success", conn.responseCode in 200..299) ?: (conn.responseCode in 200..299),
                message = responseJson?.optString("message"),
                error = responseJson?.optString("error")
            )
        } catch (e: Exception) {
            ActionApiResponse(
                success = false,
                message = null,
                error = e.message
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun updateStationSettings(station: WeatherStation): ActionApiResponse = withContext(Dispatchers.IO) {
        val conn = URL("$BASE_URL/settings").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val stationId = station.id.toIntOrNull()
        if (stationId == null) {
            conn.disconnect()
            return@withContext ActionApiResponse(
                success = false,
                message = null,
                error = "Invalid station id"
            )
        }

        val location = parseLocationParts(station.address)

        val payload = JSONObject().apply {
            put("id", stationId)
            put("name", station.name)
            put("suburb", location.suburb)
            put("state", location.state)
            put("country", location.country)
            put("coordinates", station.coordinates ?: "")
            put("hasPower", station.hasPower)
            put("settings", JSONArray().apply {
                station.settings.forEach { setting ->
                    put(JSONObject().apply {
                        put("key", setting.key)
                        put("value", setting.value)
                    })
                }
            })
        }

        try {
            conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.readText().orEmpty()
            val responseJson = parseJsonObject(responseText)
            ActionApiResponse(
                success = responseJson?.optBoolean("success", conn.responseCode in 200..299) ?: (conn.responseCode in 200..299),
                message = responseJson?.optString("message"),
                error = responseJson?.optString("error")
            )
        } catch (e: Exception) {
            ActionApiResponse(
                success = false,
                message = null,
                error = e.message
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun parseStationSettings(settingsJson: JSONArray?): List<StationSetting> {
        if (settingsJson == null) return emptyList()
        val settings = mutableListOf<StationSetting>()
        for (i in 0 until settingsJson.length()) {
            val setting = settingsJson.optJSONObject(i) ?: continue
            val key = setting.optString("key")
            if (key.isBlank()) continue
            settings.add(StationSetting(key = key, value = setting.optString("value")))
        }
        return settings
    }

    private fun parseStation(stationJson: JSONObject): WeatherStation {
        val suburb = stationJson.optString("suburb")
            .ifBlank { stationJson.optString("Suburb") }
            .ifBlank { "" }
        val state = stationJson.optString("state")
            .ifBlank { stationJson.optString("State") }
            .ifBlank { "" }
        val country = stationJson.optString("country")
            .ifBlank { stationJson.optString("Country") }
            .ifBlank { "" }
        val addressFallback = listOf(suburb, state, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        val hasPower = when {
            stationJson.has("hasPower") -> stationJson.optBoolean("hasPower", false)
            stationJson.has("HasPower") -> stationJson.optBoolean("HasPower", false)
            else -> false
        }
        return WeatherStation(
            id = stationJson.opt("id")?.toString().orEmpty(),
            name = stationJson.optString("name"),
            address = stationJson.optString("address").ifBlank { addressFallback.ifBlank { null } },
            coordinates = stationJson.optString("coordinates").ifBlank { null },
            hasPower = hasPower,
            settings = parseStationSettings(stationJson.optJSONArray("settings"))
        )
    }

    private data class LocationParts(
        val suburb: String,
        val state: String,
        val country: String
    )

    private fun parseLocationParts(address: String?): LocationParts {
        if (address.isNullOrBlank()) {
            return LocationParts("", "", "")
        }

        val parts = address.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size >= 3) {
            return LocationParts(
                suburb = parts[0],
                state = parts[1],
                country = parts.drop(2).joinToString(", ")
            )
        }

        if (parts.size == 2) {
            return LocationParts(
                suburb = parts[0],
                state = "",
                country = parts[1]
            )
        }

        return LocationParts(
            suburb = parts.firstOrNull().orEmpty(),
            state = "",
            country = ""
        )
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        if (raw.isBlank()) return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }
}
