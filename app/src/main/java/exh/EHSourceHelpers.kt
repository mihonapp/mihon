package exh

/**
 * Source helpers
 */

val LEWD_SOURCE_SERIES = 6900L
val EH_SOURCE_ID = LEWD_SOURCE_SERIES + 1
val EXH_SOURCE_ID = LEWD_SOURCE_SERIES + 2
val EH_METADATA_SOURCE_ID = LEWD_SOURCE_SERIES + 3
val EXH_METADATA_SOURCE_ID = LEWD_SOURCE_SERIES + 4

fun isLewdSource(source: Long) = source >= 6900
        && source <= 6999

fun isExSource(source: Long) = source == EXH_SOURCE_ID
        || source == EXH_METADATA_SOURCE_ID
