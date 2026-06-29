package dev.treder.info.claude.data.usagelimits

import dev.treder.info.claude.domain.model.UsageLimit
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fetches the rolling usage limits from Claude's OAuth usage endpoint — the same
 * call Claude Code itself makes for its usage display. The access token must be a
 * Claude.ai OAuth token; the endpoint rejects plain API keys.
 */
class OAuthUsageApi(
    private val client: HttpClient = defaultClient(),
    private val url: String = USAGE_URL,
    private val json: Json = parserJson,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : UsageLimitsApi {

    override suspend fun fetch(accessToken: String): List<UsageLimit> {
        val response: HttpResponse = try {
            client.get(url) {
                header("Authorization", "Bearer $accessToken")
                header("anthropic-beta", "oauth-2025-04-20")
                header("anthropic-version", "2023-06-01")
                header("Content-Type", "application/json")
            }
        } catch (e: ClientRequestException) {
            // Map auth vs. throttling so the repository can react differently (token rejected
            // -> warning; rate limited -> keep last values and back off).
            throw when (e.response.status.value) {
                401, 403 -> UsageAuthException("usage endpoint rejected the token (HTTP ${e.response.status.value})")
                429 -> UsageRateLimitException("usage endpoint rate limited (HTTP 429)")
                else -> e
            }
        }
        val dto: OAuthUsageResponse = json.decodeFromString(response.bodyAsText())
        // Only the two windows we surface; seven_day_opus/seven_day_sonnet/overage are
        // ignored via ignoreUnknownKeys.
        return buildList {
            dto.fiveHour?.toLimit("5-Stunden-Limit", zone)?.let(::add)
            dto.sevenDay?.toLimit("Wöchentlich", zone)?.let(::add)
        }
    }

    @Serializable
    internal data class OAuthUsageResponse(
        @SerialName("five_hour") val fiveHour: Window? = null,
        @SerialName("seven_day") val sevenDay: Window? = null,
    )

    @Serializable
    internal data class Window(
        // Percentage of the window consumed, 0..100 (mirrors `percent` in the `limits` array).
        @SerialName("utilization") val utilization: Double? = null,
        // ISO-8601 timestamp, e.g. "2026-06-29T17:10:00.303669+00:00"; null for inactive windows.
        @SerialName("resets_at") val resetsAt: String? = null,
    ) {
        fun toLimit(name: String, zone: TimeZone): UsageLimit? {
            val util = utilization ?: return null
            val reset = resetsAt ?: return null
            val resetInstant = runCatching { Instant.parse(reset) }.getOrNull() ?: return null
            return UsageLimit(
                name = name,
                used = (util / 100.0).toFloat().coerceIn(0f, 1f),
                resetAt = resetInstant.toLocalDateTime(zone),
            )
        }
    }

    companion object {
        const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"

        private val parserJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(parserJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
            }
            // 401/4xx/5xx -> exception -> repository flips to Unavailable and hides the bars.
            expectSuccess = true
        }
    }
}
