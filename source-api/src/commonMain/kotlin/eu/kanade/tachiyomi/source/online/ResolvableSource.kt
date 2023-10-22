package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * A source that may handle opening an SManga for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableSource : Source {

    /**
     * Returns the UriType of the uri input.
     * Returns Unknown if unable to resolve the URI
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called if canHandleUri is true. Returns the corresponding SManga, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getManga(uri: String): SManga?

    /**
     * Called if canHandleUri is true. Returns the corresponding SChapter, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getChapter(uri: String): SChapter?
}

sealed interface UriType {
    object Manga : UriType
    object Chapter : UriType
    object Unknown : UriType
}
