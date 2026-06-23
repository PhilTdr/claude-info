package dev.treder.info.claude.domain.model

/**
 * Whether a newer release than the running build is available.
 *
 * - [NoUpdate]: the running build is current — or nothing newer is known yet.
 * - [UpdateAvailable]: a newer release exists; [versionNumber] names it and [url]
 *   points at its release page.
 */
sealed interface UpdateStatus {

    data object NoUpdate : UpdateStatus

    data class UpdateAvailable(
        val versionNumber: String,
        val url: String,
    ) : UpdateStatus
}
