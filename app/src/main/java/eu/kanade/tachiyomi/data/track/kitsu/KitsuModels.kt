package eu.kanade.tachiyomi.data.track.kitsu

import android.support.annotation.CallSuper
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager

open class KitsuManga(obj: JsonObject) {
    val id by obj.byInt
    val canonicalTitle by obj["attributes"].byString
    val chapterCount = obj["attributes"].obj.get("chapterCount").nullInt
    val type = obj["attributes"]["mangaType"].string

    @CallSuper
    open fun toTrack() = Track.create(TrackManager.KITSU).apply {
        remote_id = this@KitsuManga.id
        title = canonicalTitle
        total_chapters = chapterCount ?: 0
    }
}

class KitsuLibManga(obj: JsonObject, manga: JsonObject) : KitsuManga(manga) {
    val remoteId by obj.byInt("id")
    val status by obj["attributes"].byString
    val rating = obj["attributes"].obj.get("rating").nullString
    val progress by obj["attributes"].byInt

    override fun toTrack() = super.toTrack().apply {
        remote_id = remoteId
        status = toTrackStatus()
        score = rating?.let { it.toFloat() * 2 } ?: 0f
        last_chapter_read = progress
    }

    private fun toTrackStatus() = when (status) {
        "current" -> Kitsu.READING
        "completed" -> Kitsu.COMPLETED
        "on_hold" -> Kitsu.ON_HOLD
        "dropped" -> Kitsu.DROPPED
        "planned" -> Kitsu.PLAN_TO_READ
        else -> throw Exception("Unknown status")
    }

}

fun Track.toKitsuStatus() = when (status) {
    Kitsu.READING -> "current"
    Kitsu.COMPLETED -> "completed"
    Kitsu.ON_HOLD -> "on_hold"
    Kitsu.DROPPED -> "dropped"
    Kitsu.PLAN_TO_READ -> "planned"
    else -> throw Exception("Unknown status")
}

fun Track.toKitsuScore(): String {
    return if (score > 0) (score / 2).toString() else ""
}
