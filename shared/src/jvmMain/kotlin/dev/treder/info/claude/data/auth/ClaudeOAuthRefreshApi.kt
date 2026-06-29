package dev.treder.info.claude.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Refreshes the Claude Code OAuth token via the same endpoint and client id the CLI itself
 * uses (verified from the CLI bundle). The endpoint is rate-limited (HTTP 429), so callers
 * must back off on failure rather than retry every poll.
 */
class ClaudeOAuthRefreshApi(
    private val client: HttpClient = defaultClient(),
    private val url: String = TOKEN_URL,
    private val clientId: String = CLIENT_ID,
    private val json: Json = parserJson,
) : TokenRefresher {

    override suspend fun refresh(refreshToken: String, scopes: List<String>): RefreshedToken {
        val body: JsonObject = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
            put("client_id", clientId)
            if (scopes.isNotEmpty()) put("scope", scopes.joinToString(" "))
        }
        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val dto: TokenResponse = json.decodeFromString(response.bodyAsText())
        val access = dto.accessToken ?: error("token response missing access_token")
        return RefreshedToken(
            accessToken = access,
            refreshToken = dto.refreshToken,
            expiresInSeconds = dto.expiresIn ?: 0L,
        )
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
    )

    companion object {
        const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

        private val parserJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(parserJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = true
        }
    }
}
