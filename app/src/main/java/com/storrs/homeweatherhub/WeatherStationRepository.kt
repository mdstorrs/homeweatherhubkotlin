package com.storrs.homeweatherhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

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

data class FroniusApiResponse(
    val data: FroniusInverterData?,
    val success: Boolean,
    val message: String?,
    val error: String?
)

data class GoodWeApiResponse(
    val data: GoodWeData?,
    val success: Boolean,
    val message: String?,
    val error: String?
)

data class ZappiApiResponse(
    val data: ZappiData?,
    val success: Boolean,
    val message: String?,
    val error: String?
)

// TODO: Implement API calls to 'stations', 'current', and 'history' endpoints
object WeatherStationRepository {
    private const val BASE_URL = "https://api.homeweatherhub.com/Stations"
    private const val CURRENT_URL = "https://api.homeweatherhub.com/Current"
    private const val HISTORY_URL = "https://api.homeweatherhub.com/History"
    private const val ZAPPI_DIRECTOR_URL = "https://director.myenergi.net"
    private const val ZAPPI_FALLBACK_SERVER_URL = "https://s18.myenergi.net"
    private const val ZAPPI_STATUS_PATH = "/cgi-jstatus-Z"
    private const val GOODWE_PORT = 8899

    private val GOODWE_REQUEST_INVERTER_INFO = byteArrayOf(
        0xAA.toByte(), 0x55.toByte(), 0xC0.toByte(), 0x7F.toByte(), 0x01.toByte(), 0x02.toByte(), 0x00.toByte(), 0x02.toByte(), 0x41.toByte()
    )
    private val GOODWE_REQUEST_BATTERY = byteArrayOf(
        0xAA.toByte(), 0x55.toByte(), 0xC0.toByte(), 0x7F.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x40.toByte()
    )

    suspend fun fetchFroniusInverterData(inverterIp: String): FroniusApiResponse = withContext(Dispatchers.IO) {
        val ip = inverterIp.trim()
        if (ip.isBlank()) {
            return@withContext FroniusApiResponse(
                data = null,
                success = false,
                message = null,
                error = "Fronius IP is empty"
            )
        }

        val realtimeUrls = listOf(
            "http://$ip/solar_api/v1/GetPowerFlowRealtimeData.fcgi",
            "http://$ip/solar_api/v1/GetInverterRealtimeData.fcgi?Scope=System",
            "http://$ip/solar_api/v1/GetInverterRealtimeData.cgi?Scope=System",
            "http://$ip/components/readable"
        )

        var realtimeJson: String? = null
        var workingUrl: String? = null
        var lastError: Throwable? = null

        for (url in realtimeUrls) {
            try {
                realtimeJson = getHttpResponseText(url, timeoutMillis = 5000)
                workingUrl = url
                break
            } catch (ex: Throwable) {
                lastError = ex
            }
        }

        if (realtimeJson == null) {
            return@withContext when (lastError) {
                is SocketTimeoutException -> FroniusApiResponse(
                    data = null,
                    success = false,
                    message = null,
                    error = "Connection timeout. Inverter not responding."
                )
                else -> FroniusApiResponse(
                    data = null,
                    success = false,
                    message = null,
                    error = "Error reading Fronius data: ${lastError?.message ?: "All API endpoints failed"}"
                )
            }
        }

        return@withContext try {
            var data = parseFroniusRealtimeData(realtimeJson)

            val infoUrls = listOf(
                "http://$ip/solar_api/v1/GetInverterInfo.fcgi",
                "http://$ip/solar_api/v1/GetInverterInfo.cgi"
            )

            for (url in infoUrls) {
                try {
                    val infoJson = getHttpResponseText(url, timeoutMillis = 5000)
                    val infoModel = extractStringValue(infoJson, "\"CustomName\"")
                        .ifBlank { extractStringValue(infoJson, "\"Model\"") }
                    val infoSerial = extractStringValue(infoJson, "\"UniqueID\"")
                    data = data.copy(
                        model = if (infoModel.isNotBlank()) infoModel else data.model,
                        serialNumber = if (infoSerial.isNotBlank()) infoSerial else data.serialNumber
                    )
                    break
                } catch (_: Throwable) {
                    // Non-critical: keep realtime data even if info endpoint fails.
                }
            }

            FroniusApiResponse(
                data = data,
                success = true,
                message = workingUrl?.let { "Using API endpoint: ${it.replace("http://$ip", "")}" },
                error = null
            )
        } catch (ex: Throwable) {
            FroniusApiResponse(
                data = null,
                success = false,
                message = null,
                error = "Failed to parse Fronius response: ${ex.message}"
            )
        }
    }

    suspend fun fetchGoodWeData(inverterIp: String): GoodWeApiResponse = withContext(Dispatchers.IO) {
        val ip = inverterIp.trim()
        if (ip.isBlank()) {
            return@withContext GoodWeApiResponse(
                data = null,
                success = false,
                message = null,
                error = "GoodWe IP is empty"
            )
        }

        var info: GoodWeInverterInfo? = null
        var battery: GoodWeBatteryData? = null
        val parseErrors = mutableListOf<String>()

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 3000
                val address = InetAddress.getByName(ip)

                val response1 = sendAndReceiveUdp(
                    socket = socket,
                    address = address,
                    port = GOODWE_PORT,
                    request = GOODWE_REQUEST_INVERTER_INFO
                )

                val response2 = sendAndReceiveUdp(
                    socket = socket,
                    address = address,
                    port = GOODWE_PORT,
                    request = GOODWE_REQUEST_BATTERY
                )

                try {
                    info = parseGoodWeInverterInfoResponse(response1)
                } catch (ex: Throwable) {
                    parseErrors.add("Inverter parse error: ${ex.message}")
                }

                try {
                    battery = parseGoodWeBatteryResponse(response2)
                } catch (ex: Throwable) {
                    parseErrors.add("Battery parse error: ${ex.message}")
                }
            }

            val data = if (info != null || battery != null) GoodWeData(info, battery) else null
            GoodWeApiResponse(
                data = data,
                success = data != null,
                message = parseErrors.joinToString("\n").ifBlank { null },
                error = if (data == null) {
                    parseErrors.joinToString("\n").ifBlank { "Unable to parse GoodWe responses" }
                } else {
                    null
                }
            )
        } catch (ex: Throwable) {
            GoodWeApiResponse(
                data = null,
                success = false,
                message = null,
                error = "Connection error: ${ex.message}"
            )
        }
    }

    suspend fun fetchZappiData(hubSerial: String, apiKey: String): ZappiApiResponse = withContext(Dispatchers.IO) {
        val serial = hubSerial.trim()
        val key = apiKey.trim()
        if (serial.isBlank() || key.isBlank()) {
            return@withContext ZappiApiResponse(
                data = null,
                success = false,
                message = null,
                error = "Zappi serial and API key are required"
            )
        }

        try {
            val (json, serverUsed) = getZappiStatusWithRedirect(serial, key)

            if (!json.contains("\"zappi\"", ignoreCase = true)) {
                return@withContext ZappiApiResponse(
                    data = null,
                    success = false,
                    message = null,
                    error = "No Zappi found on this myenergi account."
                )
            }

            val data = parseZappiStatusData(json)
            ZappiApiResponse(
                data = data,
                success = true,
                message = "Using myenergi server: $serverUsed",
                error = null
            )
        } catch (ex: SocketTimeoutException) {
            ZappiApiResponse(
                data = null,
                success = false,
                message = null,
                error = "Connection timeout. myenergi server not responding."
            )
        } catch (ex: Exception) {
            ZappiApiResponse(
                data = null,
                success = false,
                message = null,
                error = "Error reading Zappi data: ${ex.message}"
            )
        }
    }

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

    private fun sendAndReceiveUdp(
        socket: DatagramSocket,
        address: InetAddress,
        port: Int,
        request: ByteArray
    ): ByteArray {
        val requestPacket = DatagramPacket(request, request.size, address, port)
        socket.send(requestPacket)

        val receiveBuffer = ByteArray(512)
        val responsePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
        socket.receive(responsePacket)
        return responsePacket.data.copyOf(responsePacket.length)
    }

    private fun getZappiStatusWithRedirect(hubSerial: String, apiKey: String): Pair<String, String> {
        var serverUrl = ZAPPI_DIRECTOR_URL
        repeat(4) {
            val response = authenticatedGet(
                url = "$serverUrl$ZAPPI_STATUS_PATH",
                username = hubSerial,
                password = apiKey,
                timeoutMillis = 10000
            )
            val asnHeaderRaw = response.headers.entries
                .firstOrNull { it.key?.equals("X_MYENERGI-asn", ignoreCase = true) == true }
                ?.value
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            val asnHeader = normalizeAsnHost(asnHeaderRaw)

            val undefinedAsnFromDirector =
                serverUrl.equals(ZAPPI_DIRECTOR_URL, ignoreCase = true) &&
                    asnHeader.equals("undefined", ignoreCase = true)
            if (undefinedAsnFromDirector) {
                serverUrl = ZAPPI_FALLBACK_SERVER_URL
                return@repeat
            }

            if (isValidAsnHost(asnHeader)) {
                val redirectedServer = "https://$asnHeader"
                if (!serverUrl.equals(redirectedServer, ignoreCase = true)) {
                    serverUrl = redirectedServer
                    return@repeat
                }
            }

            if (response.code !in 200..299) {
                // Temporary safety fallback: some accounts route to s18 but director can intermittently 500.
                if (response.code == 500 && serverUrl.equals(ZAPPI_DIRECTOR_URL, ignoreCase = true)) {
                    serverUrl = ZAPPI_FALLBACK_SERVER_URL
                    return@repeat
                }
                throw IllegalStateException(
                    "Server returned ${response.code} from $serverUrl. Check hub serial/API key and myenergi server status."
                )
            }

            return response.body to serverUrl
        }

        throw IllegalStateException("Too many server redirects from myenergi director.")
    }

    private fun isValidAsnHost(host: String): Boolean {
        if (host.isBlank()) return false
        val normalized = host.trim().lowercase()
        if (normalized == "undefined" || normalized == "null") return false
        // Accept typical DNS host labels and dots only.
        return normalized.matches(Regex("^[a-z0-9.-]+$"))
    }

    private fun normalizeAsnHost(raw: String): String {
        if (raw.isBlank()) return ""
        var value = raw.trim().trim('"', '\'', ' ')
        if (value.startsWith("https://", ignoreCase = true)) {
            value = value.removePrefix("https://")
        } else if (value.startsWith("http://", ignoreCase = true)) {
            value = value.removePrefix("http://")
        }
        return value.trimEnd('/')
    }

    private data class HttpResponseSnapshot(
        val code: Int,
        val headers: Map<String?, List<String>>,
        val body: String
    )

    private fun authenticatedGet(url: String, username: String, password: String, timeoutMillis: Int): HttpResponseSnapshot {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password.toCharArray())
            }
        })

        // First try challenge-based auth (Digest/Basic via Authenticator), then Basic on explicit 401.
        val firstAttempt = performGet(url = url, timeoutMillis = timeoutMillis, authorizationHeader = null)
        if (firstAttempt.code != 401) {
            return firstAttempt
        }

        val digestChallenge = headerValue(firstAttempt.headers, "WWW-Authenticate")
        if (digestChallenge.contains("Digest", ignoreCase = true)) {
            val digestHeader = buildDigestAuthorization(
                challengeHeader = digestChallenge,
                urlValue = url,
                username = username,
                password = password,
                method = "GET"
            )
            if (digestHeader.isNotBlank()) {
                val digestAttempt = performGet(
                    url = url,
                    timeoutMillis = timeoutMillis,
                    authorizationHeader = digestHeader
                )
                if (digestAttempt.code != 401) {
                    return digestAttempt
                }
            }
        }

        return performGet(
            url = url,
            timeoutMillis = timeoutMillis,
            authorizationHeader = buildBasicAuthorization(username, password)
        )
    }

    private fun headerValue(headers: Map<String?, List<String>>, key: String): String {
        return headers.entries
            .firstOrNull { it.key?.equals(key, ignoreCase = true) == true }
            ?.value
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun buildDigestAuthorization(
        challengeHeader: String,
        urlValue: String,
        username: String,
        password: String,
        method: String
    ): String {
        val challenge = challengeHeader.substringAfter("Digest", "").trim()
        if (challenge.isBlank()) return ""

        val params = mutableMapOf<String, String>()
        challenge.split(',').forEach { token ->
            val pair = token.trim().split('=', limit = 2)
            if (pair.size == 2) {
                val k = pair[0].trim().lowercase()
                val v = pair[1].trim().trim('"')
                params[k] = v
            }
        }

        val realm = params["realm"].orEmpty()
        val nonce = params["nonce"].orEmpty()
        val opaque = params["opaque"].orEmpty()
        val qopRaw = params["qop"].orEmpty()
        val qop = when {
            qopRaw.contains("auth", ignoreCase = true) -> "auth"
            qopRaw.isNotBlank() -> qopRaw.split(',').first().trim().lowercase()
            else -> ""
        }
        val algorithm = params["algorithm"].orEmpty().ifBlank { "MD5" }
        if (!algorithm.equals("MD5", ignoreCase = true)) return ""
        if (realm.isBlank() || nonce.isBlank()) return ""

        val uri = URL(urlValue).run {
            (path.ifBlank { "/" }) + (query?.let { "?$it" } ?: "")
        }
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val nc = "00000001"

        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")
        val response = if (qop.isNotBlank()) {
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }

        return buildString {
            append("Digest ")
            append("username=\"").append(username).append("\", ")
            append("realm=\"").append(realm).append("\", ")
            append("nonce=\"").append(nonce).append("\", ")
            append("uri=\"").append(uri).append("\", ")
            append("response=\"").append(response).append("\", ")
            if (algorithm.isNotBlank()) {
                append("algorithm=").append(algorithm.uppercase()).append(", ")
            }
            if (opaque.isNotBlank()) {
                append("opaque=\"").append(opaque).append("\", ")
            }
            if (qop.isNotBlank()) {
                append("qop=").append(qop).append(", ")
                append("nc=").append(nc).append(", ")
                append("cnonce=\"").append(cnonce).append("\"")
            } else {
                // Drop trailing comma+space if qop is absent.
                if (endsWith(", ")) {
                    setLength(length - 2)
                }
            }
        }
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildBasicAuthorization(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    private fun performGet(url: String, timeoutMillis: Int, authorizationHeader: String?): HttpResponseSnapshot {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutMillis
        conn.readTimeout = timeoutMillis
        if (!authorizationHeader.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", authorizationHeader)
        }
        return try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponseSnapshot(
                code = status,
                headers = conn.headerFields,
                body = body
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun getHttpResponseText(urlValue: String, timeoutMillis: Int): String {
        val conn = URL(urlValue).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutMillis
        conn.readTimeout = timeoutMillis
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}: $body")
            }
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun parseFroniusRealtimeData(jsonResponse: String): FroniusInverterData {
        val isGen24Format = jsonResponse.contains("PV_POWERACTIVE_MEAN_01_F32") ||
            jsonResponse.contains("ACBRIDGE_POWERACTIVE_MEAN_01_F32")

        return if (isGen24Format) {
            val pv1Power = extractGen24Value(jsonResponse, "PV_POWERACTIVE_MEAN_01_F32")
            val pv2Power = extractGen24Value(jsonResponse, "PV_POWERACTIVE_MEAN_02_F32")
            val pv1Voltage = extractGen24Value(jsonResponse, "PV_VOLTAGE_MEAN_01_F32")
            val pv2Voltage = extractGen24Value(jsonResponse, "PV_VOLTAGE_MEAN_02_F32")
            val pv1Current = extractGen24Value(jsonResponse, "PV_CURRENT_MEAN_01_F32")
            val pv2Current = extractGen24Value(jsonResponse, "PV_CURRENT_MEAN_02_F32")
            val model = extractStringValue(jsonResponse, "\"model\"").ifBlank { "Fronius Gen24" }

            FroniusInverterData(
                pv1Power = pv1Power,
                pv2Power = pv2Power,
                pv1Voltage = pv1Voltage,
                pv2Voltage = pv2Voltage,
                pv1Current = pv1Current,
                pv2Current = pv2Current,
                currentPower = extractGen24Value(jsonResponse, "ACBRIDGE_POWERACTIVE_MEAN_01_F32"),
                acVoltage = extractGen24Value(jsonResponse, "ACBRIDGE_VOLTAGE_MEAN_01_F32"),
                acCurrent = extractGen24Value(jsonResponse, "ACBRIDGE_CURRENT_ACTIVE_MEAN_01_F32"),
                acFrequency = extractGen24Value(jsonResponse, "ACBRIDGE_FREQUENCY_MEAN_F32"),
                gridVoltage = extractGen24Value(jsonResponse, "FEEDINPOINT_VOLTAGE_MEAN_01_F32"),
                gridFrequency = extractGen24Value(jsonResponse, "FEEDINPOINT_FREQUENCY_MEAN_F32"),
                ambientTemperature = extractGen24Value(jsonResponse, "DEVICE_TEMPERATURE_AMBIENTMEAN_01_F32"),
                uptime = extractGen24Value(jsonResponse, "DEVICE_TIME_UPTIME_SUM_F32"),
                dcVoltage = pv1Voltage,
                dcCurrent = pv1Current,
                model = model,
                deviceStatus = "Running"
            )
        } else {
            val statusCode = extractLegacyNumber(jsonResponse, "\"DeviceStatus\"", "StatusCode").toInt()
            FroniusInverterData(
                currentPower = extractLegacyNumber(jsonResponse, "\"PAC\"", "Value"),
                dayEnergy = extractLegacyNumber(jsonResponse, "\"DAY_ENERGY\"", "Value"),
                totalEnergy = extractLegacyNumber(jsonResponse, "\"TOTAL_ENERGY\"", "Value"),
                acVoltage = extractLegacyNumber(jsonResponse, "\"UAC\"", "Value"),
                acCurrent = extractLegacyNumber(jsonResponse, "\"IAC\"", "Value"),
                acFrequency = extractLegacyNumber(jsonResponse, "\"FAC\"", "Value"),
                dcVoltage = extractLegacyNumber(jsonResponse, "\"UDC\"", "Value"),
                dcCurrent = extractLegacyNumber(jsonResponse, "\"IDC\"", "Value"),
                deviceStatusCode = statusCode,
                deviceStatus = getFroniusStatusDescription(statusCode),
                errorCode = extractStringValue(jsonResponse, "\"ErrorCode\"")
            )
        }
    }

    private fun extractGen24Value(json: String, fieldName: String): Double {
        val searchPattern = "\"$fieldName\":"
        val fieldIndex = json.indexOf(searchPattern)
        if (fieldIndex == -1) return 0.0

        val valueStart = fieldIndex + searchPattern.length
        val endIndex = json.indexOfAny(charArrayOf(',', '}', ']'), valueStart)
        if (endIndex == -1) return 0.0

        val value = json.substring(valueStart, endIndex).trim()
        return value.toDoubleOrNull() ?: 0.0
    }

    private fun extractLegacyNumber(json: String, fieldName: String, propertyName: String): Double {
        val fieldIndex = json.indexOf(fieldName)
        if (fieldIndex == -1) return 0.0

        val propertyIndex = json.indexOf("\"$propertyName\"", fieldIndex)
        if (propertyIndex == -1) return 0.0

        val colonIndex = json.indexOf(':', propertyIndex)
        if (colonIndex == -1) return 0.0

        val endIndex = json.indexOfAny(charArrayOf(',', '}', ']'), colonIndex + 1)
        if (endIndex == -1) return 0.0

        val rawValue = json.substring(colonIndex + 1, endIndex).trim()
        return rawValue.toDoubleOrNull() ?: 0.0
    }

    private fun extractStringValue(json: String, fieldName: String): String {
        val fieldIndex = json.indexOf(fieldName)
        if (fieldIndex == -1) return ""

        val colonIndex = json.indexOf(':', fieldIndex)
        if (colonIndex == -1) return ""

        val firstQuote = json.indexOf('"', colonIndex)
        if (firstQuote == -1) return ""

        val secondQuote = json.indexOf('"', firstQuote + 1)
        if (secondQuote == -1) return ""

        return json.substring(firstQuote + 1, secondQuote)
    }

    private fun getFroniusStatusDescription(statusCode: Int): String {
        return when (statusCode) {
            0, 1, 2, 3, 4, 5, 6 -> "Startup"
            7 -> "Running"
            8 -> "Standby"
            9 -> "Bootloading"
            10 -> "Error"
            else -> "Unknown ($statusCode)"
        }
    }

    private fun parseZappiStatusData(jsonResponse: String): ZappiData {
        val serialNumber = extractNumberField(jsonResponse, "sno").toInt().toString()
        val chargingPower = extractNumberField(jsonResponse, "div")
        val gridPower = extractNumberField(jsonResponse, "grd")
        val generatedPower = extractNumberField(jsonResponse, "gen")
        val chargeAdded = extractNumberField(jsonResponse, "che")
        val supplyVoltage = extractNumberField(jsonResponse, "vol") / 10.0
        val supplyFrequency = extractNumberField(jsonResponse, "frq")
        val statusCode = extractNumberField(jsonResponse, "sta").toInt()
        val plugStatusCode = extractStringField(jsonResponse, "pst")
        val modeCode = extractNumberField(jsonResponse, "zmo").toInt()
        val minimumGreenLevel = extractNumberField(jsonResponse, "mgl").toInt()
        val firmwareVersion = extractStringField(jsonResponse, "fwv")
        val phases = extractNumberField(jsonResponse, "pha").toInt()

        val ct1Power = extractNumberField(jsonResponse, "ectp1")
        val ct2Power = extractNumberField(jsonResponse, "ectp2")
        val ct3Power = extractNumberField(jsonResponse, "ectp3")
        val ct4Power = extractNumberField(jsonResponse, "ectp4")
        val ct5Power = extractNumberField(jsonResponse, "ectp5")
        val ct6Power = extractNumberField(jsonResponse, "ectp6")

        val ct1Label = extractStringField(jsonResponse, "ectt1")
        val ct2Label = extractStringField(jsonResponse, "ectt2")
        val ct3Label = extractStringField(jsonResponse, "ectt3")
        val ct4Label = extractStringField(jsonResponse, "ectt4")
        val ct5Label = extractStringField(jsonResponse, "ectt5")
        val ct6Label = extractStringField(jsonResponse, "ectt6")

        val date = extractStringField(jsonResponse, "dat")
        val time = extractStringField(jsonResponse, "tim")

        val status = getZappiStatusDescription(statusCode)
        val plugStatus = getZappiPlugStatusDescription(plugStatusCode)
        val mode = getZappiModeDescription(modeCode)

        val isCharging = chargingPower > 0 && (statusCode == 3 || statusCode == 4 || plugStatusCode == "C2")

        return ZappiData(
            serialNumber = serialNumber,
            chargingPower = chargingPower,
            gridPower = gridPower,
            generatedPower = generatedPower,
            chargeAdded = chargeAdded,
            supplyVoltage = supplyVoltage,
            supplyFrequency = supplyFrequency,
            statusCode = statusCode,
            status = status,
            plugStatusCode = plugStatusCode,
            plugStatus = plugStatus,
            modeCode = modeCode,
            mode = mode,
            minimumGreenLevel = minimumGreenLevel,
            firmwareVersion = firmwareVersion,
            phases = phases,
            ct1Power = ct1Power,
            ct2Power = ct2Power,
            ct3Power = ct3Power,
            ct4Power = ct4Power,
            ct5Power = ct5Power,
            ct6Power = ct6Power,
            ct1Label = ct1Label,
            ct2Label = ct2Label,
            ct3Label = ct3Label,
            ct4Label = ct4Label,
            ct5Label = ct5Label,
            ct6Label = ct6Label,
            date = date,
            time = time,
            isCharging = isCharging
        )
    }

    private fun getZappiStatusDescription(statusCode: Int): String {
        return when (statusCode) {
            1 -> "Paused"
            2 -> "DSR"
            3 -> "Charging"
            4 -> "Boosting"
            5 -> "Complete"
            else -> "Unknown ($statusCode)"
        }
    }

    private fun getZappiPlugStatusDescription(plugStatusCode: String): String {
        return when (plugStatusCode) {
            "A" -> "EV Disconnected"
            "B1" -> "EV Connected"
            "B2" -> "Waiting for EV"
            "C1" -> "EV Ready to Charge"
            "C2" -> "Charging"
            "F" -> "Fault"
            else -> "Unknown ($plugStatusCode)"
        }
    }

    private fun getZappiModeDescription(modeCode: Int): String {
        return when (modeCode) {
            1 -> "Fast"
            2 -> "Eco"
            3 -> "Eco+"
            4 -> "Stopped"
            else -> "Unknown ($modeCode)"
        }
    }

    private fun extractNumberField(json: String, fieldName: String): Double {
        val searchPattern = "\"$fieldName\":"
        val fieldIndex = json.indexOf(searchPattern)
        if (fieldIndex == -1) return 0.0

        val valueStart = fieldIndex + searchPattern.length
        val valueEnd = json.indexOfAny(charArrayOf(',', '}', ']'), valueStart)
        if (valueEnd == -1) return 0.0

        return json.substring(valueStart, valueEnd).trim().toDoubleOrNull() ?: 0.0
    }

    private fun extractStringField(json: String, fieldName: String): String {
        val searchPattern = "\"$fieldName\":"
        val fieldIndex = json.indexOf(searchPattern)
        if (fieldIndex == -1) return ""

        val colonIndex = fieldIndex + searchPattern.length
        val startQuote = json.indexOf('"', colonIndex)
        if (startQuote == -1) return ""
        val endQuote = json.indexOf('"', startQuote + 1)
        if (endQuote == -1) return ""

        return json.substring(startQuote + 1, endQuote)
    }

    private fun parseGoodWeInverterInfoResponse(response: ByteArray): GoodWeInverterInfo {
        if (response.size < 6 || response[5] != 0x82.toByte()) {
            throw IllegalArgumentException("Not a valid 0x82 inverter info response")
        }

        val start = 6
        var end = response.size
        for (i in start until response.size) {
            if (response[i] == 0x03.toByte()) {
                end = i
                break
            }
        }

        val asciiBytes = response.copyOfRange(start, end)
            .filter { (it in 32..126) || it == 9.toByte() }
            .toByteArray()
        val ascii = asciiBytes.toString(Charsets.US_ASCII).trim()
        val parts = ascii.split(' ', '\t').filter { it.isNotBlank() }

        val modelLine = if (parts.isNotEmpty()) parts.take(2).joinToString(" ") else ""
        val serialLine = parts.firstOrNull { it.contains("CW") }.orEmpty()
        val firmwareLine = parts.firstOrNull { token ->
            token.contains("-") && token.all { ch -> ch.isDigit() || ch == '-' }
        }.orEmpty()

        return GoodWeInverterInfo(
            rawText = ascii,
            modelLine = modelLine,
            serialLine = serialLine,
            firmwareLine = firmwareLine,
            asciiData = response.joinToString("-") { "%02X".format(it) }
        )
    }

    private fun parseGoodWeBatteryResponse(response: ByteArray): GoodWeBatteryData {
        if (response.size < 93) {
            throw IllegalArgumentException("Invalid battery response length")
        }

        val pv1Voltage = readUInt16BigEndian(response, 7) / 10.0
        val pv2Voltage = readUInt16BigEndian(response, 9) / 10.0
        val pv1Current = readUInt16BigEndian(response, 11) / 10.0
        val pv2Current = readUInt16BigEndian(response, 13) / 10.0
        val pvTotalPower = (pv1Voltage * pv1Current) + (pv2Voltage * pv2Current)

        val gridPower = readInt16BigEndian(response, 21).toInt()
        val inverterVoltage = readUInt16BigEndian(response, 15) / 10.0
        val backupVoltage = readUInt16BigEndian(response, 75) / 10.0

        val stateOfCharge = response[58].toInt() and 0xFF
        val voltage = readUInt16BigEndian(response, 53) / 10.0
        val currentRaw = readInt16BigEndian(response, 59)
        val current = currentRaw / 10.0
        val power = (voltage * currentRaw) / 10.0

        val state = when {
            currentRaw < -50 -> "Charging"
            currentRaw > 50 -> "Discharging"
            else -> "Idle"
        }

        return GoodWeBatteryData(
            stateOfCharge = stateOfCharge,
            voltage = voltage,
            current = current,
            power = power,
            state = state,
            temperature = (response[88].toInt() and 0xFF) / 10.0,
            healthIndex = response[86].toInt() and 0xFF,
            chargeLimit = response[90].toInt() and 0xFF,
            dischargeLimit = response[92].toInt() and 0xFF,
            pv1Voltage = pv1Voltage,
            pv2Voltage = pv2Voltage,
            pv1Current = pv1Current,
            pv2Current = pv2Current,
            pvTotalPower = pvTotalPower,
            gridPower = gridPower,
            inverterVoltage = inverterVoltage,
            backupVoltage = backupVoltage,
            asciiData = response.joinToString("-") { "%02X".format(it) }
        )
    }

    private fun readUInt16BigEndian(buffer: ByteArray, offset: Int): Int {
        if (offset + 1 >= buffer.size) return 0
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }

    private fun readInt16BigEndian(buffer: ByteArray, offset: Int): Short {
        if (offset + 1 >= buffer.size) return 0
        return (((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)).toShort()
    }
}
