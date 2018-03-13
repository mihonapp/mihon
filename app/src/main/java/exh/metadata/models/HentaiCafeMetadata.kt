package exh.metadata.models

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.buildTagsDescription
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

@RealmClass
open class HentaiCafeMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    @Index
    var hcId: String? = null
    var readerId: String? = null

    var url get() = hcId?.let { "$BASE_URL/$it" }
        set(a) {
            a?.let {
                hcId = hcIdFromUrl(a)
            }
        }
    
    var thumbnailUrl: String? = null

    var title: String? = null

    var artist: String? = null

    override var uploader: String? = null //Always will be null as this is unknown

    override var tags: RealmList<Tag> = RealmList()

    override fun getTitles() = listOfNotNull(title)

    @Ignore
    override val titleFields = listOf(
            ::title.name
    )

    @Index
    override var mangaId: Long? = null

    override fun copyTo(manga: SManga) {
        thumbnailUrl?.let { manga.thumbnail_url = it }
        
        manga.title = title!!
        manga.artist = artist
        manga.author = artist

        //Not available
        manga.status = SManga.UNKNOWN

        val detailsDesc = "Title: $title\n" +
                "Artist: $artist\n"

        val tagsDesc = buildTagsDescription(this)

        manga.genre = tags.filter { it.namespace == "tag" }.joinToString {
            it.name!!
        }

        manga.description = listOf(detailsDesc, tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    class EmptyQuery : GalleryQuery<HentaiCafeMetadata>(HentaiCafeMetadata::class)

    class UrlQuery(
            val url: String
    ) : GalleryQuery<HentaiCafeMetadata>(HentaiCafeMetadata::class) {
        override fun transform() = Query(
                hcIdFromUrl(url)
        )
    }

    class Query(val hcId: String): GalleryQuery<HentaiCafeMetadata>(HentaiCafeMetadata::class) {
        override fun map() = mapOf(
                HentaiCafeMetadata::hcId to Query::hcId
        )
    }

    companion object {
        val BASE_URL = "https://hentai.cafe"

        fun hcIdFromUrl(url: String)
                = url.split("/").last { it.isNotBlank() }
    }
}