package dev.treder.info.claude.domain.model

/** Availability of the remote usage-limit feed, independent of the limit values. */
enum class UsageLimitsStatus {
    /** First fetch still pending — show neither bars nor a warning. */
    Loading,

    /** Last fetch succeeded — render whatever limits were returned. */
    Available,

    /** No usable token / HTTP 401-403 — show the auth warning (re-login). */
    Unavailable,

    /** Endpoint is throttling us (HTTP 429) — show the rate-limit warning; keep the last values. */
    RateLimited,
}
