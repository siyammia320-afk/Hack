package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Flat, matte dark color palette with ZERO glow for clean MT Manager performance
private val FlatDarkColorScheme = darkColorScheme(
  primary = Color(0xFF00E676),
  secondary = Color(0xFF90A4AE),
  tertiary = Color(0xFFFFB300),
  background = Color(0xFF141A22),
  surface = Color(0xFF19202A),
  onPrimary = Color.Black,
  onSecondary = Color.White,
  onTertiary = Color.Black,
  onBackground = Color(0xFFECEFF1),
  onSurface = Color(0xFFECEFF1)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = FlatDarkColorScheme,
    typography = Typography,
    content = content
  )
}

