package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.update.GitHubReleaseApi
import dev.treder.info.claude.data.update.UpdateApi
import dev.treder.info.claude.domain.model.SemanticVersion
import dev.treder.info.claude.domain.model.UpdateStatus
import dev.treder.info.claude.domain.repository.UpdateRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Checks the release feed via [api] on start and every [refreshInterval]
 * thereafter, comparing the latest release against [currentVersion]. The
 * contract mirrors the pricing repository: a *successful* check is
 * authoritative (sets [UpdateStatus.UpdateAvailable] or [UpdateStatus.NoUpdate]),
 * while a *failed* check keeps the last known state — an update that was already
 * found is never hidden by a transient network outage.
 *
 * The check loop runs for the lifetime of [scope].
 */
class JvmUpdateRepository(
    private val currentVersion: String,
    private val api: UpdateApi = GitHubReleaseApi(),
    scope: CoroutineScope,
    private val refreshInterval: Duration = 4.hours,
) : UpdateRepository {

    private val _isUpdateAvailable = MutableStateFlow<UpdateStatus>(UpdateStatus.NoUpdate)
    override val isUpdateAvailable: StateFlow<UpdateStatus> = _isUpdateAvailable.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                checkOnce()
                delay(refreshInterval)
            }
        }
    }

    private suspend fun checkOnce() {
        runCatching { api.fetchLatestRelease() }
            .onSuccess { release ->
                _isUpdateAvailable.value =
                    if (release != null && SemanticVersion.isNewer(release.version, currentVersion)) {
                        UpdateStatus.UpdateAvailable(
                            versionNumber = release.version,
                            url = release.url,
                        )
                    } else {
                        UpdateStatus.NoUpdate
                    }
            }
            // Let cancellation propagate (runCatching would otherwise swallow it);
            // any other failure deliberately keeps the current state so a transient
            // outage never hides an update we already surfaced.
            .onFailure { if (it is CancellationException) throw it }
    }
}
