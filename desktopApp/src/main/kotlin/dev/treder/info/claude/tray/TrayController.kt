package dev.treder.info.claude.tray

import androidx.compose.ui.unit.IntOffset
import dev.treder.info.claude.autostart.Autostart
import java.awt.Dimension
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
    private val autostart: Autostart? = Autostart.forCurrentOs(),
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

        val contextMenu = buildContextMenu(
            onSelect = {
                trayIcon?.let { tray.remove(it) }
                exitProcess(0)
            },
            onHidden = { popupAnchor.isVisible = false },
        )

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

    private val surface = java.awt.Color(0x1A1715)
    private val onSurface = java.awt.Color(0xE6E1DC)
    private val accent = java.awt.Color(0xD97757)
    private val outline = java.awt.Color(0x3A332F)

    // The default Swing JMenuItem reserves a left gutter for an icon/check mark and
    // renders in the platform Look&Feel — on Windows that produces the empty left
    // spacing and a bright menu that clashes with the dark, frosted popup window.
    // We build the menu from plain JLabels instead so we fully control padding and
    // colors: dark Claude surface, light text, orange hover, no icon gutter.
    private fun buildContextMenu(
        onSelect: () -> Unit,
        onHidden: () -> Unit,
    ): javax.swing.JPopupMenu = javax.swing.JPopupMenu().apply {
        isOpaque = true
        background = surface
        border = javax.swing.BorderFactory.createLineBorder(outline, 1)

        // Autostart toggle: a JLabel click does not dismiss the popup, so the menu
        // stays open and the check mark updates in place to mirror the new OS state.
        val toggle = autostart?.let { auto ->
            styledItem(checkText(auto.menuLabel, auto.isEnabled())).also { label ->
                label.addMouseListener(object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent) {
                        val nowEnabled = auto.setEnabled(!auto.isEnabled())
                        label.text = checkText(auto.menuLabel, nowEnabled)
                    }
                })
            }
        }

        val quit = styledItem(quitLabel).also { label ->
            label.addMouseListener(object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    onSelect()
                }
            })
        }

        // All rows share one width: the widest, measured for the toggle in its CHECKED
        // state. This keeps the check mark from being truncated when switched on (the
        // row no longer grows past its layout width), and makes every row's hover
        // highlight span the full menu — a JLabel otherwise paints its background only
        // across its own text width, which left "Beenden" visibly narrow.
        val checkedWidth = autostart
            ?.let { styledItem(checkText(it.menuLabel, enabled = true)).preferredSize.width }
            ?: 0
        val rowWidth = maxOf(checkedWidth, quit.preferredSize.width)
        listOfNotNull(toggle, quit).forEach { label ->
            label.preferredSize = Dimension(rowWidth, label.preferredSize.height)
            label.maximumSize = Dimension(rowWidth, label.preferredSize.height)
        }

        toggle?.let { add(it); add(divider(rowWidth)) }
        add(quit)

        addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent) {}
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent) {
                onHidden()
            }
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent) {
                onHidden()
            }
        })
    }

    /** A clickable, hover-highlighting menu row in the dark Claude palette. */
    private fun styledItem(text: String): javax.swing.JLabel =
        javax.swing.JLabel(text).apply {
            isOpaque = true
            background = surface
            foreground = onSurface
            font = font.deriveFont(13f)
            border = javax.swing.BorderFactory.createEmptyBorder(8, 16, 8, 28)
            horizontalAlignment = javax.swing.SwingConstants.LEFT
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    background = accent
                    foreground = java.awt.Color.BLACK
                }
                override fun mouseExited(e: MouseEvent) {
                    background = surface
                    foreground = onSurface
                }
            })
        }

    /** A thin divider line in the outline color, spanning the full [width] of the menu. */
    private fun divider(width: Int): javax.swing.JComponent = javax.swing.JPanel().apply {
        background = outline
        preferredSize = Dimension(width, 1)
        maximumSize = Dimension(width, 1)
        alignmentX = java.awt.Component.LEFT_ALIGNMENT
    }

    /** Appends a trailing check mark when [enabled], so the row reads as a toggle. */
    private fun checkText(label: String, enabled: Boolean): String =
        if (enabled) "$label   ✓" else label

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
