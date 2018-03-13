package exh.metadata.models

import android.net.Uri
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
 * Gallery metadata storage model
 */

@RealmClass
open class ExGalleryMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    var url: String? = null
        set(value) {
            //Ensure that URLs are always formatted in the same way to reduce duplicate galleries
            field = value?.let { normalizeUrl(it) }
        }

    @Index
    var gId: String? = null
    @Index
    var gToken: String? = null

    @Index
    var exh: Boolean? = null

    var thumbnailUrl: String? = null

    @Index
    var title: String? = null
    @Index
    var altTitle: String? = null

    @Index
    override var uploader: String? = null

    var genre: String? = null

    var datePosted: Long? = null
    var parent: String? = null
    var visible: String? = null //Not a boolean
    var language: String? = null
    var translated: Boolean? = null
    var size: Long? = null
    var length: Int? = null
    var favorites: Int? = null
    var ratingCount: Int? = null
    var averageRating: Double? = null

    override var tags: RealmList<Tag> = RealmList()

    override fun getTitles() = listOfNotNull(title, altTitle)

    @Ignore
    override val titleFields = TITLE_FIELDS

    @Index
    override var mangaId: Long? = null

    class EmptyQuery : GalleryQuery<ExGalleryMetadata>(ExGalleryMetadata::class)

    class UrlQuery(
            val url: String,
            val exh: Boolean
    ) : GalleryQuery<ExGalleryMetadata>(ExGalleryMetadata::class) {
        override fun transform() = Query(
                galleryId(url),
                galleryToken(url),
                exh
        )
    }

    class Query(val gId: String,
                val gToken: String,
                val exh: Boolean
    ) : GalleryQuery<ExGalleryMetadata>(ExGalleryMetadata::class) {
        override fun map() = mapOf(
                ::gId to Query::gId,
                ::gToken to Query::gToken,
                ::exh to Query::exh
        )
    }

    override fun copyTo(manga: SManga) {
        url?.let { manga.url = normalizeUrl(it) }
        thumbnailUrl?.let { manga.thumbnail_url = it }

        //No title bug?
        val titleObj = if(Injekt.get<PreferencesHelper>().useJapaneseTitle().getOrDefault())
            altTitle ?: title
        else
            title
        titleObj?.let { manga.title = it }

        //Set artist (if we can find one)
        tags.filter { it.namespace == EH_ARTIST_NAMESPACE }.let {
            if(it.isNotEmpty()) manga.artist = it.joinToString(transform = { it.name!! })
        }

        //Copy tags -> genres
        manga.genre = joinTagsToGenreString(this)

        //Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
        //We default to completed
        manga.status = SManga.COMPLETED
        title?.let { t ->
            ONGOING_SUFFIX.find {
                t.endsWith(it, ignoreCase = true)
            }?.let {
                        manga.status = SManga.ONGOING
                    }
        }

        //Build a nice looking description out of what we know
        val titleDesc = StringBuilder()
        title?.let { titleDesc += "Title: $it\n" }
        altTitle?.let { titleDesc += "Alternate Title: $it\n" }

        val detailsDesc = StringBuilder()
        genre?.let { detailsDesc += "Genre: $it\n" }
        uploader?.let { detailsDesc += "Uploader: $it\n" }
        datePosted?.let { detailsDesc += "Posted: ${EX_DATE_FORMAT.format(Date(it))}\n" }
        visible?.let { detailsDesc += "Visible: $it\n" }
        language?.let {
            detailsDesc += "Language: $it"
            if(translated == true) detailsDesc += " TR"
            detailsDesc += "\n"
        }
        size?.let { detailsDesc += "File Size: ${humanReadableByteCount(it, true)}\n" }
        length?.let { detailsDesc += "Length: $it pages\n" }
        favorites?.let { detailsDesc += "Favorited: $it times\n" }
        averageRating?.let {
            detailsDesc += "Rating: $it"
            ratingCount?.let { detailsDesc += " ($it)" }
            detailsDesc += "\n"
        }

        val tagsDesc = buildTagsDescription(this)

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        private fun splitGalleryUrl(url: String)
                = url.let {
            //Only parse URL if is full URL
            val pathSegments = if(it.startsWith("http"))
                Uri.parse(it).pathSegments
            else
                it.split('/')
            pathSegments.filterNot(String::isNullOrBlank)
        }

        fun galleryId(url: String) = splitGalleryUrl(url)[1]

        fun galleryToken(url: String) =
                splitGalleryUrl(url)[2]

        fun normalizeUrl(id: String, token: String)
                = "/g/$id/$token/?nw=always"

        fun normalizeUrl(url: String)
                = normalizeUrl(galleryId(url), galleryToken(url))

        val TITLE_FIELDS = listOf(
                ExGalleryMetadata::title.name,
                ExGalleryMetadata::altTitle.name
        )

        private const val EH_ARTIST_NAMESPACE = "artist"

    }
}