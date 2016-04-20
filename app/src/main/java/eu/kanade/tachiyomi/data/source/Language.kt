package eu.kanade.tachiyomi.data.source

class Language(val code: String, val lang: String)

val EN = Language("EN", "English")
val RU = Language("RU", "Russian")

fun getLanguages() = listOf(EN, RU)