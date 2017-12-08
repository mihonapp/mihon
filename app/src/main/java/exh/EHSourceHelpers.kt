package exh

/**
 * Source helpers
 */

val LEWD_SOURCE_SERIES = 6900L
val EH_SOURCE_ID = LEWD_SOURCE_SERIES + 1
val EXH_SOURCE_ID = LEWD_SOURCE_SERIES + 2
val EH_METADATA_SOURCE_ID = LEWD_SOURCE_SERIES + 3
val EXH_METADATA_SOURCE_ID = LEWD_SOURCE_SERIES + 4

val PERV_EDEN_EN_SOURCE_ID = LEWD_SOURCE_SERIES + 5
val PERV_EDEN_IT_SOURCE_ID = LEWD_SOURCE_SERIES + 6

val NHENTAI_SOURCE_ID = LEWD_SOURCE_SERIES + 7

val HENTAI_CAFE_SOURCE_ID = LEWD_SOURCE_SERIES + 8

val TSUMINO_SOURCE_ID = LEWD_SOURCE_SERIES + 9

fun isLewdSource(source: Long) = source in 6900..6999

fun isEhSource(source: Long) = source == EH_SOURCE_ID
    || source == EH_METADATA_SOURCE_ID

fun isExSource(source: Long) = source == EXH_SOURCE_ID
        || source == EXH_METADATA_SOURCE_ID

fun isPervEdenSource(source: Long) = source == PERV_EDEN_IT_SOURCE_ID
|| source == PERV_EDEN_EN_SOURCE_ID

fun isNhentaiSource(source: Long) = source == NHENTAI_SOURCE_ID
