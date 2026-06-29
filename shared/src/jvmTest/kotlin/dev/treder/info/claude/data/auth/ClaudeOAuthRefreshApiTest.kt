package dev.treder.info.claude.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class ClaudeOAuthRefreshApiTest {

    private val parserJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun apiReturning(
        status: HttpStatusCode,
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): ClaudeOAuthRefreshApi {
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
        return ClaudeOAuthRefreshApi(client = client, json = parserJson)
    }

    @Test
    fun parsesTokenResponse() = runTest {
        val body = """{ "access_token": "acc-1", "refresh_token": "ref-2", "expires_in": 3600, "token_type": "Bearer" }"""

        val refreshed = apiReturning(HttpStatusCode.OK, body).refresh("old-rt", listOf("user:inference"))

        assertEquals("acc-1", refreshed.accessToken)
        assertEquals("ref-2", refreshed.refreshToken)
        assertEquals(3600L, refreshed.expiresInSeconds)
    }

    @Test
    fun sendsRefreshGrantWithClientId() = runTest {
        var bodyText: String? = null
        apiReturning(
            HttpStatusCode.OK,
            """{ "access_token": "a", "expires_in": 3600 }""",
            onRequest = { req -> bodyText = (req.body as? TextContent)?.text },
        ).refresh("my-refresh-token", listOf("user:inference"))

        val sent = bodyText ?: ""
        assertTrue(sent.contains("\"grant_type\":\"refresh_token\""), "body was: $sent")
        assertTrue(sent.contains("\"refresh_token\":\"my-refresh-token\""), "body was: $sent")
        assertTrue(sent.contains("9d1c250a-e61b-44d9-88ed-5944d1962f5e"), "body was: $sent")
    }

    @Test
    fun throwsOnRateLimit() = runTest {
        assertFailsWith<Exception> {
            apiReturning(HttpStatusCode.TooManyRequests, """{ "error": { "type": "rate_limit_error" } }""")
                .refresh("rt", emptyList())
        }
    }
}
