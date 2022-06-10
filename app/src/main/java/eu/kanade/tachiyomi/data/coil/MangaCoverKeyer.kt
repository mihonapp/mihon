package eu.kanade.tachiyomi.data.coil

import coil.key.Keyer
import coil.request.Options
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.hasCustomCover

class MangaCoverKeyer : Keyer<Manga> {
    override fun key(data: Manga, options: Options): String {
        return if (data.hasCustomCover()) {
            "${data.id};${data.cover_last_modified}"
        } else {
            "${data.thumbnail_url};${data.cover_last_modified}"
        }
    }
}
