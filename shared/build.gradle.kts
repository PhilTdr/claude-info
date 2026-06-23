plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

// The app version, resolved from the git tag in the root build (see build.gradle.kts).
val appVersion: String = rootProject.extra["appVersion"] as String

// Generates BuildConfig.APP_VERSION from the git-derived app version so the running app
// knows its own version for the update check, without keeping a second copy in source.
val generateBuildConfig by tasks.registering {
    val version = appVersion
    val outputDir = layout.buildDirectory.dir("generated/buildConfig/kotlin")
    inputs.property("appVersion", version)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("dev/treder/info/claude")
        packageDir.mkdirs()
        packageDir.resolve("BuildConfig.kt").writeText(
            """
                package dev.treder.info.claude

                /** Build-time constants generated from the git-derived app version. */
                object BuildConfig {
                    const val APP_VERSION: String = "$version"
                }

            """.trimIndent(),
        )
    }
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain {
            // Pick up the generated BuildConfig (see generateBuildConfig above).
            kotlin.srcDir(generateBuildConfig)
            dependencies {
                implementation(libs.ktor.client.cio)
                // The Kotlin Multiplatform / Gradle 9.1 combination does not always
                // surface common dependencies on the JVM runtime classpath; pin the
                // JVM artifacts here so they are guaranteed to be present at runtime.
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
