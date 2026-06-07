package dev.treder.info.claude.data.settings

import dev.treder.info.claude.data.jsonl.JsonlPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClaudeSettingsReader(
    private val settingsPath: Path = JsonlPaths.claudeSettingsFile,
    private val json: Json = lenientJson,
) {

    suspend fun readPreferredModel(): String? = withContext(Dispatchers.IO) {
        if (!Files.exists(settingsPath)) return@withContext null
        runCatching {
            val text = Files.readString(settingsPath)
            val obj: JsonObject = json.parseToJsonElement(text).jsonObject
            obj["model"]?.jsonPrimitive?.contentOrNull
                ?: obj["defaultModel"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    companion object {
        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
