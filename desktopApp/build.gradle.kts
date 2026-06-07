import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val jsignClasspath: Configuration by configurations.creating

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.components.resources)
    implementation(libs.compose.uiToolingPreview)

    implementation(libs.jna.platform)

    jsignClasspath("net.jsign:jsign:6.0")
}

compose.resources {
    packageOfResClass = "dev.treder.info.claude.resources"
}

compose.desktop {
    application {
        mainClass = "dev.treder.info.claude.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ClaudeInfo"
            packageVersion = "1.0.0"
            description = "Claude Nutzungsstatistiken im System Tray"

            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
                bundleID = "dev.treder.cursorinfo"
                dockName = "CursorInfo"
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                upgradeUuid = "a8e46c9b-3f14-4d28-b6e5-7c2d1a9f0e5b"
                menu = true
                shortcut = true
                dirChooser = false
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                appCategory = "Utility"
                shortcut = true
            }
        }
    }
}

val macSignIdentity = "ClaudeInfo Internal Build"

val signMacApp by tasks.registering(Exec::class) {
    val identity = macSignIdentity
    group = "distribution"
    description = "Signiert die .app mit dem internen Self-Signed Cert."
    dependsOn("createDistributable")
    onlyIf {
        if (!OperatingSystem.current().isMacOsX) return@onlyIf false
        val proc = ProcessBuilder("/usr/bin/security", "find-identity", "-v", "-p", "codesigning")
            .redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        identity in output
    }
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/ClaudeInfo.app")
    inputs.dir(appDir)
    commandLine(
        "codesign",
        "--force", "--deep",
        "--options", "runtime",
        "--timestamp",
        "--sign", identity,
        appDir.get().asFile.absolutePath
    )
}

tasks.matching { it.name == "packageDmg" }.configureEach {
    dependsOn(signMacApp)
}

abstract class SignMsiTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val msiDir: DirectoryProperty

    @get:Classpath
    abstract val jsignClasspath: ConfigurableFileCollection

    @TaskAction
    fun sign() {
        val pfxPath = System.getenv("WIN_SIGN_PFX_PATH")
            ?: error("WIN_SIGN_PFX_PATH not set")
        val pfxPassword = System.getenv("WIN_SIGN_PFX_PASSWORD")
            ?: error("WIN_SIGN_PFX_PASSWORD not set")

        val msis = msiDir.get().asFile.listFiles { f -> f.extension.equals("msi", true) }
            ?: error("Keine MSI gefunden in ${msiDir.get().asFile}")
        require(msis.isNotEmpty()) { "packageMsi hat keine MSI erzeugt." }

        msis.forEach { msi ->
            logger.lifecycle("Signing ${msi.name}")
            execOps.javaexec {
                classpath = jsignClasspath
                mainClass.set("net.jsign.JsignCLI")
                args(
                    "--keystore", pfxPath,
                    "--storepass", pfxPassword,
                    "--storetype", "PKCS12",
                    "--alg", "SHA-256",
                    "--tsaurl", "http://timestamp.digicert.com",
                    "--name", "ClaudeInfo (Internal Build)",
                    "--url", "https://github.com/PhilTdr/claude-info",
                    msi.absolutePath
                )
            }
        }
    }
}

val signMsi by tasks.registering(SignMsiTask::class) {
    group = "distribution"
    description = "Signiert die MSI mit jsign (Self-Signed)."
    dependsOn("packageMsi")
    onlyIf {
        System.getenv("WIN_SIGN_PFX_PATH") != null &&
        System.getenv("WIN_SIGN_PFX_PASSWORD") != null
    }
    msiDir.set(layout.buildDirectory.dir("compose/binaries/main/msi"))
    jsignClasspath.from(configurations["jsignClasspath"])
}