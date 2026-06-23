package dev.treder.info.claude.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class GitHubReleaseApiTest {

    private fun apiReturning(status: HttpStatusCode, body: String): GitHubReleaseApi {
        val parserJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val engine = MockEngine {
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
        return GitHubReleaseApi(client = client, json = parserJson)
    }

    @Test
    fun parsesTagAndHtmlUrlFromRealisticPayload() = runTest {
        // Trimmed but realistic /releases/latest response — unknown fields must be ignored.
        val body = """
            {
              "tag_name": "v1.4.0",
              "name": "1.4.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/PhilTdr/claude-info/releases/tag/v1.4.0",
              "assets": [{ "name": "ClaudeInfo-1.4.0.msi" }]
            }
        """.trimIndent()

        val release = apiReturning(HttpStatusCode.OK, body).fetchLatestRelease()

        assertEquals("v1.4.0", release?.version)
        assertEquals("https://github.com/PhilTdr/claude-info/releases/tag/v1.4.0", release?.url)
    }

    @Test
    fun returnsNullWhenTagOrUrlMissing() = runTest {
        val release = apiReturning(HttpStatusCode.OK, "{}").fetchLatestRelease()
        assertNull(release)
    }

    @Test
    fun throwsOnNonSuccessStatus() = runTest {
        // /releases/latest returns 404 when no release is published yet; with
        // expectSuccess the call must throw so the repository keeps NoUpdate.
        assertFailsWith<Exception> {
            apiReturning(HttpStatusCode.NotFound, "{}").fetchLatestRelease()
        }
    }

    @Test
    fun sendsRequiredGitHubHeaders() = runTest {
        // GitHub rejects requests without a User-Agent (403); the Accept header
        // pins the API version. A regression dropping either would silently break
        // the update check in production, so assert the exact contract here.
        var userAgent: String? = null
        var accept: String? = null
        val parserJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val engine = MockEngine { request ->
            userAgent = request.headers[HttpHeaders.UserAgent]
            accept = request.headers[HttpHeaders.Accept]
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(parserJson) }
            expectSuccess = true
        }

        GitHubReleaseApi(client = client, json = parserJson).fetchLatestRelease()

        assertEquals("claude-info-update-check", userAgent)
        assertEquals("application/vnd.github+json", accept)
    }
}
