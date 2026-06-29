package dev.treder.info.claude.data.settings

import dev.treder.info.claude.data.jsonl.JsonlPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The OAuth credentials Claude Code stores locally, with enough to refresh the token. */
data class ClaudeCredentials(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long?,
    val scopes: List<String>,
)

/**
 * Reads — and, after a refresh, writes back — the OAuth credentials Claude Code keeps at
 * ~/.claude/.credentials.json. Writes update only the OAuth token fields and preserve every
 * other key, so a refreshed token stays compatible with the standalone CLI's own login.
 */
class ClaudeCredentialsStore(
    private val credentialsPath: Path = JsonlPaths.claudeCredentialsFile,
    private val json: Json = lenientJson,
) {
    suspend fun read(): ClaudeCredentials? = withContext(Dispatchers.IO) {
        if (!Files.exists(credentialsPath)) return@withContext null
        runCatching {
            val oauth = json.parseToJsonElement(Files.readString(credentialsPath))
                .jsonObject["claudeAiOauth"]?.jsonObject ?: return@runCatching null
            val token = oauth["accessToken"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val scopes = oauth["scopes"]?.let { element ->
                runCatching { element.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
                    ?: element.jsonPrimitive.contentOrNull?.split(' ')?.filter { it.isNotBlank() }
            }.orEmpty()
            ClaudeCredentials(
                accessToken = token,
                refreshToken = oauth["refreshToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                expiresAtEpochMillis = oauth["expiresAt"]?.jsonPrimitive?.longOrNull,
                scopes = scopes,
            )
        }.getOrNull()
    }

    /**
     * Best-effort: rewrite only accessToken/refreshToken/expiresAt, preserving everything else.
     * Written via a temp file + atomic move so a concurrent reader never sees a half-written file.
     */
    suspend fun writeRefreshed(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochMillis: Long,
    ): Unit = withContext(Dispatchers.IO) {
        runCatching {
            if (!Files.exists(credentialsPath)) return@runCatching
            val root = json.parseToJsonElement(Files.readString(credentialsPath)).jsonObject
            val oauth = root["claudeAiOauth"]?.jsonObject ?: return@runCatching
            val newOauth = JsonObject(
                oauth.toMutableMap().apply {
                    put("accessToken", JsonPrimitive(accessToken))
                    put("refreshToken", JsonPrimitive(refreshToken))
                    put("expiresAt", JsonPrimitive(expiresAtEpochMillis))
                },
            )
            val newRoot = JsonObject(root.toMutableMap().apply { put("claudeAiOauth", newOauth) })
            val tmp = credentialsPath.resolveSibling("${credentialsPath.fileName}.tmp")
            Files.writeString(tmp, json.encodeToString(JsonObject.serializer(), newRoot))
            runCatching {
                Files.move(tmp, credentialsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.recoverCatching {
                Files.move(tmp, credentialsPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
