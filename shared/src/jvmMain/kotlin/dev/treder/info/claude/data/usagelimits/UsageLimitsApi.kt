package dev.treder.info.claude.data.usagelimits

import dev.treder.info.claude.domain.model.UsageLimit

interface UsageLimitsApi {
    /**
     * Fetches the current usage limits. Throws [UsageAuthException] when the token is rejected,
     * [UsageRateLimitException] when throttled, or another exception on other I/O failures.
     */
    suspend fun fetch(accessToken: String): List<UsageLimit>
}

/** The usage endpoint rejected the token (401/403) — the user must re-authenticate. */
class UsageAuthException(message: String) : Exception(message)

/** The usage endpoint is rate-limiting us (429) — transient; back off and keep the last values. */
class UsageRateLimitException(message: String) : Exception(message)
