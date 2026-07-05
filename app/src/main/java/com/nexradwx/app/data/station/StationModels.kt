package com.nexradwx.app.data.station

data class WeatherStation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/** Raw surface observation from a METAR-reporting station, as published by api.weather.gov. */
data class StationObservation(
    val stationId: String,
    val timestampIso: String?,
    val temperatureC: Double?,
    val dewpointC: Double?,
    val windDirectionDeg: Double?,
    val windSpeedKmh: Double?,
    val windGustKmh: Double?,
    val barometricPressurePa: Double?,
    val visibilityMeters: Double?,
    val textDescription: String?,
    val rawMessage: String?,
)
