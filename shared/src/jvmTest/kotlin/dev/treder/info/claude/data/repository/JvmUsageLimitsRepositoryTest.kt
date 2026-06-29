package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.usagelimits.UsageAuthException
import dev.treder.info.claude.data.usagelimits.UsageLimitsApi
import dev.treder.info.claude.data.usagelimits.UsageRateLimitException
import dev.treder.info.claude.domain.model.UsageLimit
import dev.treder.info.claude.domain.model.UsageLimitsStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class JvmUsageLimitsRepositoryTest {

    private val sampleLimit = UsageLimit("5-Stunden-Limit", 0.3f, LocalDateTime(2026, 1, 1, 12, 0))

    /** Queues fetch outcomes; each call pops the next producer (returning a list or throwing). */
    private class FakeApi(vararg outcomes: () -> List<UsageLimit>) : UsageLimitsApi {
        private val queue = ArrayDeque(outcomes.toList())
        var lastToken: String? = null
            private set
        var callCount = 0
            private set

        override suspend fun fetch(accessToken: String): List<UsageLimit> {
            callCount++
            lastToken = accessToken
            val producer = queue.removeFirstOrNull() ?: error("no fetch outcome queued")
            return producer()
        }
    }

    private fun repo(api: FakeApi, token: String? = "token-123", scope: kotlinx.coroutines.CoroutineScope) =
        JvmUsageLimitsRepository(
            api = api,
            accessTokenProvider = { token },
            scope = scope,
            refreshInterval = 60.seconds,
        )

    @Test
    fun firstSuccessPopulatesAndBecomesAvailable() = runTest {
        val api = FakeApi({ listOf(sampleLimit) })
        val repo = repo(api, scope = backgroundScope)
        runCurrent()

        assertEquals(listOf(sampleLimit), repo.getUsageLimits().first())
        assertEquals(UsageLimitsStatus.Available, repo.status.value)
        assertEquals("token-123", api.lastToken)
    }

    @Test
    fun nullTokenBecomesUnavailableWithoutCallingApi() = runTest {
        val api = FakeApi({ listOf(sampleLimit) })
        val repo = repo(api, token = null, scope = backgroundScope)
        runCurrent()

        assertEquals(UsageLimitsStatus.Unavailable, repo.status.value)
        assertEquals(0, api.callCount)
    }

    @Test
    fun authFailureClearsBarsAndShowsUnavailable() = runTest {
        val api = FakeApi({ throw UsageAuthException("401") })
        val repo = repo(api, scope = backgroundScope)
        runCurrent()

        assertEquals(emptyList<UsageLimit>(), repo.getUsageLimits().first())
        assertEquals(UsageLimitsStatus.Unavailable, repo.status.value)
    }

    @Test
    fun rateLimitKeepsLastGoodButFlagsRateLimited() = runTest {
        val api = FakeApi(
            { listOf(sampleLimit) },
            { throw UsageRateLimitException("429") },
        )
        val repo = repo(api, scope = backgroundScope)
        runCurrent()
        assertEquals(UsageLimitsStatus.Available, repo.status.value)

        advanceTimeBy(60.seconds + 1.seconds)
        runCurrent()

        // Throttled — the previous values stay on screen, but the status flips to RateLimited
        // so the header shows the (rate-limit-worded) warning.
        assertEquals(listOf(sampleLimit), repo.getUsageLimits().first())
        assertEquals(UsageLimitsStatus.RateLimited, repo.status.value)
        assertEquals(2, api.callCount)
    }

    @Test
    fun transientErrorKeepsLastGood() = runTest {
        val api = FakeApi(
            { listOf(sampleLimit) },
            { throw RuntimeException("network blip") },
        )
        val repo = repo(api, scope = backgroundScope)
        runCurrent()

        advanceTimeBy(60.seconds + 1.seconds)
        runCurrent()

        assertEquals(listOf(sampleLimit), repo.getUsageLimits().first())
        assertEquals(UsageLimitsStatus.Available, repo.status.value)
    }
}
