package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.Serializable

@Serializable
data class HKClient(
    val reference: String,
    val name: String,
    val description: String,
    val verified: Boolean,
    val user: HKUser,
    val created: Long,
    val updated: Long
)
