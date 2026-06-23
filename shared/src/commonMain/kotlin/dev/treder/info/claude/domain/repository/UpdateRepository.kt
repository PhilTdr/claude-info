package dev.treder.info.claude.domain.repository

import dev.treder.info.claude.domain.model.UpdateStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive source of update availability. The implementation checks a remote
 * release feed on start and periodically thereafter, exposing the result as
 * observable state. A failed check keeps the last known state in place rather
 * than hiding an update that was already found.
 */
interface UpdateRepository {

    /** Whether a newer version than the running build is available. */
    val isUpdateAvailable: StateFlow<UpdateStatus>
}
