package eu.kanade.tachiyomi.data.source

class Language(val code: String, val lang: String)

val ALL = Language("ALL", "All")

fun getLanguages() = listOf(ALL)