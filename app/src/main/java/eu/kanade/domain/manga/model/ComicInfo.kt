package eu.kanade.domain.manga.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@XmlSerialName("ComicInfo", "", "")
data class ComicInfo(
    val title: ComicInfoTitle?,
    val series: ComicInfoSeries?,
    val summary: ComicInfoSummary?,
    val writer: ComicInfoWriter?,
    val penciller: ComicInfoPenciller?,
    val inker: ComicInfoInker?,
    val colorist: ComicInfoColorist?,
    val letterer: ComicInfoLetterer?,
    val coverArtist: ComicInfoCoverArtist?,
    val translator: ComicInfoTranslator?,
    val genre: ComicInfoGenre?,
    val tags: ComicInfoTags?,
    val web: ComicInfoWeb?,
    val publishingStatusTachiyomi: ComicInfoPublishingStatusTachiyomi?,
) {
    @XmlElement(false)
    @XmlSerialName("xmlns:xsd", "", "")
    val xmlSchema: String = "http://www.w3.org/2001/XMLSchema"

    @XmlElement(false)
    @XmlSerialName("xmlns:xsi", "", "")
    val xmlSchemaInstance: String = "http://www.w3.org/2001/XMLSchema-instance"
}

@Serializable
@XmlSerialName("Title", "", "")
data class ComicInfoTitle(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Series", "", "")
data class ComicInfoSeries(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Summary", "", "")
data class ComicInfoSummary(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Writer", "", "")
data class ComicInfoWriter(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Penciller", "", "")
data class ComicInfoPenciller(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Inker", "", "")
data class ComicInfoInker(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Colorist", "", "")
data class ComicInfoColorist(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Letterer", "", "")
data class ComicInfoLetterer(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("CoverArtist", "", "")
data class ComicInfoCoverArtist(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Translator", "", "")
data class ComicInfoTranslator(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Genre", "", "")
data class ComicInfoGenre(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Tags", "", "")
data class ComicInfoTags(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("Web", "", "")
data class ComicInfoWeb(@XmlValue(true) val value: String = "")

@Serializable
@XmlSerialName("PublishingStatusTachiyomi", "http://www.w3.org/2001/XMLSchema", "ty")
data class ComicInfoPublishingStatusTachiyomi(@XmlValue(true) val value: String = "")
