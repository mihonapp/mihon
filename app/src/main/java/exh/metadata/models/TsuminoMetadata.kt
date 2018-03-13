package exh.metadata.models

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.buildTagsDescription
import exh.metadata.joinEmulatedTagsToGenreString
import exh.plusAssign
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

@RealmClass
open class TsuminoMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()
    
    @Index
    var tmId: String? = null
    
    var url get() = tmId?.let { mangaUrlFromId(it) }
        set(a) {
            a?.let {
                tmId = tmIdFromUrl(a)
            }
        }
    
    var title: String? = null
    
    var artist: String? = null
    
    override var uploader: String? = null
    
    var uploadDate: Long? = null
    
    var length: Int? = null
    
    var ratingString: String? = null
    
    var category: String? = null
    
    var collection: String? = null
    
    var group: String? = null
    
    var parody: RealmList<String> = RealmList()
    
    var character: RealmList<String> = RealmList()
    
    override var tags: RealmList<Tag> = RealmList()
    
    override fun getTitles() = listOfNotNull(title)
    
    @Ignore
    override val titleFields = listOf(
            ::title.name
    )
    
    @Index
    override var mangaId: Long? = null
    
    class EmptyQuery : GalleryQuery<TsuminoMetadata>(TsuminoMetadata::class)
    
    class UrlQuery(
            val url: String
    ) : GalleryQuery<TsuminoMetadata>(TsuminoMetadata::class) {
        override fun transform() = Query(
                tmIdFromUrl(url)
        )
    }
    
    class Query(
            val tmId: String
    ) : GalleryQuery<TsuminoMetadata>(TsuminoMetadata::class) {
        override fun map() = mapOf(
                ::tmId to Query::tmId
        )
    }
    
    override fun copyTo(manga: SManga) {
        title?.let { manga.title = it }
        manga.thumbnail_url = thumbUrlFromId(tmId.toString())
        
        artist?.let { manga.artist = it }
        
        manga.status = SManga.UNKNOWN
        
        val titleDesc = "Title: $title\n"
    
        val detailsDesc = StringBuilder()
        uploader?.let { detailsDesc += "Uploader: $it\n" }
        uploadDate?.let { detailsDesc += "Uploaded: ${EX_DATE_FORMAT.format(Date(it))}\n" }
        length?.let { detailsDesc += "Length: $it pages\n" }
        ratingString?.let { detailsDesc += "Rating: $it\n" }
        category?.let {
            detailsDesc += "Category: $it\n"
        }
        collection?.let { detailsDesc += "Collection: $it\n" }
        group?.let { detailsDesc += "Group: $it\n" }
        val parodiesString = parody.joinToString()
        if(parodiesString.isNotEmpty()) {
            detailsDesc += "Parody: $parodiesString\n"
        }
        val charactersString = character.joinToString()
        if(charactersString.isNotEmpty()) {
            detailsDesc += "Character: $charactersString\n"
        }

        //Copy tags -> genres
        manga.genre = joinEmulatedTagsToGenreString(this)

        val tagsDesc = buildTagsDescription(this)
        
        manga.description = listOf(titleDesc, detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }
    
    companion object {
        val BASE_URL = "http://www.tsumino.com"
        
        fun tmIdFromUrl(url: String)
            = Uri.parse(url).pathSegments[2]
        
        fun mangaUrlFromId(id: String) = "$BASE_URL/Book/Info/$id"
        
        fun thumbUrlFromId(id: String) = "$BASE_URL/Image/Thumb/$id"
    }
}