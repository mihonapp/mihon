package mihon.sync.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0,
    // Only returned on the initial authorization, not on refreshes.
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class GoogleUserInfo(
    @SerialName("email") val email: String = "",
)

/**
 * Raised when the account has to be linked again: no refresh token, or Google rejected the one we
 * hold (revoked access, password change, expired consent). Callers surface this to the user instead
 * of retrying, since no amount of retrying will fix it.
 */
class SyncAuthRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)
