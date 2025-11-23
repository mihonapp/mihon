package eu.kanade.tachiyomi.data.track.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.core.common.JWT

class AnilistToken(
    val value: String,
    val decoded: Decoded,
) {
    @Serializable
    class Decoded(
        @SerialName("sub")
        val id: String,
        @SerialName("exp")
        val expiresAt: Long,
    )

    companion object {
        fun from(accessToken: String): AnilistToken {
            val alTokenDecoded = JWT.decode<Decoded>(accessToken)
            return AnilistToken(accessToken, alTokenDecoded)
        }
    }
}
