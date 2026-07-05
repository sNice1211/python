package com.nexradwx.app.ui.stations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexradwx.app.data.station.StationObservation
import com.nexradwx.app.data.station.WeatherStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(viewModel: StationsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var latText by remember { mutableStateOf(uiState.latitude.toString()) }
    var lonText by remember { mutableStateOf(uiState.longitude.toString()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Surface Stations") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Lat") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Lon") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    if (lat != null && lon != null) viewModel.loadNearby(lat, lon)
                }) {
                    Text("Go")
                }
            }

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.isLoadingStations) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                    LazyColumn {
                        items(uiState.stations, key = { it.id }) { station ->
                            StationRow(
                                station = station,
                                selected = station.id == uiState.selected?.id,
                                onClick = { viewModel.selectStation(station) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Column(modifier = Modifier.weight(1.2f).padding(12.dp)) {
                    if (uiState.isLoadingObservation) {
                        CircularProgressIndicator()
                    }
                    uiState.observation?.let { ObservationCard(it) }
                }
            }
        }
    }
}

@Composable
private fun StationRow(station: WeatherStation, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(station.id) },
        supportingContent = { Text(station.name) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = if (selected) {
            ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary)
        } else {
            ListItemDefaults.colors()
        },
    )
}

@Composable
private fun ObservationCard(observation: StationObservation) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(observation.stationId, style = MaterialTheme.typography.titleMedium)
            observation.timestampIso?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ObservationRow("Temperature", observation.temperatureC?.let { "%.1f C".format(it) })
            ObservationRow("Dewpoint", observation.dewpointC?.let { "%.1f C".format(it) })
            ObservationRow(
                "Wind",
                if (observation.windSpeedKmh != null) {
                    "%.0f deg @ %.0f km/h".format(observation.windDirectionDeg ?: 0.0, observation.windSpeedKmh)
                } else {
                    null
                },
            )
            ObservationRow("Gust", observation.windGustKmh?.let { "%.0f km/h".format(it) })
            ObservationRow("Pressure", observation.barometricPressurePa?.let { "%.0f Pa".format(it) })
            ObservationRow("Visibility", observation.visibilityMeters?.let { "%.0f m".format(it) })
            observation.textDescription?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            observation.rawMessage?.let {
                Text(
                    "METAR: $it",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ObservationRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value ?: "--", style = MaterialTheme.typography.bodySmall)
    }
}
