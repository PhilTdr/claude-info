package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.update.LatestRelease
import dev.treder.info.claude.data.update.UpdateApi
import dev.treder.info.claude.domain.model.UpdateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class JvmUpdateRepositoryTest {

    /** Queues check outcomes; each call pops the next producer (returning a release/null or throwing). */
    private class FakeApi(vararg outcomes: () -> LatestRelease?) : UpdateApi {
        private val queue = ArrayDeque(outcomes.toList())
        var callCount = 0
            private set

        override suspend fun fetchLatestRelease(): LatestRelease? {
            callCount++
            val producer = queue.removeFirstOrNull() ?: error("no check outcome queued")
            return producer()
        }
    }

    @Test
    fun newerReleaseBecomesUpdateAvailable() = runTest {
        val api = FakeApi({ LatestRelease("v1.2.0", "https://example.test/release") })

        val repo = JvmUpdateRepository("1.0.0", api, backgroundScope, refreshInterval = 4.hours)
        runCurrent()

        val state = repo.isUpdateAvailable.value
        assertTrue(state is UpdateStatus.UpdateAvailable)
        assertEquals("v1.2.0", state.versionNumber)
        assertEquals("https://example.test/release", state.url)
        assertEquals(1, api.callCount)
    }

    @Test
    fun sameVersionStaysNoUpdate() = runTest {
        val api = FakeApi({ LatestRelease("1.0.0", "https://example.test/release") })

        val repo = JvmUpdateRepository("1.0.0", api, backgroundScope, refreshInterval = 4.hours)
        runCurrent()

        assertEquals(UpdateStatus.NoUpdate, repo.isUpdateAvailable.value)
    }

    @Test
    fun firstCheckFailureStaysNoUpdate() = runTest {
        val api = FakeApi({ throw RuntimeException("offline") })

        val repo = JvmUpdateRepository("1.0.0", api, backgroundScope, refreshInterval = 4.hours)
        runCurrent()

        assertEquals(UpdateStatus.NoUpdate, repo.isUpdateAvailable.value)
    }

    @Test
    fun laterFailureKeepsPreviousUpdateAvailable() = runTest {
        val api = FakeApi(
            { LatestRelease("1.5.0", "https://example.test/release") },
            { throw RuntimeException("transient outage") },
        )

        val repo = JvmUpdateRepository("1.0.0", api, backgroundScope, refreshInterval = 4.hours)
        runCurrent()
        assertTrue(repo.isUpdateAvailable.value is UpdateStatus.UpdateAvailable)

        // Periodic check fires and fails — the previously found update must survive.
        advanceTimeBy(4.hours + 1.seconds)
        runCurrent()

        assertTrue(repo.isUpdateAvailable.value is UpdateStatus.UpdateAvailable)
        assertEquals(2, api.callCount)
    }

    @Test
    fun periodicCheckPicksUpNewlyPublishedRelease() = runTest {
        val api = FakeApi(
            { LatestRelease("1.0.0", "https://example.test/release") },
            { LatestRelease("2.0.0", "https://example.test/release-2") },
        )

        val repo = JvmUpdateRepository("1.0.0", api, backgroundScope, refreshInterval = 4.hours)
        runCurrent()
        assertEquals(UpdateStatus.NoUpdate, repo.isUpdateAvailable.value)

        advanceTimeBy(4.hours + 1.seconds)
        runCurrent()

        val state = repo.isUpdateAvailable.value
        assertTrue(state is UpdateStatus.UpdateAvailable)
        assertEquals("2.0.0", state.versionNumber)
        assertEquals(2, api.callCount)
    }

    @Test
    fun successfulNoUpdateClearsAStaleUpdate() = runTest {
        // First check finds an update; a later successful check finds the release
        // gone (e.g. yanked) and must clear the hint back to NoUpdate.
        val api = FakeApi(
            { LatestRelease("2.0.0", "https://example.test/release") },
            { LatestRelease("1.0.0", "https://example.test/release") },
        )

        val repo = JvmUpdateRepository("1.0.0", api, backgroundScope, refreshInterval = 4.hours)
        runCurrent()
        assertTrue(repo.isUpdateAvailable.value is UpdateStatus.UpdateAvailable)

        advanceTimeBy(4.hours + 1.seconds)
        runCurrent()

        assertEquals(UpdateStatus.NoUpdate, repo.isUpdateAvailable.value)
    }
}
