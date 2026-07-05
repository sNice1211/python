package com.nexradwx.app.data.station

import com.nexradwx.app.network.HttpClientProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

sealed class StationResult<out T> {
    data class Success<T>(val value: T) : StationResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : StationResult<Nothing>()
}

/**
 * Talks to the National Weather Service's free, keyless api.weather.gov.
 *
 * NWS asks every client to self-identify with a descriptive User-Agent (app name + contact) --
 * see https://www.weather.gov/documentation/services-web-api. Replace [USER_AGENT] with your own
 * before shipping.
 */
class NwsStationRepository {
    companion object {
        private const val USER_AGENT = "NexradWx/0.1 (replace-with-your-contact-info)"
    }

    suspend fun nearbyStations(latitude: Double, longitude: Double): StationResult<List<WeatherStation>> =
        withContext(Dispatchers.IO) {
            try {
                val pointJson = getJson("https://api.weather.gov/points/$latitude,$longitude")
                val stationsUrl = pointJson.getJSONObject("properties").getString("observationStations")
                val stationsJson = getJson(stationsUrl)
                val features = stationsJson.getJSONArray("features")
                val stations = buildList {
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val props = feature.getJSONObject("properties")
                        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                        add(
                            WeatherStation(
                                id = props.getString("stationIdentifier"),
                                name = props.optString("name", props.getString("stationIdentifier")),
                                longitude = coords.getDouble(0),
                                latitude = coords.getDouble(1),
                            ),
                        )
                    }
                }
                StationResult.Success(stations)
            } catch (e: Exception) {
                StationResult.Failure("Could not load nearby stations: ${e.message}", e)
            }
        }

    suspend fun latestObservation(stationId: String): StationResult<StationObservation> =
        withContext(Dispatchers.IO) {
            try {
                val json = getJson("https://api.weather.gov/stations/$stationId/observations/latest")
                val props = json.getJSONObject("properties")
                StationResult.Success(
                    StationObservation(
                        stationId = stationId,
                        timestampIso = props.optStringOrNull("timestamp"),
                        temperatureC = props.optQuantity("temperature"),
                        dewpointC = props.optQuantity("dewpoint"),
                        windDirectionDeg = props.optQuantity("windDirection"),
                        windSpeedKmh = props.optQuantity("windSpeed"),
                        windGustKmh = props.optQuantity("windGust"),
                        barometricPressurePa = props.optQuantity("barometricPressure"),
                        visibilityMeters = props.optQuantity("visibility"),
                        textDescription = props.optStringOrNull("textDescription"),
                        rawMessage = props.optStringOrNull("rawMessage"),
                    ),
                )
            } catch (e: Exception) {
                StationResult.Failure("Could not load observation for $stationId: ${e.message}", e)
            }
        }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/geo+json")
            .build()
        HttpClientProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IOException("Empty body for $url")
            return JSONObject(body)
        }
    }
}

/** api.weather.gov wraps most numeric fields as {"value": <num-or-null>, "unitCode": "..."}. */
private fun JSONObject.optQuantity(field: String): Double? {
    val obj = optJSONObject(field) ?: return null
    if (obj.isNull("value")) return null
    val value = obj.optDouble("value")
    return value.takeIf { !it.isNaN() }
}

private fun JSONObject.optStringOrNull(field: String): String? =
    if (has(field) && !isNull(field)) getString(field) else null
