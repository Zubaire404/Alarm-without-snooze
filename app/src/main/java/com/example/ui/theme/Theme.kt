package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color


private val CleanMinimalismLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6750A4),        // M3 purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF), // Soft lavender workday list-item container
    onPrimaryContainer = Color(0xFF21005D), // Rich dark indigo text
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E4), // Soft highlight shell pink
    onSecondaryContainer = Color(0xFF31111D), // Dark plum text for rules or tabs
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFEF7FF),       // Clean minimalism background
    onBackground = Color(0xFF1D1B20),     // Soft rich charcoal text
    surface = Color(0xFFFEF7FF),          // Surface matching background
    onSurface = Color(0xFF1D1B20),
    outline = Color(0xFFCAC4D0)           // System outline grey border
  )

private val CleanMinimalismDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E5),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set false to strictly enforce the Gorgeous Clean Minimalism vibe
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) {
    CleanMinimalismDarkColorScheme
  } else {
    CleanMinimalismLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
