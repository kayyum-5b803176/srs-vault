package com.srspassword.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary         = Color(0xFF6C9EFF),
    onPrimary       = Color(0xFF002D6E),
    primaryContainer= Color(0xFF1A4090),
    secondary       = Color(0xFF82CFEA),
    tertiary        = Color(0xFFB5C4FF),
    background      = Color(0xFF0F1117),
    surface         = Color(0xFF1A1D27),
    surfaceVariant  = Color(0xFF252836),
    onBackground    = Color(0xFFE2E4F0),
    onSurface       = Color(0xFFE2E4F0),
    error           = Color(0xFFFF6B6B),
    outline         = Color(0xFF3E4257)
)

private val LightColorScheme = lightColorScheme(
    primary         = Color(0xFF1A4090),
    onPrimary       = Color.White,
    primaryContainer= Color(0xFFD8E4FF),
    secondary       = Color(0xFF0C6A88),
    tertiary        = Color(0xFF3F52A0),
    background      = Color(0xFFF6F7FC),
    surface         = Color.White,
    surfaceVariant  = Color(0xFFEEF0FA),
    onBackground    = Color(0xFF1A1D27),
    onSurface       = Color(0xFF1A1D27),
    error           = Color(0xFFB3261E),
    outline         = Color(0xFFC4C6D8)
)

@Composable
fun SRSPasswordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content
    )
}
