package com.nexradwx.app.ui.radar

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.nexradwx.core.model.MomentCode
import com.nexradwx.core.model.RadarVolume
import com.nexradwx.core.render.PpiRasterizer
import com.nexradwx.core.render.colorFunctionFor
import com.nexradwx.core.site.RadarSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BASE_ZOOM = 7.5
private const val BASE_RASTER_SIZE_PX = 1024
private const val MAX_RASTER_SIZE_PX = 2048
private const val KM_PER_DEGREE_LAT = 111.32
private const val ZOOM_DEBOUNCE_MS = 150L

@Composable
fun RadarMap(
    site: RadarSite,
    volume: RadarVolume?,
    moment: MomentCode,
    rangeKm: Float,
    userLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    // Osmdroid needs a User-Agent to avoid being blocked by OSM servers.
    LaunchedEffect(context) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(7.5)
            controller.setCenter(GeoPoint(site.latitude.toDouble(), site.longitude.toDouble()))
        }
    }

    // Update center when site changes
    LaunchedEffect(site) {
        mapView.controller.animateTo(GeoPoint(site.latitude.toDouble(), site.longitude.toDouble()))
    }

    // Track zoom so the radar raster's resolution keeps up with it - without this the overlay
    // is a fixed-size bitmap that osmdroid just stretches over a bigger area, blurring visibly.
    var zoomLevel by remember { mutableStateOf(mapView.zoomLevelDouble) }
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false
            override fun onZoom(event: ZoomEvent?): Boolean {
                event?.let { zoomLevel = it.zoomLevel }
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    val rasterSizePx = remember(zoomLevel) {
        val scale = 2.0.pow(zoomLevel - BASE_ZOOM).coerceAtLeast(1.0)
        (BASE_RASTER_SIZE_PX * scale).roundToInt().coerceIn(BASE_RASTER_SIZE_PX, MAX_RASTER_SIZE_PX)
    }

    val sweep = remember(volume, moment) { volume?.lowestSweepWith(moment) }
    val nyquist = remember(sweep) {
        sweep?.radials?.firstNotNullOfOrNull { it.nyquistVelocityMs } ?: 1f
    }

    val bitmapState = produceState<Bitmap?>(initialValue = null, sweep, moment, rangeKm, rasterSizePx) {
        value = if (sweep == null) {
            null
        } else {
            // Debounce so a pinch-zoom gesture (which fires many onZoom events) collapses into a
            // single re-rasterization once the zoom level settles, instead of one per tick.
            delay(ZOOM_DEBOUNCE_MS)
            withContext(Dispatchers.Default) {
                val colorFor = colorFunctionFor(moment, nyquist)
                val pixels = PpiRasterizer.rasterize(
                    sweep = sweep,
                    moment = moment,
                    widthPx = rasterSizePx,
                    heightPx = rasterSizePx,
                    maxRangeKm = rangeKm,
                    colorFor = colorFor,
                )
                Bitmap.createBitmap(pixels, rasterSizePx, rasterSizePx, Bitmap.Config.ARGB_8888)
            }
        }
    }

    // Manage Overlays
    LaunchedEffect(bitmapState.value, site, rangeKm, userLocation) {
        mapView.overlays.clear()

        // 1. Radar Overlay
        bitmapState.value?.let { bitmap ->
            val latDelta = (rangeKm / KM_PER_DEGREE_LAT).toDouble()
            val lonDelta = (rangeKm / (KM_PER_DEGREE_LAT * cos(Math.toRadians(site.latitude.toDouble())))).toDouble()
            
            val north = site.latitude.toDouble() + latDelta
            val south = site.latitude.toDouble() - latDelta
            val east = site.longitude.toDouble() + lonDelta
            val west = site.longitude.toDouble() - lonDelta

            val groundOverlay = GroundOverlay().apply {
                setImage(bitmap)
                // setPosition takes (TopLeft GeoPoint, BottomRight GeoPoint)
                setPosition(GeoPoint(north, west), GeoPoint(south, east))
                transparency = 0.3f
            }
            mapView.overlays.add(groundOverlay)
        }

        // 2. Station Marker
        val stationMarker = Marker(mapView).apply {
            position = GeoPoint(site.latitude.toDouble(), site.longitude.toDouble())
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = site.id
            snippet = "NEXRAD Station"
        }
        mapView.overlays.add(stationMarker)

        // 3. User Location Overlay
        if (userLocation != null) {
            val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
            myLocationOverlay.enableMyLocation()
            mapView.overlays.add(myLocationOverlay)
        }

        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        // MapView is a real embedded View; Android always draws such interop Views after all
        // pure-Compose content in the same Owner, regardless of layout order, so its own async
        // redraws during panning/flinging would otherwise flash on top of the TabRow above it.
        // Forcing it through an offscreen compositing layer makes it respect normal Compose z-order.
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    )
}
