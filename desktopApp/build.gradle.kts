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

val macSignIdentity = "treder.dev Apps"

val signMacApp by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Signiert die .app mit dem internen Self-Signed Cert."
    dependsOn("createDistributable")

    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/ClaudeInfo.app")
    inputs.dir(appDir)

    // Signieren wird über MAC_SIGN_IDENTITY gesteuert (in CI gesetzt, sobald der
    // Import des Zertifikats geklappt hat). Kein find-identity-Probe mehr: dieser
    // war strenger als codesign selbst und hat den Task fälschlich übersprungen.
    // Env config-cache-sicher über providers lesen, commandLine zur Konfigurations-
    // zeit bauen (kein doFirst -> keine Script-Referenz im serialisierten Task).
    val identity = providers.environmentVariable("MAC_SIGN_IDENTITY")
    val keychain = providers.environmentVariable("MAC_KEYCHAIN")

    onlyIf {
        OperatingSystem.current().isMacOsX && identity.isPresent
    }

    commandLine(
        buildList {
            add("codesign")
            add("--force"); add("--deep")
            add("--options"); add("runtime")
            add("--timestamp")
            // codesign direkt auf den importierten Keychain zeigen lassen,
            // statt von der Keychain-Suchliste/Trust-Policy abzuhängen.
            keychain.orNull?.let { add("--keychain"); add(it) }
            add("--sign"); add(identity.getOrElse(macSignIdentity))
            add(appDir.get().asFile.absolutePath)
        }
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
                    "--name", "treder.dev Apps",
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