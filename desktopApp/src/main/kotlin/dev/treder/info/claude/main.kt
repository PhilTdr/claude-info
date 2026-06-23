package dev.treder.info.claude

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.treder.info.claude.backdrop.Backdrop
import dev.treder.info.claude.presentation.ui.ClaudeInfoTheme
import dev.treder.info.claude.presentation.ui.UsageDashboard
import dev.treder.info.claude.resources.Res
import dev.treder.info.claude.resources.icon
import dev.treder.info.claude.tray.TrayController
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.roundToInt

private const val POPUP_WIDTH_DP = 380
private const val INITIAL_HEIGHT_DP = 200
private const val TRAY_GAP_PX = 8

fun main() = application(exitProcessOnExit = true) {
    val app = remember { ClaudeInfoApp() }
    val viewModel = remember { app.createViewModel() }

    var popupVisible by remember { mutableStateOf(false) }
    var trayClick by remember { mutableStateOf<IntOffset?>(null) }
    // Bumped to force Compose to recreate the native window. A window built before a
    // monitor-configuration change (dock/undock) keeps a stale rendering density and
    // ends up clipped; only a fresh window (like an app restart) renders correctly.
    var windowGeneration by remember { mutableStateOf(0) }
    // Snapshot of the screen layout the current window was built for; if it differs at
    // open time, the monitors changed and the window must be rebuilt.
    var builtScreenSignature by remember { mutableStateOf("") }
    val composeWindowRef = remember { mutableStateOf<ComposeWindow?>(null) }
    val windowState = rememberWindowState(
        size = DpSize(POPUP_WIDTH_DP.dp, INITIAL_HEIGHT_DP.dp),
        position = WindowPosition.Aligned(Alignment.BottomEnd),
    )

    DisposableEffect(Unit) {
        val controller = TrayController(
            onLeftClick = { x, y ->
                val click = IntOffset(x, y)
                trayClick = click
                if (popupVisible) {
                    popupVisible = false
                } else {
                    // If the monitor configuration changed since the window was built
                    // (dock/undock, scaling change), rebuild it fresh so it renders at
                    // the current scale. The content-driven self-healing size and the
                    // target-size anchoring make the freshly built window settle to the
                    // right size and position, so we can open it in the same click.
                    val changed = composeWindowRef.value != null &&
                        screenConfigSignature() != builtScreenSignature
                    if (changed) {
                        windowState.size = DpSize(POPUP_WIDTH_DP.dp, INITIAL_HEIGHT_DP.dp)
                        windowGeneration++
                    }
                    popupVisible = true
                }
                composeWindowRef.value?.let {
                    setWindowPosition(
                        click, it,
                        windowState.size.width.value.roundToInt(),
                        windowState.size.height.value.roundToInt(),
                    )
                }
            },
            onQuit = {
                popupVisible = false
                exitApplication()
            },
        )
        val installed = controller.install()
        if (!installed) {
            // Fallback: no system tray available — show the window once so the
            // user can at least see the data, otherwise the app would be invisible.
            popupVisible = true
        }
        onDispose { controller.uninstall() }
    }

    // Watch for monitor-configuration changes (dock/undock, rescale) even while the
    // popup is hidden. When the layout differs from what the current window was built
    // for, rebuild the window ahead of time so the next open shows a fresh, correctly
    // rendered window (a stale window renders clipped and only a rebuild — like an app
    // restart — fixes it). Polling because AWT exposes no public display-change event.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (composeWindowRef.value != null && screenConfigSignature() != builtScreenSignature) {
                // Close the popup so the window is rebuilt while hidden and re-anchors
                // cleanly on the next open. Reopening a window across a screen change
                // leaves it mis-positioned, so we don't try to keep it visible.
                popupVisible = false
                windowState.size = DpSize(POPUP_WIDTH_DP.dp, INITIAL_HEIGHT_DP.dp)
                windowGeneration++
            }
        }
    }

    // Keyed on windowGeneration so a monitor-configuration change recreates the
    // native window: a fresh peer renders at the current scale, which a merely
    // hidden/shown window never re-reads (the source of the clipped popup).
    key(windowGeneration) {
        Window(
            icon = painterResource(Res.drawable.icon),
            onCloseRequest = { popupVisible = false },
            visible = popupVisible,
            state = windowState,
            title = "Claude Info",
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            focusable = true,
        ) {
            val density = LocalDensity.current
            val currentWindow = window
            var lastContentPx by remember { mutableStateOf(IntSize.Zero) }

            DisposableEffect(currentWindow) {
                composeWindowRef.value = currentWindow
                builtScreenSignature = screenConfigSignature()
                Backdrop.apply(currentWindow)
                onDispose {
                    if (composeWindowRef.value === currentWindow) composeWindowRef.value = null
                }
            }

            // Drive the window size from the measured content. Keyed on windowState.size
            // too: when Compose's componentResized feedback clobbers the size back to a
            // stale value (which happens when the window is recreated while visible after
            // a monitor change), re-assert the content-driven size instead of staying
            // stuck. Also pins the width to POPUP_WIDTH_DP against the same feedback.
            LaunchedEffect(lastContentPx, windowState.size, density) {
                if (lastContentPx.height <= 0) return@LaunchedEffect
                val targetHeight = with(density) { lastContentPx.height.toDp() }
                val off = abs((windowState.size.height - targetHeight).value) > 0.5f ||
                    abs((windowState.size.width - POPUP_WIDTH_DP.dp).value) > 0.5f
                if (off) windowState.size = DpSize(POPUP_WIDTH_DP.dp, targetHeight)
            }

            // Re-anchor after the window becomes visible or its content-driven size changes.
            // Re-apply the backdrop on each show: hiding a layered window on Windows
            // drops the composition attribute, so the blur would otherwise be lost.
            LaunchedEffect(popupVisible, windowState.size) {
                val click = trayClick
                val window = composeWindowRef.value
                if (popupVisible && click != null && window != null) {
                    // Position from the target size (windowState, in dp == AWT user px),
                    // not the live window height, which may still be the pre-grow value.
                    setWindowPosition(
                        click, window,
                        windowState.size.width.value.roundToInt(),
                        windowState.size.height.value.roundToInt(),
                    )
                    Backdrop.apply(window)
                }
            }

            ClaudeInfoTheme {
                UsageDashboard(
                    viewModel = viewModel,
                    backgroundColor = Backdrop.surfaceTint(),
                    onContentSizeChanged = { intSize ->
                        if (intSize.height > 0) lastContentPx = intSize
                    },
                    onClose = { popupVisible = false },
                    onOpenUrl = { url -> UrlOpener.open(url) },
                )
            }
        }
    }
}

/** The screen device whose device-pixel bounds contain [click] (tray clicks report device px). */
fun screenDeviceAt(click: IntOffset): GraphicsDevice {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return env.screenDevices.firstOrNull { d ->
        val b = d.defaultConfiguration.bounds
        val tx = d.defaultConfiguration.defaultTransform
        val devX = (b.x * tx.scaleX).toInt()
        val devY = (b.y * tx.scaleY).toInt()
        val devW = (b.width * tx.scaleX).toInt()
        val devH = (b.height * tx.scaleY).toInt()
        click.x in devX until (devX + devW) && click.y in devY until (devY + devH)
    } ?: env.defaultScreenDevice
}

/**
 * A signature of the current monitor layout (each screen's bounds + scale). Changes
 * when monitors are added/removed or rescaled — i.e. on dock/undock or a scaling change.
 */
fun screenConfigSignature(): String =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.joinToString("|") { d ->
        val b = d.defaultConfiguration.bounds
        val s = d.defaultConfiguration.defaultTransform.scaleX
        "${b.x},${b.y},${b.width},${b.height}@$s"
    }

fun setWindowPosition(click: IntOffset, window: ComposeWindow, widthPx: Int, heightPx: Int) {
    // AWT MouseEvent.xOnScreen for tray icons reports device pixels on HiDPI Windows,
    // while JFrame.setLocation expects user-space pixels. Find the screen containing
    // the click (in device space) and convert to user-space via its transform.
    val device = screenDeviceAt(click)

    val config: GraphicsConfiguration = device.defaultConfiguration
    val scaleX = config.defaultTransform.scaleX.takeIf { it > 0 } ?: 1.0
    val scaleY = config.defaultTransform.scaleY.takeIf { it > 0 } ?: 1.0
    val clickXUser = (click.x / scaleX).toInt()
    val clickYUser = (click.y / scaleY).toInt()

    // Work area = screen bounds minus taskbar/dock insets.
    val screen = config.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val workLeft = screen.x + insets.left
    val workTop = screen.y + insets.top
    val workRight = screen.x + screen.width - insets.right
    val workBottom = screen.y + screen.height - insets.bottom

    // Anchor the popup to the opposite side of the taskbar (deduced from the
    // largest non-zero inset). On a top taskbar the popup drops down; on a side
    // taskbar it expands horizontally; otherwise (the common bottom case) it goes up.
    val maxInset = maxOf(insets.top, insets.bottom, insets.left, insets.right)
    val clampX: (Int) -> Int = { it.coerceIn(workLeft, (workRight - widthPx).coerceAtLeast(workLeft)) }
    val clampY: (Int) -> Int = { it.coerceIn(workTop, (workBottom - heightPx).coerceAtLeast(workTop)) }
    val (targetX, targetY) = when {
        maxInset == insets.top && insets.top > 0 ->
            clampX(clickXUser - widthPx / 2) to clampY(clickYUser + TRAY_GAP_PX)
        maxInset == insets.left && insets.left > 0 ->
            clampX(clickXUser + TRAY_GAP_PX) to clampY(clickYUser - heightPx / 2)
        maxInset == insets.right && insets.right > 0 ->
            clampX(clickXUser - widthPx - TRAY_GAP_PX) to clampY(clickYUser - heightPx / 2)
        else ->
            clampX(clickXUser - widthPx / 2) to clampY(clickYUser - heightPx - TRAY_GAP_PX)
    }

    SwingUtilities.invokeLater { window.setLocation(targetX, targetY) }
}
