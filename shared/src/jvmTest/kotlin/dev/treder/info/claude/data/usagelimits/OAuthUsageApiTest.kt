package dev.treder.info.claude.data.usagelimits

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class OAuthUsageApiTest {

    private val parserJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun apiReturning(
        status: HttpStatusCode,
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): OAuthUsageApi {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(parserJson) }
            expectSuccess = true
        }
        return OAuthUsageApi(client = client, json = parserJson, zone = TimeZone.UTC)
    }

    @Test
    fun parsesBothWindowsAndIgnoresExtras() = runTest {
        // Mirrors the real /api/oauth/usage body: utilization (0..100) + ISO-8601 resets_at.
        val fiveReset = "2026-06-29T17:10:00.303669+00:00"
        val sevenReset = "2026-06-30T07:00:00+00:00"
        val body = """
            {
              "five_hour": { "utilization": 42.5, "resets_at": "$fiveReset" },
              "seven_day": { "utilization": 14, "resets_at": "$sevenReset" },
              "seven_day_sonnet": { "utilization": 0.0, "resets_at": null },
              "extra_usage": { "is_enabled": false, "currency": "EUR" },
              "limits": [ { "kind": "session", "percent": 42, "resets_at": "$fiveReset" } ]
            }
        """.trimIndent()

        val limits = apiReturning(HttpStatusCode.OK, body).fetch("tok")

        assertEquals(2, limits.size)
        assertEquals("5-Stunden-Limit", limits[0].name)
        assertEquals("Wöchentlich", limits[1].name)
        assertTrue(abs(limits[0].used - 0.425f) < 1e-4f, "five-hour used was ${limits[0].used}")
        assertTrue(abs(limits[1].used - 0.14f) < 1e-4f, "seven-day used was ${limits[1].used}")
        assertEquals(Instant.parse(fiveReset).toLocalDateTime(TimeZone.UTC), limits[0].resetAt)
    }

    @Test
    fun mapsOnlyPresentWindows() = runTest {
        val body = """{ "five_hour": { "utilization": 80, "resets_at": "2026-06-29T17:10:00+00:00" } }"""

        val limits = apiReturning(HttpStatusCode.OK, body).fetch("tok")

        assertEquals(1, limits.size)
        assertEquals("5-Stunden-Limit", limits[0].name)
    }

    @Test
    fun skipsWindowWithNullReset() = runTest {
        // Inactive windows report a utilization but a null reset — they must be dropped, not crash.
        val body = """{ "seven_day": { "utilization": 18, "resets_at": null } }"""

        val limits = apiReturning(HttpStatusCode.OK, body).fetch("tok")

        assertTrue(limits.isEmpty())
    }

    @Test
    fun clampsUsageAboveHundredPercent() = runTest {
        val body = """{ "seven_day": { "utilization": 130, "resets_at": "2026-06-30T07:00:00+00:00" } }"""

        val limits = apiReturning(HttpStatusCode.OK, body).fetch("tok")

        assertEquals(1f, limits[0].used)
    }

    @Test
    fun sendsOAuthHeaders() = runTest {
        var auth: String? = null
        var beta: String? = null
        var version: String? = null
        apiReturning(
            HttpStatusCode.OK,
            """{ "five_hour": { "utilization": 1, "resets_at": "2026-06-29T17:10:00+00:00" } }""",
            onRequest = { req ->
                auth = req.headers["Authorization"]
                beta = req.headers["anthropic-beta"]
                version = req.headers["anthropic-version"]
            },
        ).fetch("my-token")

        assertEquals("Bearer my-token", auth)
        assertEquals("oauth-2025-04-20", beta)
        assertEquals("2023-06-01", version)
    }

    @Test
    fun throwsAuthExceptionOnUnauthorized() = runTest {
        assertFailsWith<UsageAuthException> {
            apiReturning(HttpStatusCode.Unauthorized, "{}").fetch("expired")
        }
    }

    @Test
    fun throwsRateLimitExceptionOnTooManyRequests() = runTest {
        assertFailsWith<UsageRateLimitException> {
            apiReturning(HttpStatusCode.TooManyRequests, """{ "error": { "type": "rate_limit_error" } }""")
                .fetch("tok")
        }
    }

    @Test
    fun emptyBodyYieldsNoLimits() = runTest {
        val limits = apiReturning(HttpStatusCode.OK, "{}").fetch("tok")
        assertTrue(limits.isEmpty())
    }
}
