package com.storrs.homeweatherhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                stations.add(
                    WeatherStation(
                        id = s.getString("id"),
                        name = s.getString("name"),
                        address = s.optString("address"),
                        coordinates = s.optString("coordinates")
                    )
                )
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
}
