package com.moshbari.yaptext.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand colors — blue keyboard + orange mic, matching the iOS app.
val YapBlue = Color(0xFF1A4DE6)
val YapBlueDark = Color(0xFF0F1E4D)
val YapOrange = Color(0xFFFF8C00)
val YapPurple = Color(0xFF7B2FF7)
val YapGreen = Color(0xFF34C759)

private val LightColors = lightColorScheme(
    primary = YapBlue,
    secondary = YapOrange,
    tertiary = YapPurple,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6E8BFF),
    secondary = YapOrange,
    tertiary = Color(0xFFB28BFF),
)

@Composable
fun YapTextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
