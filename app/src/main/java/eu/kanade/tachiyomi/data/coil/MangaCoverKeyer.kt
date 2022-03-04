package eu.kanade.tachiyomi.data.coil

import coil.key.Keyer
import coil.request.Options
import eu.kanade.tachiyomi.data.database.models.Manga

class MangaCoverKeyer : Keyer<Manga> {
    override fun key(data: Manga, options: Options): String? {
        return data.thumbnail_url?.takeIf { it.isNotBlank() }
    }
}
