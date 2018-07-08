package exh.metadata.models

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.buildTagsDescription
import exh.metadata.joinTagsToGenreString
import exh.plusAssign
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

@RealmClass
open class HitomiGalleryMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    @Index
    var hlId: String? = null

    var thumbnailUrl: String? = null

    var artist: String? = null

    var group: String? = null

    var type: String? = null

    var language: String? = null

    var languageSimple: String? = null

    var series: RealmList<String> = RealmList()
    
    var characters: RealmList<String> = RealmList()

    var buyLink: String? = null

    var uploadDate: Long? = null

    override var tags: RealmList<Tag> = RealmList()

    // Sites does not show uploader
    override var uploader: String? = "admin"

    var url get() = hlId?.let { urlFromHlId(it) }
        set(a) {
            a?.let {
                hlId = hlIdFromUrl(a)
            }
        }

    @Index
    override var mangaId: Long? = null

    @Index
    var title: String? = null

    override fun getTitles() = listOfNotNull(title)

    @Ignore
    override val titleFields = listOf(
            ::title.name
    )

    class EmptyQuery : GalleryQuery<HitomiGalleryMetadata>(HitomiGalleryMetadata::class)

    class UrlQuery(
            val url: String
    ) : GalleryQuery<HitomiGalleryMetadata>(HitomiGalleryMetadata::class) {
        override fun transform() = Query(
                hlIdFromUrl(url)
        )
    }

    class Query(val hlId: String): GalleryQuery<HitomiGalleryMetadata>(HitomiGalleryMetadata::class) {
        override fun map() = mapOf(
                HitomiGalleryMetadata::hlId to Query::hlId
        )
    }

    override fun copyTo(manga: SManga) {
        thumbnailUrl?.let { manga.thumbnail_url = it }

        val titleDesc = StringBuilder()

        title?.let {
            manga.title = it
            titleDesc += "Title: $it\n"
        }

        val detailsDesc = StringBuilder()

        artist?.let {
            manga.artist = it
            manga.author = it

            detailsDesc += "Artist: $it\n"
        }

        group?.let {
            detailsDesc += "Group: $it\n"
        }

        type?.let {
            detailsDesc += "Type: $it\n"
        }

        (language ?: languageSimple ?: "none").let {
            detailsDesc += "Language: $it\n"
        }

        if(series.isNotEmpty())
            detailsDesc += "Series: ${series.joinToString()}\n"

        if(characters.isNotEmpty())
            detailsDesc += "Characters: ${characters.joinToString()}\n"

        uploadDate?.let {
            detailsDesc += "Upload date: ${EX_DATE_FORMAT.format(Date(it))}\n"
        }

        buyLink?.let {
            detailsDesc += "Buy at: $it"
        }

        manga.status = SManga.UNKNOWN

        //Copy tags -> genres
        manga.genre = joinTagsToGenreString(this)

        val tagsDesc = buildTagsDescription(this)

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        val LTN_BASE_URL = "https://ltn.hitomi.la"
        val BASE_URL = "https://hitomi.la"

        fun hlIdFromUrl(url: String)
                = url.split('/').last().substringBeforeLast('.')

        fun urlFromHlId(id: String)
                = "$BASE_URL/galleries/$id.html"
    }
}
