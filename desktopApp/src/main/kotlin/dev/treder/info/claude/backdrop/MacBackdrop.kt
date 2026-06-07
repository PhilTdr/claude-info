package dev.treder.info.claude.backdrop

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Applies an NSVisualEffectView vibrancy backdrop to a Compose Desktop window
 * on macOS. Two pieces have to land together:
 *
 *  1. **Topology — replace `contentView`, re-add Compose view as a child.**
 *     Compose's NSView (`AWTView`) is layer-backed by a CAMetalLayer that
 *     paints opaquely from Metal *before* compositing sublayers. So adding
 *     NSVisualEffectView as a *subview* of the AWTView puts the effect
 *     visually on top — that's what gave us the white block earlier. The
 *     working topology, used by IntelliJ / Firefox / lukakerr/NSWindowStyles,
 *     is to make NSVisualEffectView the window's contentView and reparent
 *     the AWTView underneath it. AWT routes events via the NSWindow + the
 *     AWTView pointer (not via view position), so reparenting is safe.
 *
 *  2. **Force Skia's render-pass clear color to be transparent.**
 *     Compose's `Window(transparent = true)` flows through
 *     `WindowSkiaLayerComponent.transparency`, which only sets
 *     `Color(0,0,0,0)` on the SkiaLayer's background when
 *     `windowContext.isWindowTransparent` is true — and on this stack that
 *     gate apparently misfires, leaving SkiaLayer.background = null so AWT
 *     paints opaque JFrame.background instead. Calling SkiaLayer's
 *     `setTransparency(true)` *and* `setBackground(Color(0,0,0,0))` via
 *     reflection on the method (not the field — Kotlin's `var` compiles to a
 *     synthetic backing field; the setter is what triggers
 *     `configureBackground` and pushes the bg into the Metal clear).
 *
 * NSWindow handle resolution: `ComposeWindow.windowHandle: Long` is the public
 * Compose API for the underlying NSWindow*. `Native.getWindowID` is broken on
 * modern JDK/Compose stacks (returns 0), so we use the property directly.
 */
object MacBackdrop {

    // NSVisualEffectMaterial values (10.14+). `.hudWindow` (8) gives a dark,
    // blickdicht-genug HUD look that keeps our light-on-dark theme readable;
    // `.popover` (3) is lighter and washes out dark themes; `.sidebar` (4) and
    // `.underWindowBackground` (9) are other plausible choices.
    private const val MATERIAL_HUD_WINDOW = 8
    private const val BLENDING_BEHIND_WINDOW = 0
    private const val STATE_ACTIVE = 1
    private const val AUTORESIZE_FILL = 2L or 16L  // WidthSizable | HeightSizable

    /** True when `NSGlassEffectView` (macOS 26 Tahoe / WWDC25 "Liquid Glass")
     *  is available at runtime. Class is `nil` on macOS 15 and earlier. */
    val hasLiquidGlass: Boolean by lazy {
        val rt = objc ?: return@lazy false
        Pointer.nativeValue(rt.objc_getClass("NSGlassEffectView")) != 0L
    }

    private const val RETRY_DELAY_MS = 50
    private const val MAX_RETRIES = 40  // ~2s; gives Compose time to attach SkiaLayer + windowHandle

    private val isMac: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().let { it.contains("mac") || it.contains("darwin") }

    private val objc: ObjC? = if (isMac) {
        runCatching { Native.load("objc", ObjC::class.java) }
            .onFailure { log("Native.load(\"objc\") failed: ${it.message}") }
            .getOrNull()
    } else null

    // libdispatch may live under several names depending on macOS version and
    // dyld-shared-cache layout. Try the common candidates in order; first hit wins.
    private val dispatchLibNames = listOf(
        "dispatch",
        "System",
        "/usr/lib/system/libdispatch.dylib",
        "/usr/lib/libSystem.B.dylib",
    )

    private val dispatch: Dispatch? = if (isMac) loadDispatch() else null
    private val mainQueue: Pointer? = if (isMac) loadMainQueue() else null

    init {
        if (isMac) {
            log("init: objc=${objc != null}, dispatch=${dispatch != null}, mainQueue=${
                mainQueue?.let { "0x${Pointer.nativeValue(it).toString(16)}" } ?: "null"
            }")
        }
    }

    private fun loadDispatch(): Dispatch? {
        for (name in dispatchLibNames) {
            val result = runCatching { Native.load(name, Dispatch::class.java) }
            if (result.isSuccess) {
                log("dispatch loaded via \"$name\"")
                return result.getOrNull()
            }
            log("dispatch load \"$name\" failed: ${result.exceptionOrNull()?.message}")
        }
        return null
    }

    private fun loadMainQueue(): Pointer? {
        // First: try calling dispatch_get_main_queue() as a real function — on
        // modern macOS it IS exported (in addition to the macro form).
        for (name in dispatchLibNames) {
            try {
                val lib = NativeLibrary.getInstance(name)
                val fn = runCatching { lib.getFunction("dispatch_get_main_queue") }.getOrNull()
                if (fn != null) {
                    val q = fn.invokePointer(emptyArray())
                    if (q != null && Pointer.nativeValue(q) != 0L) {
                        log("mainQueue via dispatch_get_main_queue() @ \"$name\" = 0x${Pointer.nativeValue(q).toString(16)}")
                        return q
                    }
                }
            } catch (e: Throwable) {
                log("dispatch_get_main_queue() @ \"$name\" failed: ${e.message}")
            }
        }
        // Fall back: look up the global symbol `_dispatch_main_q` directly.
        for (name in dispatchLibNames) {
            for (sym in listOf("_dispatch_main_q", "dispatch_main_q")) {
                try {
                    val lib = NativeLibrary.getInstance(name)
                    val q = lib.getGlobalVariableAddress(sym)
                    if (q != null && Pointer.nativeValue(q) != 0L) {
                        log("mainQueue via global \"$sym\" @ \"$name\" = 0x${Pointer.nativeValue(q).toString(16)}")
                        return q
                    }
                } catch (e: Throwable) {
                    log("global \"$sym\" @ \"$name\" failed: ${e.message}")
                }
            }
        }
        return null
    }

    // Keep a strong reference per active block so JNA's Callback peer isn't GC'd
    // while libdispatch is still holding its function pointer.
    private val callbackAnchor = mutableListOf<DispatchFn>()

    private val installed = mutableSetOf<Long>()    // nsWindow pointer values we've already wrapped
    private val pendingRetry = mutableSetOf<Int>()  // ComposeWindow identityHashCodes with a retry loop in flight

    fun apply(window: ComposeWindow) {
        if (!isMac || objc == null) return
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { apply(window) }
            return
        }
        val key = System.identityHashCode(window)
        if (!pendingRetry.add(key)) return
        tryInstall(window, attempt = 0)
    }

    private fun tryInstall(window: ComposeWindow, attempt: Int) {
        val rt = objc ?: return
        val key = System.identityHashCode(window)

        val nsWindowPtr = window.windowHandle
        if (nsWindowPtr == 0L) {
            if (attempt == 0) log("windowHandle=0 — retrying")
            scheduleRetry(window, attempt, key)
            return
        }
        if (nsWindowPtr in installed) {
            pendingRetry.remove(key)
            return
        }
        if (window.width < 2 || window.height < 2) {
            if (attempt == 0) log("window not sized yet (${window.width}x${window.height}) — retrying")
            scheduleRetry(window, attempt, key)
            return
        }

        pendingRetry.remove(key)
        val nsWindow = Pointer(nsWindowPtr)
        if (attempt > 0) log("ready at attempt=$attempt")
        log("nsWindow=0x${nsWindowPtr.toString(16)} size=${window.width}x${window.height}")

        // 1) Make Skia render with transparent clear color (Swing-side, safe from EDT).
        forceSkiaTransparency(window)

        // 2) Force the JFrame background to transparent — backstop for the
        //    Compose internal gate (`windowContext.isWindowTransparent`).
        window.background = java.awt.Color(0, 0, 0, 0)

        // 3) All AppKit mutations must run on the AppKit main thread. On modern
        //    Corretto/JBR builds the AWT EDT is *not* the AppKit main thread —
        //    direct calls from JNA on the EDT trip `-[AWTView viewDidMoveToWindow]`'s
        //    thread check and crash the JVM (`Cocoa AWT: Not running on AppKit thread 0`).
        runOnAppKitMainThread {
            rt.objc_msgSend(nsWindow, sel("setOpaque:"), 0.toByte())
            val clearColor = rt.objc_msgSend(rt.objc_getClass("NSColor"), sel("clearColor"))
            if (clearColor != null) {
                rt.objc_msgSend(nsWindow, sel("setBackgroundColor:"), clearColor)
            }

            val originalContent = rt.objc_msgSend(nsWindow, sel("contentView"))
            if (originalContent == null) {
                log("original contentView nil — bailing")
                return@runOnAppKitMainThread
            }

            val frame = NSRect.ByValue().apply {
                x = 0.0; y = 0.0
                width = window.width.toDouble()
                height = window.height.toDouble()
            }

            // Prefer Liquid Glass (macOS 26+); fall back to NSVisualEffectView.
            val (effect, isGlass) = tryCreateLiquidGlass(rt)?.let { it to true }
                ?: (createVibrancy(rt) to false)
            if (effect == null) {
                log("backdrop view creation failed — bailing")
                return@runOnAppKitMainThread
            }
            rt.objc_msgSend(effect, sel("setFrame:"), frame)
            rt.objc_msgSend(effect, sel("setAutoresizingMask:"), AUTORESIZE_FILL)

            // Topology differs between the two view types:
            //   - NSGlassEffectView is a *container* with a `contentView` slot
            //     (you assign the wrapped view into that property; it manages
            //     layout of the slotted content itself).
            //   - NSVisualEffectView is a regular NSView; you `addSubview:` the
            //     wrapped view and size it explicitly.
            rt.objc_msgSend(nsWindow, sel("setContentView:"), effect)
            if (isGlass) {
                rt.objc_msgSend(effect, sel("setContentView:"), originalContent)
            } else {
                rt.objc_msgSend(effect, sel("addSubview:"), originalContent)
            }
            rt.objc_msgSend(originalContent, sel("setFrame:"), frame)
            rt.objc_msgSend(originalContent, sel("setAutoresizingMask:"), AUTORESIZE_FILL)
            rt.objc_msgSend(nsWindow, sel("makeFirstResponder:"), originalContent)

            log("installed ${if (isGlass) "NSGlassEffectView (Liquid Glass)" else "NSVisualEffectView (hudWindow fallback)"}")
        }

        installed.add(nsWindowPtr)
    }

    private fun tryCreateLiquidGlass(rt: ObjC): Pointer? {
        if (!hasLiquidGlass) return null
        val cls = rt.objc_getClass("NSGlassEffectView")
        val alloc = rt.objc_msgSend(cls, sel("alloc")) ?: return null
        val glass = rt.objc_msgSend(alloc, sel("init")) ?: return null
        forceDarkAppearance(rt, glass)
        return glass
    }

    private fun forceDarkAppearance(rt: ObjC, view: Pointer) {
        // NSGlassEffectView and NSVisualEffectView both follow the system's
        // Light/Dark setting by default. Our Compose theme is dark-only, so we
        // force a dark appearance on the backdrop view to keep the tint dark
        // even when the user runs macOS in Light Mode.
        val name = nsString(rt, "NSAppearanceNameDarkAqua") ?: return
        val darkAqua = rt.objc_msgSend(rt.objc_getClass("NSAppearance"), sel("appearanceNamed:"), name)
        if (darkAqua != null && Pointer.nativeValue(darkAqua) != 0L) {
            rt.objc_msgSend(view, sel("setAppearance:"), darkAqua)
        }
    }

    private fun createVibrancy(rt: ObjC): Pointer? {
        val cls = rt.objc_getClass("NSVisualEffectView")
        val alloc = rt.objc_msgSend(cls, sel("alloc")) ?: return null
        val effect = rt.objc_msgSend(alloc, sel("init")) ?: return null

        rt.objc_msgSend(effect, sel("setMaterial:"), MATERIAL_HUD_WINDOW.toLong())
        rt.objc_msgSend(effect, sel("setBlendingMode:"), BLENDING_BEHIND_WINDOW.toLong())
        rt.objc_msgSend(effect, sel("setState:"), STATE_ACTIVE.toLong())
        forceDarkAppearance(rt, effect)
        return effect
    }

    private fun runOnAppKitMainThread(work: () -> Unit) {
        val rt = objc ?: return
        val onMain = boolReturn(rt.objc_msgSend(rt.objc_getClass("NSThread"), sel("isMainThread")))
        if (onMain) {
            work()
            return
        }
        val d = dispatch
        val q = mainQueue
        if (d == null || q == null) {
            log("dispatch unavailable — falling back to direct call (may crash)")
            work()
            return
        }
        val fn = object : DispatchFn {
            override fun invoke(context: Pointer?) {
                try {
                    work()
                } catch (t: Throwable) {
                    log("main-thread block threw: ${t::class.simpleName}: ${t.message}")
                }
            }
        }
        // Anchor against JNA Callback GC for the duration of the sync call.
        synchronized(callbackAnchor) { callbackAnchor.add(fn) }
        try {
            d.dispatch_sync_f(q, null, fn)
        } finally {
            synchronized(callbackAnchor) { callbackAnchor.remove(fn) }
        }
    }

    /** objc_msgSend with a BOOL return: the meaningful bits live in the low
     *  byte of the return register; mask the rest before treating as boolean. */
    private fun boolReturn(p: Pointer?): Boolean =
        p != null && (Pointer.nativeValue(p) and 0xFFL) != 0L

    /**
     * Walk the AWT component tree, find Compose's `SkiaLayer` (whose runtime
     * class is an anonymous subclass like `SkiaLayer$1`), and force its
     * transparency on via the *setter method* — not the backing field, which
     * skips `configureBackground()` and leaves the Metal clear color unchanged.
     */
    private fun forceSkiaTransparency(window: ComposeWindow) {
        val queue = ArrayDeque<java.awt.Component>()
        queue.add(window)
        var poked = 0
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            val fqn = c::class.java.name
            if ("SkiaLayer" in fqn || "skiko" in fqn.lowercase()) {
                if (callTransparencySetter(c)) poked++
            }
            if (c is java.awt.Container) c.components.forEach { queue.add(it) }
        }
        log("forceSkiaTransparency: poked $poked SkiaLayer(s)")
    }

    private fun callTransparencySetter(target: Any): Boolean {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val current: Class<*> = cls
            val setter = runCatching {
                current.getDeclaredMethod("setTransparency", java.lang.Boolean.TYPE)
            }.getOrNull()
            if (setter != null) {
                return try {
                    setter.isAccessible = true
                    setter.invoke(target, true)
                    // Also push a transparent AWT background so the Skia bg
                    // chain (see WindowSkiaLayerComponent) resolves to clear.
                    runCatching {
                        val setBg = current.getMethod("setBackground", java.awt.Color::class.java)
                        setBg.invoke(target, java.awt.Color(0, 0, 0, 0))
                    }
                    // Nudge a repaint so the new clear color takes effect now.
                    runCatching {
                        current.getMethod("needRedraw").invoke(target)
                    }
                    log("SkiaLayer (${current.name}) setTransparency(true) called")
                    true
                } catch (e: Exception) {
                    log("setTransparency invoke failed: ${e::class.simpleName}: ${e.message}")
                    false
                }
            }
            cls = current.superclass
        }
        log("${target.javaClass.name}: no setTransparency(boolean) in class chain")
        return false
    }

    private fun scheduleRetry(window: ComposeWindow, attempt: Int, key: Int) {
        if (attempt >= MAX_RETRIES) {
            log("gave up after $attempt retries")
            pendingRetry.remove(key)
            return
        }
        Timer(RETRY_DELAY_MS) { tryInstall(window, attempt + 1) }.apply {
            isRepeats = false
            start()
        }
    }

    private fun sel(name: String): Pointer = objc!!.sel_registerName(name)

    private fun nsString(rt: ObjC, value: String): Pointer? {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val mem = Memory((bytes.size + 1).toLong())
        mem.write(0, bytes, 0, bytes.size)
        mem.setByte(bytes.size.toLong(), 0)
        return rt.objc_msgSend(
            rt.objc_getClass("NSString"),
            sel("stringWithUTF8String:"),
            mem,
        )
    }

    private fun log(msg: String) {
        println("[MacBackdrop] $msg")
    }

    @Structure.FieldOrder("x", "y", "width", "height")
    open class NSRect : Structure() {
        @JvmField var x: Double = 0.0
        @JvmField var y: Double = 0.0
        @JvmField var width: Double = 0.0
        @JvmField var height: Double = 0.0
        class ByValue : NSRect(), Structure.ByValue
    }

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer
        fun sel_registerName(name: String): Pointer
        fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, arg1: Pointer): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, arg1: Byte): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, arg1: Long): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, arg1: NSRect.ByValue): Pointer?
    }

    @Suppress("FunctionName")
    private interface Dispatch : Library {
        fun dispatch_sync_f(queue: Pointer, context: Pointer?, work: DispatchFn)
    }

    interface DispatchFn : Callback {
        @Suppress("unused")
        fun invoke(context: Pointer?)
    }
}
