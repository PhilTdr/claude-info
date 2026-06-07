package dev.treder.info.claude.presentation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClaudeOrange = Color(0xFFD97757)
private val SoftBeige = Color(0xFFB39A87)
private val SurfaceDark = Color(0xFF1A1715)
private val BackgroundDark = Color(0xFF120F0D)
private val OnSurfaceLight = Color(0xFFE6E1DC)
private val OutlineSubtle = Color(0xFF3A332F)

private val claudeInfoColors = darkColorScheme(
    primary = ClaudeOrange,
    onPrimary = Color.Black,
    secondary = SoftBeige,
    surface = SurfaceDark,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = OnSurfaceLight,
    background = BackgroundDark,
    onBackground = OnSurfaceLight,
    outline = OutlineSubtle,
)

@Composable
fun ClaudeInfoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = claudeInfoColors, content = content)
}
