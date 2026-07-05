package com.nexradwx.app.ui.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexradwx.core.color.CorrelationCoefficientColorTable
import com.nexradwx.core.color.ReflectivityColorTable
import com.nexradwx.core.color.VelocityColorTable
import com.nexradwx.core.model.MomentCode

/** A compact horizontal color-scale strip for the active product, with min/mid/max labels. */
@Composable
fun ProductLegend(moment: MomentCode, nyquistMs: Float, modifier: Modifier = Modifier) {
    val stops = when (moment) {
        MomentCode.REFLECTIVITY -> (0..14).map { i ->
            val dbz = 5f + i * 5f
            Color(ReflectivityColorTable.colorFor(dbz))
        }
        MomentCode.VELOCITY -> (-20..20).map { i ->
            Color(VelocityColorTable.colorFor(i / 20f * nyquistMs, nyquistMs))
        }
        MomentCode.CORRELATION_COEFFICIENT -> (0..20).map { i ->
            Color(CorrelationCoefficientColorTable.colorFor(i / 20f))
        }
        else -> emptyList()
    }

    val (minLabel, midLabel, maxLabel) = when (moment) {
        MomentCode.REFLECTIVITY -> Triple("5 dBZ", "40 dBZ", "75+ dBZ")
        MomentCode.VELOCITY -> Triple("-%.0f m/s".format(nyquistMs), "0", "+%.0f m/s".format(nyquistMs))
        MomentCode.CORRELATION_COEFFICIENT -> Triple("0.0", "0.5", "1.0+")
        else -> Triple("", "", "")
    }

    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.weight(1f).height(14.dp)) {
            stops.forEach { color ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(color))
            }
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(minLabel, style = MaterialTheme.typography.labelSmall)
        Text(midLabel, style = MaterialTheme.typography.labelSmall)
        Text(maxLabel, style = MaterialTheme.typography.labelSmall)
    }
}
