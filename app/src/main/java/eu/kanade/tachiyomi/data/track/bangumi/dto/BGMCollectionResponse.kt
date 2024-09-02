package eu.kanade.tachiyomi.data.track.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BGMCollectionResponse(
    val code: Int?,
    val `private`: Int? = 0,
    val comment: String? = "",
    @SerialName("ep_status")
    val epStatus: Int? = 0,
    @SerialName("lasttouch")
    val lastTouch: Int? = 0,
    val rating: Double? = 0.0,
    val status: Status? = Status(),
    val tag: List<String?>? = emptyList(),
    val user: User? = User(),
    @SerialName("vol_status")
    val volStatus: Int? = 0,
)

@Serializable
data class Status(
    val id: Long? = 0,
    val name: String? = "",
    val type: String? = "",
)
