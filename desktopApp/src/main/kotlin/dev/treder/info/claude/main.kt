package dev.treder.info.claude

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
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
import org.jetbrains.compose.resources.painterResource
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.SwingUtilities
import kotlin.math.abs

private const val POPUP_WIDTH_DP = 380
private const val INITIAL_HEIGHT_DP = 200
private const val TRAY_GAP_PX = 8

fun main() = application(exitProcessOnExit = true) {
    val app = remember { ClaudeInfoApp() }
    val viewModel = remember { app.createViewModel() }

    var popupVisible by remember { mutableStateOf(false) }
    var trayClick by remember { mutableStateOf<IntOffset?>(null) }
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
                popupVisible = !popupVisible
                composeWindowRef.value?.let {
                    setWindowPosition(click, it)
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

        DisposableEffect(currentWindow) {
            composeWindowRef.value = currentWindow
            Backdrop.apply(currentWindow)
            onDispose {
                if (composeWindowRef.value === currentWindow) composeWindowRef.value = null
            }
        }

        // Re-anchor after the window becomes visible or its content-driven size changes.
        // Re-apply the backdrop on each show: hiding a layered window on Windows
        // drops the composition attribute, so the blur would otherwise be lost.
        LaunchedEffect(popupVisible, windowState.size) {
            val click = trayClick
            val window = composeWindowRef.value
            if (popupVisible && click != null && window != null) {
                setWindowPosition(click, window)
                Backdrop.apply(window)
            }
        }

        ClaudeInfoTheme {
            UsageDashboard(
                viewModel = viewModel,
                backgroundColor = Backdrop.surfaceTint(),
                onContentSizeChanged = { intSize ->
                    if (intSize.height <= 0) return@UsageDashboard
                    val contentHeightDp = with(density) { intSize.height.toDp() }
                    if (abs((windowState.size.height - contentHeightDp).value) > 0.5f) {
                        windowState.size = DpSize(POPUP_WIDTH_DP.dp, contentHeightDp)
                    }
                },
                onClose = { popupVisible = false },
            )
        }
    }
}

fun setWindowPosition(click: IntOffset, window: ComposeWindow) {
    // AWT MouseEvent.xOnScreen for tray icons reports device pixels on HiDPI Windows,
    // while JFrame.setLocation expects user-space pixels. Find the screen containing
    // the click (in device space) and convert to user-space via its transform.
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val device = env.screenDevices.firstOrNull { d ->
        val b = d.defaultConfiguration.bounds
        val tx = d.defaultConfiguration.defaultTransform
        val devX = (b.x * tx.scaleX).toInt()
        val devY = (b.y * tx.scaleY).toInt()
        val devW = (b.width * tx.scaleX).toInt()
        val devH = (b.height * tx.scaleY).toInt()
        click.x in devX until (devX + devW) && click.y in devY until (devY + devH)
    } ?: env.defaultScreenDevice

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

    val widthPx = if (window.width > 0) window.width else POPUP_WIDTH_DP
    val heightPx = if (window.height > 0) window.height else INITIAL_HEIGHT_DP

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
