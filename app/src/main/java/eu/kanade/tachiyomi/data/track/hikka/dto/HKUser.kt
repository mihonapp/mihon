package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.Serializable

@Serializable
data class HKUser(
    val reference: String,
    val updated: Long,
    val created: Long,
    val description: String,
    val username: String,
    val cover: String,
    val active: Boolean,
    val avatar: String,
    val role: String,
)
