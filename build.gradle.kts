plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

// Single source of truth for the app version: This returns the most recent reachable tag.
val gitTag: String = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val resolvedAppVersion: String = gitTag
    .ifBlank { "1.0.0" }
    .removePrefix("v").removePrefix("V")

extra["appVersion"] = resolvedAppVersion