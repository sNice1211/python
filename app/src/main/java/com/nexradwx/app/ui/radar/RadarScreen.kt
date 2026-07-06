package com.nexradwx.app.ui.radar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexradwx.core.model.MomentCode
import com.nexradwx.core.site.RadarSite
import com.nexradwx.core.site.RadarSiteCatalog

private val DISPLAYED_MOMENTS = listOf(
    MomentCode.REFLECTIVITY,
    MomentCode.VELOCITY,
    MomentCode.CORRELATION_COEFFICIENT,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(viewModel: RadarViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showSitePicker by remember { mutableStateOf(false) }
    var useMap by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { showSitePicker = true }) {
                        Text(uiState.site.id, style = MaterialTheme.typography.titleLarge)
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "Change radar site",
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.useMyLocation() }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
                    }
                    IconButton(onClick = { useMap = !useMap }) {
                        Icon(
                            if (useMap) Icons.Filled.Radar else Icons.Filled.Map,
                            contentDescription = if (useMap) "Switch to PPI" else "Switch to Map"
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val selectedIndex = DISPLAYED_MOMENTS.indexOf(uiState.moment).coerceAtLeast(0)
            TabRow(selectedTabIndex = selectedIndex) {
                DISPLAYED_MOMENTS.forEach { moment ->
                    Tab(
                        selected = moment == uiState.moment,
                        onClick = { viewModel.selectMoment(moment) },
                        text = { Text(moment.displayName) },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (useMap) {
                    RadarMap(
                        site = uiState.site,
                        volume = uiState.volume,
                        moment = uiState.moment,
                        rangeKm = uiState.rangeKm,
                        userLocation = uiState.userLocation
                    )
                } else {
                    PpiView(
                        volume = uiState.volume,
                        moment = uiState.moment,
                        rangeKm = uiState.rangeKm,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                }
            }

            val nyquist = uiState.volume
                ?.lowestSweepWith(uiState.moment)
                ?.radials
                ?.firstNotNullOfOrNull { it.nyquistVelocityMs }
                ?: 1f
            ProductLegend(moment = uiState.moment, nyquistMs = nyquist)

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            uiState.objectKey?.let { key ->
                Text(
                    text = "Source: $key",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (showSitePicker) {
        SitePickerDialog(
            currentSite = uiState.site,
            onDismiss = { showSitePicker = false },
            onSiteSelected = { site ->
                viewModel.selectSite(site)
                showSitePicker = false
            },
        )
    }
}

@Composable
private fun SitePickerDialog(
    currentSite: RadarSite,
    onDismiss: () -> Unit,
    onSiteSelected: (RadarSite) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { RadarSiteCatalog.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a radar site") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search by ID (e.g. KTLX)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    contentPadding = PaddingValues(top = 8.dp),
                ) {
                    items(results, key = { it.id }) { site ->
                        ListItem(
                            headlineContent = { Text(site.id) },
                            supportingContent = {
                                Text("%.2f, %.2f".format(site.latitude, site.longitude))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSiteSelected(site) },
                            colors = if (site.id == currentSite.id) {
                                androidx.compose.material3.ListItemDefaults.colors(
                                    headlineColor = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                androidx.compose.material3.ListItemDefaults.colors()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
