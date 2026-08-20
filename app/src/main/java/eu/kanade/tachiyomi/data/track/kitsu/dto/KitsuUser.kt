package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuCurrentAccountResult(
    val data: KitsuCurrentAccountData,
)

@Serializable
data class KitsuCurrentAccountData(
    val currentAccount: KitsuAccount,
)

@Serializable
data class KitsuAccount(
    val id: String,
    val ratingSystem: String,
    val profile: KitsuProfile,
)

@Serializable
data class KitsuProfile(
    val name: String,
)
