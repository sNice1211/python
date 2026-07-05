package com.nexradwx.app.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexradwx.app.data.radar.RadarArchiveRepository
import com.nexradwx.app.data.radar.RadarFetchResult
import com.nexradwx.core.model.MomentCode
import com.nexradwx.core.model.RadarVolume
import com.nexradwx.core.site.RadarSite
import com.nexradwx.core.site.RadarSiteCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RadarUiState(
    val site: RadarSite = RadarSiteCatalog.findById("KTLX") ?: RadarSiteCatalog.sites.first(),
    val moment: MomentCode = MomentCode.REFLECTIVITY,
    val volume: RadarVolume? = null,
    val objectKey: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // 230 km / 124 nm matches the classic WSR-88D surveillance-cut display range.
    val rangeKm: Float = 230f,
)

class RadarViewModel : ViewModel() {
    private val repository = RadarArchiveRepository()

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectSite(site: RadarSite) {
        _uiState.value = _uiState.value.copy(site = site, volume = null, objectKey = null)
        refresh()
    }

    fun selectMoment(moment: MomentCode) {
        _uiState.value = _uiState.value.copy(moment = moment)
    }

    fun refresh() {
        val site = _uiState.value.site
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.fetchLatestVolume(site.id)) {
                is RadarFetchResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    volume = result.volume,
                    objectKey = result.objectKey,
                )
                is RadarFetchResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }
        }
    }
}
