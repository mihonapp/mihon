package mihon.domain.network

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Cookie(
    val name: String,
    val value: String,
    val path: String,
    val hostOnly: Boolean,
)
