package dev.treder.info.claude.data.update

/** The newest published release as reported by the remote release feed. */
data class LatestRelease(
    val version: String,
    val url: String,
)

/**
 * Fetches the latest release metadata from a remote feed. Abstracted behind an
 * interface so the repository can be exercised without real network I/O.
 */
interface UpdateApi {

    /**
     * The newest published release, or `null` when the feed carries no usable
     * release. May throw on I/O or HTTP failure; callers handle that.
     */
    suspend fun fetchLatestRelease(): LatestRelease?
}
