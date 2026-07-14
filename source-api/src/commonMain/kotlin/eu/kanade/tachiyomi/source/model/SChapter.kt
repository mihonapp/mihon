@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.source.Source
import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var chapter_number: Float

    var scanlator: String?

    var date_upload: Long

    /**
     * Language of the chapter content.
     *
     * Expected to be a valid IETF BCP 47 language tag, for example:
     * * `"en"` → English
     * * `"en-US"` → English (United States)
     * * `"zh-Hant"` → Traditional Chinese
     * * `"es-419"` → Spanish (Latin America)
     * * `"mul"` → Multiple languages
     * * `"und"` → Undetermined
     *
     * If [SManga.language] is not inferred as `"mul"`, any non-null value must match it.
     * A `null` value should be treated as [SManga.language].
     *
     * @see Source.language
     * @see SManga.language
     * @since tachiyomix 1.7
     */
    var language: String?

    /**
     * Extra metadata associated with the chapter.
     *
     * The JSON object is not visible to users and intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"mihon.*"`) for sources to populate.
     *
     * This allows apps to attach and ask for custom information without affecting the visible
     * chapter data.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}
