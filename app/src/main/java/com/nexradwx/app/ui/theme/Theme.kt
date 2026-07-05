package com.nexradwx.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexradWxColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF4DE39A),
    secondary = androidx.compose.ui.graphics.Color(0xFF7FB3FF),
    background = androidx.compose.ui.graphics.Color(0xFF0B1220),
    surface = androidx.compose.ui.graphics.Color(0xFF121B2E),
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
)

/** Dark-first theme: radar/nowcasting tools are overwhelmingly used against a dark PPI display. */
@Composable
fun NexradWxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexradWxColorScheme,
        content = content,
    )
}
