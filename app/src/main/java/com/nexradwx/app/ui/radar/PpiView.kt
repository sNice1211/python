package com.nexradwx.app.ui.radar

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nexradwx.core.model.MomentCode
import com.nexradwx.core.model.RadarVolume
import com.nexradwx.core.render.PpiRasterizer
import com.nexradwx.core.render.colorFunctionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RASTER_SIZE_PX = 640
private const val DEFAULT_NYQUIST_MS = 1f

/** Plan position indicator: renders the lowest sweep carrying [moment] as a north-up radar image. */
@Composable
fun PpiView(
    volume: RadarVolume?,
    moment: MomentCode,
    rangeKm: Float,
    modifier: Modifier = Modifier,
) {
    val sweep = remember(volume, moment) { volume?.lowestSweepWith(moment) }
    val nyquist = remember(sweep) {
        sweep?.radials?.firstNotNullOfOrNull { it.nyquistVelocityMs } ?: DEFAULT_NYQUIST_MS
    }

    val bitmapState = produceState<Bitmap?>(initialValue = null, sweep, moment, rangeKm) {
        value = if (sweep == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val colorFor = colorFunctionFor(moment, nyquist)
                val pixels = PpiRasterizer.rasterize(
                    sweep = sweep,
                    moment = moment,
                    widthPx = RASTER_SIZE_PX,
                    heightPx = RASTER_SIZE_PX,
                    maxRangeKm = rangeKm,
                    colorFor = colorFor,
                )
                Bitmap.createBitmap(pixels, RASTER_SIZE_PX, RASTER_SIZE_PX, Bitmap.Config.ARGB_8888)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        bitmapState.value?.let { bitmap ->
            drawImage(
                image = bitmap.asImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }

        val ringCount = 4
        val center = Offset(size.width / 2f, size.height / 2f)
        for (i in 1..ringCount) {
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = size.minDimension / 2f * i / ringCount,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
