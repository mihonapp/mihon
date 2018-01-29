package exh.metadata.models

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SManga
import exh.PERV_EDEN_EN_SOURCE_ID
import exh.PERV_EDEN_IT_SOURCE_ID
import exh.metadata.buildTagsDescription
import exh.metadata.joinEmulatedTagsToGenreString
import exh.plusAssign
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

@RealmClass
open class PervEdenGalleryMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    @Index
    var pvId: String? = null

    var url: String? = null
    var thumbnailUrl: String? = null

    @Index
    var title: String? = null
    var altTitles: RealmList<PervEdenTitle> = RealmList()

    @Index
    override var uploader: String? = null

    @Index
    var artist: String? = null

    var type: String? = null

    var rating: Float? = null

    var status: String? = null

    var lang: String? = null

    override var tags: RealmList<Tag> = RealmList()

    override fun getTitles() = listOf(title).plus(altTitles.map {
        it.title
    }).filterNotNull()

    @Ignore
    override val titleFields = TITLE_FIELDS

    @Index
    override var mangaId: Long? = null

    override fun copyTo(manga: SManga) {
        url?.let { manga.url = it }
        thumbnailUrl?.let { manga.thumbnail_url = it }

        val titleDesc = StringBuilder()
        title?.let {
            manga.title = it
            titleDesc += "Title: $it\n"
        }
        if(altTitles.isNotEmpty())
            titleDesc += "Alternate Titles: \n" + altTitles.map {
                "▪ ${it.title}"
            }.joinToString(separator = "\n", postfix = "\n")

        val detailsDesc = StringBuilder()
        artist?.let {
            manga.artist = it
            detailsDesc += "Artist: $it\n"
        }

        type?.let {
            detailsDesc += "Type: $it\n"
        }

        status?.let {
            manga.status = when(it) {
                "Ongoing" -> SManga.ONGOING
                "Completed", "Suspended" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            detailsDesc += "Status: $it\n"
        }

        rating?.let {
            detailsDesc += "Rating: %.2\n".format(it)
        }

        //Copy tags -> genres
        manga.genre = joinEmulatedTagsToGenreString(this)

        val tagsDesc = buildTagsDescription(this)

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    class EmptyQuery : GalleryQuery<PervEdenGalleryMetadata>(PervEdenGalleryMetadata::class)

    class UrlQuery(
            val url: String,
            val lang: PervEdenLang
    ) : GalleryQuery<PervEdenGalleryMetadata>(PervEdenGalleryMetadata::class) {
        override fun transform() = Query(
                pvIdFromUrl(url),
                lang
        )
    }

    class Query(val pvId: String,
                val lang: PervEdenLang
    ) : GalleryQuery<PervEdenGalleryMetadata>(PervEdenGalleryMetadata::class) {
        override fun map() = mapOf(
                PervEdenGalleryMetadata::pvId to Query::pvId
        )

        override fun override(meta: RealmQuery<PervEdenGalleryMetadata>)
            = meta.equalTo(PervEdenGalleryMetadata::lang.name, lang.name)
    }

    companion object {
        private fun splitGalleryUrl(url: String)
                = url.let {
            Uri.parse(it).pathSegments.filterNot(String::isNullOrBlank)
        }

        fun pvIdFromUrl(url: String) = splitGalleryUrl(url).last()

        val TITLE_FIELDS = listOf(
                //TODO Somehow include altTitles
                PervEdenGalleryMetadata::title.name
        )
    }
}

@RealmClass
open class PervEdenTitle(var metadata: PervEdenGalleryMetadata? = null,
                         @Index var title: String? = null): RealmObject() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PervEdenTitle

        if (metadata != other.metadata) return false
        if (title != other.title) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metadata?.hashCode() ?: 0
        result = 31 * result + (title?.hashCode() ?: 0)
        return result
    }

    override fun toString() = "PervEdenTitle(metadata=$metadata, title=$title)"
}

enum class PervEdenLang(val id: Long) {
    //DO NOT RENAME THESE TO CAPITAL LETTERS! The enum names are used to build URLs
    en(PERV_EDEN_EN_SOURCE_ID),
    it(PERV_EDEN_IT_SOURCE_ID);

    companion object {
        fun source(id: Long)
                = PervEdenLang.values().find { it.id == id }
                ?: throw IllegalArgumentException("Unknown source ID: $id!")
    }
}