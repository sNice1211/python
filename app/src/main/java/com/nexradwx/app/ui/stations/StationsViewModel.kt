package com.nexradwx.app.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexradwx.app.data.station.NwsStationRepository
import com.nexradwx.app.data.station.StationObservation
import com.nexradwx.app.data.station.StationResult
import com.nexradwx.app.data.station.WeatherStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StationsUiState(
    // Default center: Oklahoma City. There's no on-device location lookup in this build (see
    // README) -- pick a starting point with "Search near" instead.
    val latitude: Double = 35.4676,
    val longitude: Double = -97.5164,
    val stations: List<WeatherStation> = emptyList(),
    val selected: WeatherStation? = null,
    val observation: StationObservation? = null,
    val isLoadingStations: Boolean = false,
    val isLoadingObservation: Boolean = false,
    val errorMessage: String? = null,
)

class StationsViewModel : ViewModel() {
    private val repository = NwsStationRepository()

    private val _uiState = MutableStateFlow(StationsUiState())
    val uiState: StateFlow<StationsUiState> = _uiState.asStateFlow()

    init {
        loadNearby(_uiState.value.latitude, _uiState.value.longitude)
    }

    fun loadNearby(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            isLoadingStations = true,
            errorMessage = null,
            latitude = latitude,
            longitude = longitude,
        )
        viewModelScope.launch {
            when (val result = repository.nearbyStations(latitude, longitude)) {
                is StationResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoadingStations = false, stations = result.value)
                    result.value.firstOrNull()?.let { selectStation(it) }
                }
                is StationResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoadingStations = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun selectStation(station: WeatherStation) {
        _uiState.value = _uiState.value.copy(selected = station, isLoadingObservation = true, observation = null)
        viewModelScope.launch {
            when (val result = repository.latestObservation(station.id)) {
                is StationResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingObservation = false,
                    observation = result.value,
                )
                is StationResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoadingObservation = false,
                    errorMessage = result.message,
                )
            }
        }
    }
}
