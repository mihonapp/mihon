package exh

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.english.HentaiCafe

/**
 * Source helpers
 */

// Lewd source IDs
const val LEWD_SOURCE_SERIES = 6900L
const val EH_SOURCE_ID = LEWD_SOURCE_SERIES + 1
const val EXH_SOURCE_ID = LEWD_SOURCE_SERIES + 2
const val PERV_EDEN_EN_SOURCE_ID = LEWD_SOURCE_SERIES + 5
const val PERV_EDEN_IT_SOURCE_ID = LEWD_SOURCE_SERIES + 6
const val NHENTAI_SOURCE_ID = LEWD_SOURCE_SERIES + 7
val HENTAI_CAFE_SOURCE_ID = delegatedSourceId<HentaiCafe>()
const val TSUMINO_SOURCE_ID = LEWD_SOURCE_SERIES + 9
const val HITOMI_SOURCE_ID = LEWD_SOURCE_SERIES + 10
const val MERGED_SOURCE_ID = LEWD_SOURCE_SERIES + 69

private val DELEGATED_LEWD_SOURCES = listOf(
        HentaiCafe::class
)

private inline fun <reified T> delegatedSourceId(): Long {
    return SourceManager.DELEGATED_SOURCES.entries.find {
        it.value.newSourceClass == T::class
    }!!.value.sourceId
}

// Used to speed up isLewdSource
private val lewdDelegatedSourceIds = SourceManager.DELEGATED_SOURCES.filter {
    it.value.newSourceClass in DELEGATED_LEWD_SOURCES
}.map { it.value.sourceId }.sorted()

// This method MUST be fast!
fun isLewdSource(source: Long) = source in 6900..6999
        || lewdDelegatedSourceIds.binarySearch(source) >= 0

fun Source.isEhBasedSource() = id == EH_SOURCE_ID || id == EXH_SOURCE_ID
