package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.pricing.PricingApi
import dev.treder.info.claude.domain.model.ModelPricing
import dev.treder.info.claude.domain.model.PricingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class JvmPricingRepositoryTest {

    private val opus = ModelPricing(5.0, 25.0, 0.5, 6.25, 10.0)
    private val haiku = ModelPricing(1.0, 5.0, 0.1, 1.25, 2.0)

    /** Queues fetch outcomes; each call pops the next producer (returning a map or throwing). */
    private class FakeApi(vararg outcomes: () -> Map<String, ModelPricing>) : PricingApi {
        private val queue = ArrayDeque(outcomes.toList())
        var callCount = 0
            private set

        override suspend fun fetch(): Map<String, ModelPricing> {
            callCount++
            val producer = queue.removeFirstOrNull() ?: error("no fetch outcome queued")
            return producer()
        }
    }

    @Test
    fun firstSuccessfulFetchBecomesReady() = runTest {
        val api = FakeApi({ mapOf("claude-opus-4-7" to opus) })

        val repo = JvmPricingRepository(api, backgroundScope, refreshInterval = 60.minutes)
        runCurrent()

        val state = repo.state.value
        assertTrue(state is PricingState.Ready)
        assertEquals(opus, state.table.forModel("claude-opus-4-7"))
        assertEquals(1, api.callCount)
    }

    @Test
    fun firstFetchFailureBecomesFailed() = runTest {
        val api = FakeApi({ throw RuntimeException("network down") })

        val repo = JvmPricingRepository(api, backgroundScope, refreshInterval = 60.minutes)
        runCurrent()

        assertEquals(PricingState.Failed, repo.state.value)
    }

    @Test
    fun emptyFeedIsTreatedAsFailure() = runTest {
        val api = FakeApi({ emptyMap() })

        val repo = JvmPricingRepository(api, backgroundScope, refreshInterval = 60.minutes)
        runCurrent()

        assertEquals(PricingState.Failed, repo.state.value)
    }

    @Test
    fun refreshFailureKeepsPreviousTable() = runTest {
        val api = FakeApi(
            { mapOf("m" to opus) },
            { throw RuntimeException("transient outage") },
        )

        val repo = JvmPricingRepository(api, backgroundScope, refreshInterval = 60.minutes)
        runCurrent()
        assertTrue(repo.state.value is PricingState.Ready)

        // Periodic refresh fires and fails — the prior table must survive.
        advanceTimeBy(60.minutes + 1.seconds)
        runCurrent()

        val state = repo.state.value
        assertTrue(state is PricingState.Ready)
        assertEquals(opus, state.table.forModel("m"))
        assertEquals(2, api.callCount)
    }

    @Test
    fun periodicRefreshPicksUpNewPrices() = runTest {
        val api = FakeApi(
            { mapOf("m" to opus) },
            { mapOf("m" to haiku) },
        )

        val repo = JvmPricingRepository(api, backgroundScope, refreshInterval = 60.minutes)
        runCurrent()
        assertEquals(opus, (repo.state.value as PricingState.Ready).table.forModel("m"))

        advanceTimeBy(60.minutes + 1.seconds)
        runCurrent()

        assertEquals(haiku, (repo.state.value as PricingState.Ready).table.forModel("m"))
        assertEquals(2, api.callCount)
    }

    @Test
    fun manualRefreshRecoversFromFailure() = runTest {
        val api = FakeApi(
            { throw RuntimeException("down") },
            { mapOf("m" to opus) },
        )

        val repo = JvmPricingRepository(api, backgroundScope, refreshInterval = 60.minutes)
        runCurrent()
        assertEquals(PricingState.Failed, repo.state.value)

        repo.refresh()
        runCurrent()

        val state = repo.state.value
        assertTrue(state is PricingState.Ready)
        assertEquals(opus, state.table.forModel("m"))
        assertEquals(2, api.callCount)
    }
}
