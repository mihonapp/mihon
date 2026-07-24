package mihon.domain.network

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class CookieIndex(
    val key: String,
    val domain: String,
    val path: String,
)
