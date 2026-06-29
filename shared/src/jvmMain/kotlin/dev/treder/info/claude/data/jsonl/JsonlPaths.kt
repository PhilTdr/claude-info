package dev.treder.info.claude.data.jsonl

import java.nio.file.Path
import java.nio.file.Paths

object JsonlPaths {
    val claudeProjectsDir: Path
        get() = Paths.get(System.getProperty("user.home"), ".claude", "projects")

    val claudeSettingsFile: Path
        get() = Paths.get(System.getProperty("user.home"), ".claude", "settings.json")

    val claudeCredentialsFile: Path
        get() = Paths.get(System.getProperty("user.home"), ".claude", ".credentials.json")
}
