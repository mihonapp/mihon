package exh.metadata.metadata

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.*
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.plusAssign
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*

class NHentaiSearchMetadata : RaisedSearchMetadata() {
    var url get() = nhId?.let { BASE_URL + nhIdToPath(it) }
        set(a) {
            a?.let {
                nhId = nhUrlToId(a)
            }
        }

    var nhId: Long? = null

    var uploadDate: Long? = null

    var favoritesCount: Long? = null

    var mediaId: String? = null

    var japaneseTitle by titleDelegate(TITLE_TYPE_JAPANESE)
    var englishTitle by titleDelegate(TITLE_TYPE_ENGLISH)
    var shortTitle by titleDelegate(TITLE_TYPE_SHORT)

    var coverImageType: String? = null
    var pageImageTypes: List<String> = emptyList()
    var thumbnailImageType: String? = null

    var scanlator: String? = null

    override fun copyTo(manga: SManga) {
        nhId?.let { manga.url = nhIdToPath(it) }

        if(mediaId != null) {
            val hqThumbs = Injekt.get<PreferencesHelper>().eh_nh_useHighQualityThumbs().getOrDefault()
            typeToExtension(if(hqThumbs) coverImageType else thumbnailImageType)?.let {
                manga.thumbnail_url = "https://t.nhentai.net/galleries/$mediaId/${if(hqThumbs)
                    "cover"
                else "thumb"}.$it"
            }
        }

        manga.title = englishTitle ?: japaneseTitle ?: shortTitle!!

        //Set artist (if we can find one)
        tags.filter { it.namespace == NHENTAI_ARTIST_NAMESPACE }.let {
            if(it.isNotEmpty()) manga.artist = it.joinToString(transform = { it.name })
        }

        var category: String? = null
        tags.filter { it.namespace == NHENTAI_CATEGORIES_NAMESPACE }.let {
            if(it.isNotEmpty()) category = it.joinToString(transform = { it.name })
        }

        //Copy tags -> genres
        manga.genre = tagsToGenreString()

        //Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
        //We default to completed
        manga.status = SManga.COMPLETED
        englishTitle?.let { t ->
            ONGOING_SUFFIX.find {
                t.endsWith(it, ignoreCase = true)
            }?.let {
                manga.status = SManga.ONGOING
            }
        }

        val titleDesc = StringBuilder()
        englishTitle?.let { titleDesc += "English Title: $it\n" }
        japaneseTitle?.let { titleDesc += "Japanese Title: $it\n" }
        shortTitle?.let { titleDesc += "Short Title: $it\n" }

        val detailsDesc = StringBuilder()
        category?.let { detailsDesc += "Category: $it\n" }
        uploadDate?.let { detailsDesc += "Upload Date: ${EX_DATE_FORMAT.format(Date(it * 1000))}\n" }
        pageImageTypes.size.let { detailsDesc += "Length: $it pages\n" }
        favoritesCount?.let { detailsDesc += "Favorited: $it times\n" }
        scanlator?.nullIfBlank()?.let { detailsDesc += "Scanlator: $it\n" }

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        private const val TITLE_TYPE_JAPANESE = 0
        private const val TITLE_TYPE_ENGLISH = 1
        private const val TITLE_TYPE_SHORT = 2

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://nhentai.net"

        private const val NHENTAI_ARTIST_NAMESPACE = "artist"
        private const val NHENTAI_CATEGORIES_NAMESPACE = "category"

        fun typeToExtension(t: String?) =
                when(t) {
                    "p" -> "png"
                    "j" -> "jpg"
                    else -> null
                }

        fun nhUrlToId(url: String)
                = url.split("/").last { it.isNotBlank() }.toLong()

        fun nhIdToPath(id: Long) = "/g/$id/"
    }
}
