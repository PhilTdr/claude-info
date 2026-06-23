package dev.treder.info.claude.autostart

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * Registers/unregisters the app to launch automatically at login. Implemented per OS
 * with the platform-native mechanism, so it survives without any extra daemon:
 * Windows uses the `…\CurrentVersion\Run` registry value, macOS a LaunchAgent plist,
 * Linux a `.desktop` file in the autostart directory.
 *
 * State is derived directly from the platform (registry value / file presence), so the
 * menu always reflects the real OS state — even if the user toggled it elsewhere.
 */
interface Autostart {

    /** Localized menu label, e.g. "Mit Windows starten". */
    val menuLabel: String

    /** Whether the app is currently registered to start at login. */
    fun isEnabled(): Boolean

    /** Registers ([enable] = true) or removes the autostart entry. @return the resulting state. */
    fun setEnabled(enable: Boolean): Boolean

    companion object {
        private const val APP_NAME = "ClaudeInfo"
        // Matches the macOS bundleID in build.gradle.kts so the LaunchAgent is unambiguous.
        private const val BUNDLE_ID = "dev.treder.cursorinfo"

        /** The launcher executable of the running app, or null if it can't be resolved. */
        private fun currentExecutable(): String? =
            ProcessHandle.current().info().command().orElse(null)

        /** The autostart handler for the current OS, or null on an unsupported platform. */
        fun forCurrentOs(): Autostart? {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.contains("win") -> WindowsAutostart(APP_NAME)
                os.contains("mac") || os.contains("darwin") -> MacAutostart(BUNDLE_ID)
                os.contains("nux") || os.contains("nix") -> LinuxAutostart(APP_NAME)
                else -> null
            }
        }

        // Visible to the implementations below.
        internal fun executable() = currentExecutable()
    }
}

/** Windows: a string value under HKCU\…\CurrentVersion\Run (the Run key always exists). */
private class WindowsAutostart(private val appName: String) : Autostart {

    private val runKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    override val menuLabel = "Mit Windows starten"

    override fun isEnabled(): Boolean = runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, runKey, appName)
    }.getOrDefault(false)

    override fun setEnabled(enable: Boolean): Boolean {
        runCatching {
            if (enable) {
                val exe = Autostart.executable() ?: return isEnabled()
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, runKey, appName, "\"$exe\"")
            } else if (isEnabled()) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, runKey, appName)
            }
        }
        return isEnabled()
    }
}

/** macOS: a LaunchAgent plist with RunAtLoad. launchd loads it on the next login. */
private class MacAutostart(private val label: String) : Autostart {

    private val plistFile =
        File(System.getProperty("user.home"), "Library/LaunchAgents/$label.plist")

    override val menuLabel = "Mit macOS starten"

    override fun isEnabled(): Boolean = plistFile.isFile

    override fun setEnabled(enable: Boolean): Boolean {
        runCatching {
            if (enable) {
                val exe = Autostart.executable() ?: return isEnabled()
                plistFile.parentFile?.mkdirs()
                plistFile.writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>Label</key>
                        <string>$label</string>
                        <key>ProgramArguments</key>
                        <array>
                            <string>$exe</string>
                        </array>
                        <key>RunAtLoad</key>
                        <true/>
                    </dict>
                    </plist>

                    """.trimIndent(),
                )
            } else {
                plistFile.delete()
            }
        }
        return isEnabled()
    }
}

/** Linux: a freedesktop.org autostart entry in ~/.config/autostart. */
private class LinuxAutostart(private val appName: String) : Autostart {

    private val configHome: File =
        System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".config")

    private val desktopFile = File(configHome, "autostart/$appName.desktop")

    override val menuLabel = "Mit Linux starten"

    override fun isEnabled(): Boolean = desktopFile.isFile

    override fun setEnabled(enable: Boolean): Boolean {
        runCatching {
            if (enable) {
                val exe = Autostart.executable() ?: return isEnabled()
                desktopFile.parentFile?.mkdirs()
                desktopFile.writeText(
                    """
                    [Desktop Entry]
                    Type=Application
                    Name=$appName
                    Exec="$exe"
                    Terminal=false
                    X-GNOME-Autostart-enabled=true

                    """.trimIndent(),
                )
            } else {
                desktopFile.delete()
            }
        }
        return isEnabled()
    }
}
