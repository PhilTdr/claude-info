package dev.treder.info.claude.tray

import androidx.compose.ui.unit.IntOffset
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

class TrayController(
    private val tooltip: String = "Claude Info",
    private val quitLabel: String = "Beenden",
    private val onLeftClick: (xOnScreen: Int, yOnScreen: Int) -> Unit,
    private val onQuit: () -> Unit,
) {

    private var trayIcon: TrayIcon? = null

    fun install(): Boolean {
        if (!SystemTray.isSupported()) return false
        val tray = SystemTray.getSystemTray()

        val image = try {
            ImageIO.read(Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png"))
        } catch (e: Exception) {
            val fallback = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val g = fallback.createGraphics()
            g.color = java.awt.Color.BLUE
            g.fillRect(0, 0, 16, 16)
            g.dispose()
            fallback
        }

        // invisible anchor dialog for right-click context menu
        val popupAnchor = javax.swing.JDialog().apply {
            isUndecorated = true
            setSize(1, 1)
            isAlwaysOnTop = true
        }

        val contextMenu = javax.swing.JPopupMenu().apply {
            add(javax.swing.JMenuItem(quitLabel).apply {
                addActionListener {
                    trayIcon?.let { tray.remove(it) }
                    exitProcess(0)
                }
            })
            addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent) {}
                override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent) {
                    popupAnchor.isVisible = false
                }
                override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent) {
                    popupAnchor.isVisible = false
                }
            })
        }

        val icon = TrayIcon(image, tooltip, null).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    when {
                        SwingUtilities.isLeftMouseButton(e) -> {
                            onLeftClick(e.xOnScreen, e.yOnScreen)
                        }
                        SwingUtilities.isRightMouseButton(e) -> {
                            val screen = resolveScreen(e.xOnScreen, e.yOnScreen)
                            val anchor = anchorForContextMenu(screen, contextMenu)
                            popupAnchor.setLocation(anchor.x, anchor.y)
                            popupAnchor.isVisible = true
                            contextMenu.show(popupAnchor, 0, 0)
                        }
                    }
                }
            })
        }

        tray.add(icon)
        trayIcon = icon
        return true
    }

    fun uninstall() {
        val icon = trayIcon ?: return
        runCatching { SystemTray.getSystemTray().remove(icon) }
        trayIcon = null
    }

    private data class ScreenContext(val userPoint: IntOffset, val config: GraphicsConfiguration)

    // AWT MouseEvent.xOnScreen for tray icons reports device pixels on HiDPI Windows,
    // but Swing setLocation expects user-space pixels. Without this conversion the
    // context menu anchor ends up off-screen and Windows clamps it to a corner.
    private fun resolveScreen(deviceX: Int, deviceY: Int): ScreenContext {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val device = env.screenDevices.firstOrNull { d ->
            val b = d.defaultConfiguration.bounds
            val tx = d.defaultConfiguration.defaultTransform
            val devX = (b.x * tx.scaleX).toInt()
            val devY = (b.y * tx.scaleY).toInt()
            val devW = (b.width * tx.scaleX).toInt()
            val devH = (b.height * tx.scaleY).toInt()
            deviceX in devX until (devX + devW) && deviceY in devY until (devY + devH)
        } ?: env.defaultScreenDevice
        val config = device.defaultConfiguration
        val transform = config.defaultTransform
        val scaleX = transform.scaleX.takeIf { it > 0 } ?: 1.0
        val scaleY = transform.scaleY.takeIf { it > 0 } ?: 1.0
        return ScreenContext(
            userPoint = IntOffset((deviceX / scaleX).toInt(), (deviceY / scaleY).toInt()),
            config = config,
        )
    }

    // Place the context menu on the opposite side of the taskbar — for a bottom
    // taskbar it expands upward, for a top taskbar downward, similarly horizontally
    // for side taskbars. Falls back to "down-right of cursor" when no clear taskbar.
    private fun anchorForContextMenu(screen: ScreenContext, menu: javax.swing.JPopupMenu): IntOffset {
        val (click, config) = screen
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val size = menu.preferredSize
        val maxInset = maxOf(insets.top, insets.bottom, insets.left, insets.right)
        return when {
            maxInset == insets.bottom && insets.bottom > 0 -> IntOffset(click.x, click.y - size.height)
            maxInset == insets.top && insets.top > 0 -> IntOffset(click.x, click.y)
            maxInset == insets.left && insets.left > 0 -> IntOffset(click.x, click.y)
            maxInset == insets.right && insets.right > 0 -> IntOffset(click.x - size.width, click.y)
            else -> IntOffset(click.x, click.y)
        }
    }
}
