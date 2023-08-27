package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga

/**
 * A source that may handle opening an SManga for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableSource : Source {

    /**
     * Whether this source may potentially handle the given URI.
     *
     * @since extensions-lib 1.5
     */
    fun canResolveUri(uri: String): Boolean

    /**
     * Called if canHandleUri is true. Returns the corresponding SManga, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getManga(uri: String): SManga?
}
