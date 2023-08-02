package eu.kanade.tachiyomi.source.model

import tachiyomi.data.Chapters

fun SChapter.copyFrom(other: Chapters) {
    name = other.name
    url = other.url
    date_upload = other.date_upload
    chapter_number = other.chapter_number.toFloat()
    scanlator = other.scanlator
}
