package dev.treder.info.claude

import java.awt.Desktop
import java.net.URI

/**
 * Opens an HTTP(S) URL in the user's default browser. Tries AWT's [Desktop]
 * first and falls back to the platform's "open" command where Desktop is
 * unavailable (notably many Linux desktops). Runs off the calling (UI) thread
 * because launching the browser can briefly block.
 */
object UrlOpener {

    fun open(url: String) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        if (uri.scheme?.startsWith("http") != true) return

        Thread {
            if (!browseViaDesktop(uri)) browseViaCommand(url)
        }.apply {
            isDaemon = true
            name = "url-opener"
        }.start()
    }

    private fun browseViaDesktop(uri: URI): Boolean = runCatching {
        if (Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            Desktop.getDesktop().browse(uri)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    private fun browseViaCommand(url: String) {
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            os.contains("mac") -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
        runCatching { ProcessBuilder(command).start() }
    }
}
