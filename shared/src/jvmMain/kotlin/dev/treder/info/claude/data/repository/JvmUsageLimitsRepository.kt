package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.usagelimits.OAuthUsageApi
import dev.treder.info.claude.data.usagelimits.UsageAuthException
import dev.treder.info.claude.data.usagelimits.UsageLimitsApi
import dev.treder.info.claude.data.usagelimits.UsageRateLimitException
import dev.treder.info.claude.domain.model.UsageLimit
import dev.treder.info.claude.domain.model.UsageLimitsStatus
import dev.treder.info.claude.domain.repository.UsageLimitsRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls the OAuth usage endpoint for the lifetime of [scope].
 *
 * [accessTokenProvider] yields a ready-to-use access token (refreshing it if needed) or null.
 * The endpoint is rate-limited, so [refreshInterval] is deliberately gentle and transient
 * failures back off exponentially up to [maxBackoff].
 *
 * Status handling:
 * - Only a genuine auth failure (no token, or HTTP 401/403) flips [status] to
 *   [UsageLimitsStatus.Unavailable] and clears the bars — that is the case the warning hint addresses.
 * - A 429 / network / decode failure is treated as transient: the last good values stay on screen
 *   and we simply back off, so a throttled endpoint never flashes the (auth-worded) warning.
 */
class JvmUsageLimitsRepository(
    private val api: UsageLimitsApi = OAuthUsageApi(),
    private val accessTokenProvider: suspend () -> String?,
    scope: CoroutineScope,
    private val refreshInterval: Duration = 10.minutes,
    private val maxBackoff: Duration = 30.minutes,
) : UsageLimitsRepository {

    private val _limits = MutableStateFlow<List<UsageLimit>>(emptyList())
    private val _status = MutableStateFlow(UsageLimitsStatus.Loading)
    override val status: StateFlow<UsageLimitsStatus> = _status.asStateFlow()

    init {
        scope.launch {
            var backoff = refreshInterval
            while (isActive) {
                val transient = loadOnce()
                backoff = if (transient) (backoff * 2).coerceAtMost(maxBackoff) else refreshInterval
                delay(backoff)
            }
        }
    }

    // StateFlow already conflates equal values, so no distinctUntilChanged needed.
    override fun getUsageLimits(): Flow<List<UsageLimit>> = _limits.asStateFlow()

    /** Returns true when the failure was transient (caller should back off before retrying). */
    private suspend fun loadOnce(): Boolean {
        val token = try {
            accessTokenProvider()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        if (token == null) {
            setUnavailable()
            return false
        }
        return try {
            _limits.value = api.fetch(token)
            _status.value = UsageLimitsStatus.Available
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: UsageAuthException) {
            // Token rejected — show the warning so the user can re-authenticate.
            setUnavailable()
            false
        } catch (_: UsageRateLimitException) {
            // Throttled — keep the last values, surface a rate-limit warning, and back off.
            _status.value = UsageLimitsStatus.RateLimited
            true
        } catch (_: Throwable) {
            // Network / decode / 5xx — keep the last values and back off (no warning).
            true
        }
    }

    private fun setUnavailable() {
        _limits.value = emptyList()
        _status.value = UsageLimitsStatus.Unavailable
    }
}
