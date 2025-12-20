package eu.kanade.tachiyomi.data.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import tachiyomi.i18n.MR

enum class TrackStatus(val int: Int, val res: StringResource) {
    READING(1, MR.strings.reading),
    REPEATING(2, MR.strings.repeating),
    PLAN_TO_READ(3, MR.strings.plan_to_read),
    PAUSED(4, MR.strings.on_hold),
    COMPLETED(5, MR.strings.completed),
    DROPPED(6, MR.strings.dropped),
    OTHER(7, MR.strings.not_tracked),
    ;

    companion object {
        fun parseTrackerStatus(trackerManager: TrackerManager, tracker: Long, status: Long): TrackStatus? {
            return when (tracker) {
                trackerManager.myAnimeList.id -> {
                    when (status) {
                        MyAnimeList.READING -> READING
                        MyAnimeList.COMPLETED -> COMPLETED
                        MyAnimeList.ON_HOLD -> PAUSED
                        MyAnimeList.PLAN_TO_READ -> PLAN_TO_READ
                        MyAnimeList.DROPPED -> DROPPED
                        MyAnimeList.REREADING -> REPEATING
                        else -> null
                    }
                }
                trackerManager.aniList.id -> {
                    when (status) {
                        Anilist.READING -> READING
                        Anilist.COMPLETED -> COMPLETED
                        Anilist.ON_HOLD -> PAUSED
                        Anilist.PLAN_TO_READ -> PLAN_TO_READ
                        Anilist.DROPPED -> DROPPED
                        Anilist.REREADING -> REPEATING
                        else -> null
                    }
                }
                trackerManager.kitsu.id -> {
                    when (status) {
                        Kitsu.READING -> READING
                        Kitsu.COMPLETED -> COMPLETED
                        Kitsu.ON_HOLD -> PAUSED
                        Kitsu.PLAN_TO_READ -> PLAN_TO_READ
                        Kitsu.DROPPED -> DROPPED
                        else -> null
                    }
                }
                trackerManager.shikimori.id -> {
                    when (status) {
                        Shikimori.READING -> READING
                        Shikimori.COMPLETED -> COMPLETED
                        Shikimori.ON_HOLD -> PAUSED
                        Shikimori.PLAN_TO_READ -> PLAN_TO_READ
                        Shikimori.DROPPED -> DROPPED
                        Shikimori.REREADING -> REPEATING
                        else -> null
                    }
                }
                trackerManager.bangumi.id -> {
                    when (status) {
                        Bangumi.READING -> READING
                        Bangumi.COMPLETED -> COMPLETED
                        Bangumi.ON_HOLD -> PAUSED
                        Bangumi.PLAN_TO_READ -> PLAN_TO_READ
                        Bangumi.DROPPED -> DROPPED
                        else -> null
                    }
                }
                trackerManager.komga.id -> {
                    when (status) {
                        Komga.READING -> READING
                        Komga.COMPLETED -> COMPLETED
                        Komga.UNREAD -> null
                        else -> null
                    }
                }
                trackerManager.mangaUpdates.id -> {
                    when (status) {
                        MangaUpdates.READING_LIST -> READING
                        MangaUpdates.COMPLETE_LIST -> COMPLETED
                        MangaUpdates.ON_HOLD_LIST -> PAUSED
                        MangaUpdates.WISH_LIST -> PLAN_TO_READ
                        MangaUpdates.UNFINISHED_LIST -> DROPPED
                        else -> null
                    }
                }
                else -> null
            }
        }
    }
}
