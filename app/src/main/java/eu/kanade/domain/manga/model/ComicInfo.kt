package eu.kanade.domain.manga.model

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

const val COMIC_INFO_FILE = "ComicInfo.xml"

/**
 * Creates a ComicInfo instance based on the manga and chapter metadata.
 */
fun getComicInfo(manga: Manga, chapter: Chapter): ComicInfo {
    return ComicInfo(
        title = ComicInfo.Title(chapter.name),
        series = ComicInfo.Series(manga.title),
        web = ComicInfo.Web(manga.url),
        summary = manga.description?.let { ComicInfo.Summary(it) },
        writer = manga.author?.let { ComicInfo.Writer(it) },
        penciller = manga.artist?.let { ComicInfo.Penciller(it) },
        translator = chapter.scanlator?.let { ComicInfo.Translator(it) },
        genre = manga.genre?.let { ComicInfo.Genre(it.joinToString()) },
        publishingStatusTachiyomi = ComicInfo.PublishingStatusTachiyomi(
            ComicInfoPublishingStatusMap.toComicInfoValue(manga.status),
        ),
        inker = null,
        colorist = null,
        letterer = null,
        coverArtist = null,
        tags = null,
    )
}

@Serializable
@XmlSerialName("ComicInfo", "", "")
data class ComicInfo(
    val title: Title?,
    val series: Series?,
    val summary: Summary?,
    val writer: Writer?,
    val penciller: Penciller?,
    val inker: Inker?,
    val colorist: Colorist?,
    val letterer: Letterer?,
    val coverArtist: CoverArtist?,
    val translator: Translator?,
    val genre: Genre?,
    val tags: Tags?,
    val web: Web?,
    val publishingStatusTachiyomi: PublishingStatusTachiyomi?,
) {
    @Suppress("UNUSED")
    @XmlElement(false)
    @XmlSerialName("xmlns:xsd", "", "")
    val xmlSchema: String = "http://www.w3.org/2001/XMLSchema"

    @Suppress("UNUSED")
    @XmlElement(false)
    @XmlSerialName("xmlns:xsi", "", "")
    val xmlSchemaInstance: String = "http://www.w3.org/2001/XMLSchema-instance"

    @Serializable
    @XmlSerialName("Title", "", "")
    data class Title(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Series", "", "")
    data class Series(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Summary", "", "")
    data class Summary(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Writer", "", "")
    data class Writer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Penciller", "", "")
    data class Penciller(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Inker", "", "")
    data class Inker(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Colorist", "", "")
    data class Colorist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Letterer", "", "")
    data class Letterer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("CoverArtist", "", "")
    data class CoverArtist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Translator", "", "")
    data class Translator(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Genre", "", "")
    data class Genre(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Tags", "", "")
    data class Tags(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Web", "", "")
    data class Web(@XmlValue(true) val value: String = "")

    // The spec doesn't have a good field for this
    @Serializable
    @XmlSerialName("PublishingStatusTachiyomi", "http://www.w3.org/2001/XMLSchema", "ty")
    data class PublishingStatusTachiyomi(@XmlValue(true) val value: String = "")
}

enum class ComicInfoPublishingStatusMap(
    val comicInfoValue: String,
    val sMangaModelValue: Int,
) {
    ONGOING("Ongoing", SManga.ONGOING),
    COMPLETED("Completed", SManga.COMPLETED),
    LICENSED("Licensed", SManga.LICENSED),
    PUBLISHING_FINISHED("Publishing finished", SManga.PUBLISHING_FINISHED),
    CANCELLED("Cancelled", SManga.CANCELLED),
    ON_HIATUS("On hiatus", SManga.ON_HIATUS),
    ;

    companion object {
        fun toComicInfoValue(value: Long): String {
            return values().firstOrNull { it.sMangaModelValue == value.toInt() }?.comicInfoValue
                ?: "Unknown"
        }

        fun toSMangaValue(value: String?): Int {
            return values().firstOrNull { it.comicInfoValue == value }?.sMangaModelValue
                ?: SManga.UNKNOWN
        }
    }
}
