package mihon.domain.network

import kotlin.time.Instant

data class Cookie(
    val name: String,
    val value: String,
    val path: String,
    val hostOnly: Boolean,
    val expiresAt: Instant?,
    val secure: Boolean,
    val httpOnly: Boolean,
    val sameSite: SameSite,
) {
    data class Identity(
        val name: String,
        val path: String,
        val hostOnly: Boolean,
    ) {
        val key: String get() = "$name:$path|$hostOnly"
    }

    val identity: Identity
        get() = Identity(name, path, hostOnly)
}

enum class SameSite {
    Unspecified,
    None,
    Lax,
    Strict,
    ;

    companion object {
        fun fromDatabaseValue(value: Long): SameSite = when (value) {
            0L -> None
            1L -> Lax
            2L -> Strict
            else -> Unspecified
        }
    }
}
