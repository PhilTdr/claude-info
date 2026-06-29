package dev.treder.info.claude.data.auth

import dev.treder.info.claude.data.settings.ClaudeCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ClaudeAccessTokenProviderTest {

    private val nowMs = 2_000_000_000_000L
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    /** Counts refresh calls; produces a token or throws via [outcome]. */
    private class FakeRefresher(private val outcome: () -> RefreshedToken) : TokenRefresher {
        var calls = 0
            private set

        override suspend fun refresh(refreshToken: String, scopes: List<String>): RefreshedToken {
            calls++
            return outcome()
        }
    }

    private fun creds(expiresAtMillis: Long?, refreshToken: String? = "rt") =
        ClaudeCredentials("access-old", refreshToken, expiresAtMillis, listOf("user:inference"))

    @Test
    fun validTokenReturnedWithoutRefresh() = runTest {
        val refresher = FakeRefresher { error("must not refresh") }
        var persisted = false
        val provider = ClaudeAccessTokenProvider(
            readCredentials = { creds(expiresAtMillis = nowMs + 600_000) }, // 10 min ahead
            refresher = refresher,
            persist = { _, _, _ -> persisted = true },
            clock = fixedClock,
        )

        assertEquals("access-old", provider.token())
        assertEquals(0, refresher.calls)
        assertEquals(false, persisted)
    }

    @Test
    fun expiredTokenRefreshesAndPersists() = runTest {
        val refresher = FakeRefresher { RefreshedToken("access-new", "rt-new", expiresInSeconds = 3600) }
        var pAccess: String? = null
        var pRefresh: String? = null
        var pExpires: Long? = null
        val provider = ClaudeAccessTokenProvider(
            readCredentials = { creds(expiresAtMillis = nowMs) }, // already at expiry
            refresher = refresher,
            persist = { a, r, e -> pAccess = a; pRefresh = r; pExpires = e },
            clock = fixedClock,
        )

        assertEquals("access-new", provider.token())
        assertEquals(1, refresher.calls)
        assertEquals("access-new", pAccess)
        assertEquals("rt-new", pRefresh)
        assertEquals(nowMs + 3600_000, pExpires)
    }

    @Test
    fun refreshFailureReturnsNullAndBacksOff() = runTest {
        val refresher = FakeRefresher { throw RuntimeException("429 rate limited") }
        val provider = ClaudeAccessTokenProvider(
            readCredentials = { creds(expiresAtMillis = nowMs) },
            refresher = refresher,
            persist = { _, _, _ -> },
            clock = fixedClock,
        )

        assertNull(provider.token())
        assertEquals(1, refresher.calls)

        // Immediate retry must be suppressed by the backoff window.
        assertNull(provider.token())
        assertEquals(1, refresher.calls)
    }

    @Test
    fun expiredWithoutRefreshTokenReturnsNull() = runTest {
        val refresher = FakeRefresher { error("must not refresh") }
        val provider = ClaudeAccessTokenProvider(
            readCredentials = { creds(expiresAtMillis = nowMs, refreshToken = null) },
            refresher = refresher,
            persist = { _, _, _ -> },
            clock = fixedClock,
        )

        assertNull(provider.token())
        assertEquals(0, refresher.calls)
    }

    @Test
    fun missingCredentialsReturnsNull() = runTest {
        val refresher = FakeRefresher { error("must not refresh") }
        val provider = ClaudeAccessTokenProvider(
            readCredentials = { null },
            refresher = refresher,
            persist = { _, _, _ -> },
            clock = fixedClock,
        )

        assertNull(provider.token())
        assertEquals(0, refresher.calls)
    }
}
