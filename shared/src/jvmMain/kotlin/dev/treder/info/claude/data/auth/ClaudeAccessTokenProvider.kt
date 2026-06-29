package dev.treder.info.claude.data.auth

import dev.treder.info.claude.data.settings.ClaudeCredentials
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException

/**
 * Supplies a valid OAuth access token for the usage endpoint.
 *
 * Returns the stored token while it is still valid; once it is within [expirySkewMillis] of
 * expiry it refreshes via [refresher] and persists the result through [persist]. A failed
 * refresh (e.g. HTTP 429) backs off for [minRefreshRetryMillis] so the rate-limited endpoint
 * is not hit on every 60s poll. Returns null when no usable token can be obtained.
 */
class ClaudeAccessTokenProvider(
    private val readCredentials: suspend () -> ClaudeCredentials?,
    private val refresher: TokenRefresher,
    private val persist: suspend (accessToken: String, refreshToken: String, expiresAtEpochMillis: Long) -> Unit,
    private val clock: Clock = Clock.System,
    private val expirySkewMillis: Long = 60_000,
    private val minRefreshRetryMillis: Long = 5 * 60_000,
) {
    private var lastFailedRefreshAtMillis: Long? = null

    suspend fun token(): String? {
        val creds = readCredentials() ?: return null
        if (creds.accessToken.isNotBlank() && !isExpiringSoon(creds.expiresAtEpochMillis)) {
            return creds.accessToken
        }
        val refreshToken = creds.refreshToken ?: return null
        val now = clock.now().toEpochMilliseconds()
        val lastFailure = lastFailedRefreshAtMillis
        if (lastFailure != null && now - lastFailure < minRefreshRetryMillis) return null

        val refreshed = try {
            refresher.refresh(refreshToken, creds.scopes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            lastFailedRefreshAtMillis = now
            return null
        }

        val newRefreshToken = refreshed.refreshToken ?: refreshToken
        val expiresAt = now + refreshed.expiresInSeconds * 1000
        persist(refreshed.accessToken, newRefreshToken, expiresAt)
        return refreshed.accessToken
    }

    private fun isExpiringSoon(expiresAtMillis: Long?): Boolean =
        expiresAtMillis != null && expiresAtMillis <= clock.now().toEpochMilliseconds() + expirySkewMillis
}
