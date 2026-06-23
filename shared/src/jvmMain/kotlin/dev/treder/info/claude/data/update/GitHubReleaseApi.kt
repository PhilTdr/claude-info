package dev.treder.info.claude.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the latest published release of the project from the GitHub REST API,
 * the same approach many open-source desktop apps use for update checks. The
 * `/releases/latest` endpoint already excludes drafts and pre-releases, so a
 * returned release is always a real, user-facing version.
 *
 * GitHub rejects requests without a `User-Agent`; unauthenticated calls are
 * rate-limited to 60/hour per IP, far above the four-hourly check cadence.
 */
class GitHubReleaseApi(
    private val client: HttpClient = defaultClient(),
    private val url: String = LATEST_RELEASE_URL,
    private val json: Json = parserJson,
) : UpdateApi {

    override suspend fun fetchLatestRelease(): LatestRelease? {
        val response: HttpResponse = client.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        val release: GitHubReleaseEntry = json.decodeFromString(response.bodyAsText())
        val version = release.tagName?.trim().orEmpty()
        val link = release.htmlUrl?.trim().orEmpty()
        // A release without a tag or page is unusable; treat it as "nothing published".
        if (version.isEmpty() || link.isEmpty()) return null
        return LatestRelease(version = version, url = link)
    }

    @Serializable
    internal data class GitHubReleaseEntry(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
    )

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/PhilTdr/claude-info/releases/latest"

        private const val USER_AGENT = "claude-info-update-check"

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
