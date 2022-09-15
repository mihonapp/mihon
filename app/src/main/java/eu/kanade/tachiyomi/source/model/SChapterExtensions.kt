package eu.kanade.tachiyomi.source.model

import data.Chapters

fun SChapter.copyFrom(other: Chapters) {
    name = other.name
    url = other.url
    date_upload = other.date_upload
    chapter_number = other.chapter_number
    scanlator = other.scanlator
}
