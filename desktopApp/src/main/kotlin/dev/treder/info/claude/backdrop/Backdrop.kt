package dev.treder.info.claude.backdrop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color

/**
 * Platform-aware popup backdrop. [apply] installs the OS-native blur where
 * available (Acrylic on Windows, NSVisualEffectView on macOS) and [surfaceTint]
 * returns the Compose-side fill the dashboard should paint on top of it.
 *
 * - Windows: Acrylic's `gradientColor` already bakes in a dark tint, so the
 *   Compose surface stays fully transparent.
 * - macOS: NSVisualEffectView only blurs — its tint follows the system Light/
 *   Dark setting, which would wash out our dark theme on a Light-mode system.
 *   Compose paints its own translucent dark tint on top of the OS blur.
 * - Linux: no OS blur, so Compose paints the full translucent Material tint.
 */
object Backdrop {
    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = osName.contains("win")
    private val isMac = osName.contains("mac") || osName.contains("darwin")

    @Composable
    fun surfaceTint(): Color {
        val surface = MaterialTheme.colorScheme.surface
        return when {
            isWindows -> Color.Transparent
            // Liquid Glass (macOS 26+) does its own contrast adaption and
            // specular highlighting — any Compose tint just mutes the effect.
            // Let the glass material itself carry the look.
            isMac && MacBackdrop.hasLiquidGlass -> Color.Transparent
            isMac -> surface.copy(alpha = 0.55f)
            else -> surface.copy(alpha = 0.86f)
        }
    }

    fun apply(window: ComposeWindow) {
        when {
            isWindows -> WindowsBackdrop.apply(window)
            isMac -> MacBackdrop.apply(window)
        }
    }
}
