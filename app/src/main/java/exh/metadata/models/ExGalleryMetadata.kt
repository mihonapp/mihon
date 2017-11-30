package exh.metadata.models

import android.net.Uri
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.ONGOING_SUFFIX
import exh.metadata.buildTagsDescription
import exh.metadata.humanReadableByteCount
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

    override fun getTitles() = listOf(title, altTitle).filterNotNull()

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
                ExGalleryMetadata::gId to Query::gId,
                ExGalleryMetadata::gToken to Query::gToken,
                ExGalleryMetadata::exh to Query::exh
        )
    }

    override fun copyTo(manga: SManga) {
        url?.let { manga.url = it }
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
        //Set author (if we can find one)
        tags.filter { it.namespace == EH_AUTHOR_NAMESPACE }.let {
            if(it.isNotEmpty()) manga.author = it.joinToString(transform = { it.name!! })
        }
        //Set genre
        genre?.let { manga.genre = it }

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
            Uri.parse(it).pathSegments
                    .filterNot(String::isNullOrBlank)
        }

        fun galleryId(url: String) = splitGalleryUrl(url).let { it[it.size - 2] }

        fun galleryToken(url: String) =
                splitGalleryUrl(url).last()

        val TITLE_FIELDS = listOf(
                ExGalleryMetadata::title.name,
                ExGalleryMetadata::altTitle.name
        )

        private const val EH_ARTIST_NAMESPACE = "artist"
        private const val EH_AUTHOR_NAMESPACE = "author"

    }
}