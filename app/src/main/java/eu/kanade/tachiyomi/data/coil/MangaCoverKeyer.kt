package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.Manga as DomainManga

class MangaKeyer(
    private val coverCache: CoverCache,
) : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        return if (data.favorite && data.hasCustomCover(coverCache)) {
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class MangaCoverKeyer(
    private val coverCache: CoverCache,
) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        return if (data.isMangaFavorite && coverCache.hasCustomCover(data.mangaId)) {
            "${data.mangaId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified}"
        }
    }
}
