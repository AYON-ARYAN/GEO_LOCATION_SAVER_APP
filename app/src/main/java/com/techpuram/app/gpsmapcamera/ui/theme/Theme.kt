package com.techpuram.app.gpsmapcamera.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// GPS Map Camera Fixed Color Scheme (Dark Theme)
private val GPSDarkColorScheme = darkColorScheme(
    primary = GPSPrimaryLight,
    onPrimary = GPSOnPrimary,
    primaryContainer = GPSPrimaryDark,
    onPrimaryContainer = GPSOnPrimary,
    
    secondary = GPSSecondary,
    onSecondary = GPSOnSecondary,
    secondaryContainer = GPSSecondaryDark,
    onSecondaryContainer = GPSOnSecondary,
    
    tertiary = GPSTertiary,
    onTertiary = GPSOnTertiary,
    tertiaryContainer = GPSTertiaryDark,
    onTertiaryContainer = GPSOnTertiary,
    
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCCCCCC),
    
    outline = GPSGray,
    outlineVariant = GPSGrayLight,
)

// GPS Map Camera Fixed Color Scheme (Light Theme)
private val GPSLightColorScheme = lightColorScheme(
    primary = GPSPrimary,
    onPrimary = GPSOnPrimary,
    primaryContainer = GPSPrimaryLight,
    onPrimaryContainer = GPSOnPrimary,
    
    secondary = GPSSecondary,
    onSecondary = GPSOnSecondary,
    secondaryContainer = GPSSecondaryLight,
    onSecondaryContainer = GPSOnSecondary,
    
    tertiary = GPSTertiary,
    onTertiary = GPSOnTertiary,
    tertiaryContainer = GPSTertiaryLight,
    onTertiaryContainer = GPSOnTertiary,
    
    background = GPSBackground,
    onBackground = GPSOnBackground,
    surface = GPSSurface,
    onSurface = GPSOnSurface,
    surfaceVariant = GPSSurfaceVariant,
    onSurfaceVariant = GPSOnSurface,
    
    outline = GPSGray,
    outlineVariant = GPSGrayLight,
)

@Composable
fun GPSmapCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color to use fixed brand colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use our fixed color scheme, ignoring dynamic colors
    val colorScheme = if (darkTheme) GPSDarkColorScheme else GPSLightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}