package dev.treder.info.claude.backdrop

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.W32APIOptions

/**
 * Applies Windows 10/11 acrylic blur-behind to a Compose Desktop window via the
 * undocumented user32!SetWindowCompositionAttribute API. This is the same
 * mechanism Explorer uses for the Start menu / search popup. Works with
 * per-pixel-alpha (layered) windows, which is what Compose `transparent = true`
 * produces.
 */
object WindowsBackdrop {

    private const val WCA_ACCENT_POLICY = 19
    private const val ACCENT_ENABLE_ACRYLICBLURBEHIND = 4
    private const val ACCENT_ENABLE_BLURBEHIND = 3
    private const val DRAW_LEFT_BORDER = 0x20
    private const val DRAW_TOP_BORDER = 0x40
    private const val DRAW_RIGHT_BORDER = 0x80
    private const val DRAW_BOTTOM_BORDER = 0x100

    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private val user32: User32Ex? = if (isWindows) {
        runCatching {
            Native.load("user32", User32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    } else null

    /**
     * Applies acrylic blur with the given ABGR tint. On Windows 10 pre-1803 falls
     * back to a plain blur-behind; on other platforms it's a no-op.
     * @param tintAbgr 32-bit ABGR color (Windows uses ABGR byte order). Use 0 for
     *                 minimal tint, or e.g. 0x90_20_20_20 for a dark glassy look.
     */
    fun apply(window: ComposeWindow, tintAbgr: Int = 0x90_20_20_20.toInt()) {
        val api = user32 ?: return
        val hwnd = HWND(Native.getWindowPointer(window))

        val accent = AccentPolicy().apply {
            accentState = ACCENT_ENABLE_ACRYLICBLURBEHIND
            accentFlags = DRAW_LEFT_BORDER or DRAW_TOP_BORDER or DRAW_RIGHT_BORDER or DRAW_BOTTOM_BORDER
            gradientColor = tintAbgr
            animationId = 0
        }
        accent.write()

        val data = WindowCompositionAttribData().apply {
            attribute = WCA_ACCENT_POLICY
            data = accent.pointer
            sizeOfData = accent.size()
        }

        val ok = api.SetWindowCompositionAttribute(hwnd, data)
        if (!ok) {
            // Acrylic unsupported (pre-Windows 10 1803) — try plain blur-behind.
            accent.accentState = ACCENT_ENABLE_BLURBEHIND
            accent.write()
            api.SetWindowCompositionAttribute(hwnd, data)
        }
    }

    @Suppress("FunctionName")
    private interface User32Ex : Library {
        fun SetWindowCompositionAttribute(hwnd: HWND, data: WindowCompositionAttribData): Boolean
    }

    @Structure.FieldOrder("accentState", "accentFlags", "gradientColor", "animationId")
    class AccentPolicy : Structure() {
        @JvmField var accentState: Int = 0
        @JvmField var accentFlags: Int = 0
        @JvmField var gradientColor: Int = 0
        @JvmField var animationId: Int = 0
    }

    @Structure.FieldOrder("attribute", "data", "sizeOfData")
    class WindowCompositionAttribData : Structure() {
        @JvmField var attribute: Int = 0
        @JvmField var data: Pointer? = null
        @JvmField var sizeOfData: Int = 0
    }
}
