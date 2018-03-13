package exh.metadata.models

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.*
import exh.plusAssign
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*

/**
 * NHentai metadata
 */

@RealmClass
open class NHentaiMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    var nhId: Long? = null

    var url get() = nhId?.let { "$BASE_URL/g/$it" }
    set(a) {
        a?.let {
            nhId = nhIdFromUrl(a)
        }
    }

    @Index
    override var uploader: String? = null

    var uploadDate: Long? = null

    var favoritesCount: Long? = null

    var mediaId: String? = null

    @Index
    var japaneseTitle: String? = null
    @Index
    var englishTitle: String? = null
    @Index
    var shortTitle: String? = null

    var coverImageType: String? = null
    var pageImageTypes: RealmList<PageImageType> = RealmList()
    var thumbnailImageType: String? = null

    var scanlator: String? = null

    override var tags: RealmList<Tag> = RealmList()

    override fun getTitles() = listOf(japaneseTitle, englishTitle, shortTitle).filterNotNull()

    @Ignore
    override val titleFields = TITLE_FIELDS

    @Index
    override var mangaId: Long? = null

    class EmptyQuery : GalleryQuery<NHentaiMetadata>(NHentaiMetadata::class)

    class UrlQuery(
            val url: String
    ) : GalleryQuery<NHentaiMetadata>(NHentaiMetadata::class) {
        override fun transform() = Query(
                nhIdFromUrl(url)
        )
    }

    class Query(
            val nhId: Long
    ) : GalleryQuery<NHentaiMetadata>(NHentaiMetadata::class) {
        override fun map() = mapOf(
                ::nhId to Query::nhId
        )
    }

    override fun copyTo(manga: SManga) {
        url?.let { manga.url = it }

        if(mediaId != null)
            NHentaiMetadata.typeToExtension(thumbnailImageType)?.let {
                manga.thumbnail_url = "https://t.nhentai.net/galleries/$mediaId/${
                if(Injekt.get<PreferencesHelper>().eh_nh_useHighQualityThumbs().getOrDefault())
                    "cover"
                else
                    "thumb"
                }.$it"
            }

        manga.title = englishTitle ?: japaneseTitle ?: shortTitle!!

        //Set artist (if we can find one)
        tags.filter { it.namespace == NHENTAI_ARTIST_NAMESPACE }.let {
            if(it.isNotEmpty()) manga.artist = it.joinToString(transform = { it.name!! })
        }

        var category: String? = null
        tags.filter { it.namespace == NHENTAI_CATEGORIES_NAMESPACE }.let {
            if(it.isNotEmpty()) category = it.joinToString(transform = { it.name!! })
        }

        //Copy tags -> genres
        manga.genre = joinEmulatedTagsToGenreString(this)

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

        val tagsDesc = buildTagsDescription(this)

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        val BASE_URL = "https://nhentai.net"

        private const val NHENTAI_ARTIST_NAMESPACE = "artist"
        private const val NHENTAI_CATEGORIES_NAMESPACE = "category"

        fun typeToExtension(t: String?) =
                when(t) {
                    "p" -> "png"
                    "j" -> "jpg"
                    else -> null
                }

        fun nhIdFromUrl(url: String)
                = url.split("/").last { it.isNotBlank() }.toLong()

        val TITLE_FIELDS = listOf(
                NHentaiMetadata::japaneseTitle.name,
                NHentaiMetadata::englishTitle.name,
                NHentaiMetadata::shortTitle.name
        )
    }
}

@RealmClass
open class PageImageType(var type: String? = null): RealmObject() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageImageType

        if (type != other.type) return false

        return true
    }


    override fun hashCode() = type?.hashCode() ?: 0

    override fun toString() = "PageImageType(type=$type)"
}
