package eu.kanade.tachiyomi.event

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga

class ReaderEvent(val manga: Manga, val chapter: Chapter)
