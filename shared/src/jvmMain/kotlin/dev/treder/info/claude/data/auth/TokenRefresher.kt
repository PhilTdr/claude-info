package dev.treder.info.claude.data.auth

interface TokenRefresher {
    /** Exchanges a refresh token for a fresh access token. Throws on HTTP/IO failure. */
    suspend fun refresh(refreshToken: String, scopes: List<String>): RefreshedToken
}

data class RefreshedToken(
    val accessToken: String,
    /** A rotated refresh token if the server issued one; null to keep the existing one. */
    val refreshToken: String?,
    val expiresInSeconds: Long,
)
