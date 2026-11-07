package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.chapter.model.ChapterThumbnail

class ChapterThumbnailKeyer : Keyer<ChapterThumbnail> {
    override fun key(data: ChapterThumbnail, options: Options): String? {
        return if (data.isMangaFavorite) {
            "chapter;${data.chapterId};${data.lastModified}"
        } else {
            "chapter;${data.url};${data.lastModified}"
        }
    }
}
